(ns metabase.api.dataset
  "/api/dataset endpoints."
  (:require [cheshire.core :as json]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [compojure.core :refer [POST]]
            [metabase.api.common :as api]
            [metabase.events :as events]
            [metabase.mbql.normalize :as mbql.normalize]
            [metabase.mbql.schema :as mbql.s]
            [metabase.models.card :refer [Card]]
            [metabase.models.database :as database :refer [Database]]
            [metabase.models.query :as query]
            [metabase.models.table :refer [Table]]
            [metabase.query-processor :as qp]
            [metabase.query-processor.middleware.constraints :as qp.constraints]
            [metabase.query-processor.middleware.permissions :as qp.perms]
            [metabase.query-processor.pivot :as qp.pivot]
            [metabase.query-processor.streaming :as qp.streaming]
            [metabase.query-processor.util :as qp.util]
            [metabase.shared.models.visualization-settings :as mb.viz]
            [metabase.util :as u]
            [metabase.util.i18n :refer [trs tru]]
            [metabase.util.schema :as su]
            [schema.core :as s]
            [toucan.db :as db]))

;;; -------------------------------------------- Running a Query Normally --------------------------------------------

(defn- query->source-card-id
  "Return the ID of the Card used as the \"source\" query of this query, if applicable; otherwise return `nil`. Used so
  `:card-id` context can be passed along with the query so Collections perms checking is done if appropriate. This fn
  is a wrapper for the function of the same name in the QP util namespace; it adds additional permissions checking as
  well."
  [outer-query]
  (when-let [source-card-id (qp.util/query->source-card-id outer-query)]
    (log/info (trs "Source query for this query is Card {0}" source-card-id))
    (api/read-check Card source-card-id)
    source-card-id))

(defn- run-query-async
  [{:keys [database], :as query}
   & {:keys [context export-format qp-runner]
      :or   {context       :ad-hoc
             export-format :api
             qp-runner     qp/process-query-and-save-with-max-results-constraints!}}]
  (when (and (not= (:type query) "internal")
             (not= database mbql.s/saved-questions-virtual-database-id))
    (when-not database
      (throw (ex-info (tru "`database` is required for all queries whose type is not `internal`.")
                      {:status-code 400, :query query})))
    (api/read-check Database database))
  ;; store table id trivially iff we get a query with simple source-table
  (let [table-id (get-in query [:query :source-table])]
    (when (int? table-id)
      (events/publish-event! :table-read (assoc (Table table-id) :actor_id api/*current-user-id*))))
  ;; add sensible constraints for results limits on our query
  (let [source-card-id (query->source-card-id query)
        source-card    (when source-card-id
                         (db/select-one [Card :result_metadata :dataset] :id source-card-id))
        info           (cond-> {:executed-by api/*current-user-id*
                                :context     context
                                :card-id     source-card-id}
                         (:dataset source-card)
                         (assoc :metadata/dataset-metadata (:result_metadata source-card)))]
    (binding [qp.perms/*card-id* source-card-id]
      (qp.streaming/streaming-response [context export-format]
        (qp-runner query info context)))))

(api/defendpoint ^:streaming POST "/"
  "Execute a query and retrieve the results in the usual format."
  [:as {{:keys [database] :as query} :body}]
  {database (s/maybe s/Int)}
  (run-query-async (update-in query [:middleware :js-int-to-string?] (fnil identity true))))


;;; ----------------------------------- Downloading Query Results in Other Formats -----------------------------------

(def ExportFormat
  "Schema for valid export formats for downloading query results."
  (apply s/enum (map u/qualified-name (qp.streaming/export-formats))))

(s/defn export-format->context :- mbql.s/Context
  "Return the `:context` that should be used when saving a QueryExecution triggered by a request to download results
  in `export-format`.

    (export-format->context :json) ;-> :json-download"
  [export-format]
  (keyword (str (u/qualified-name export-format) "-download")))

(def export-format-regex
  "Regex for matching valid export formats (e.g., `json`) for queries.
   Inteneded for use in an endpoint definition:

     (api/defendpoint POST [\"/:export-format\", :export-format export-format-regex]"
  (re-pattern (str "(" (str/join "|" (map u/qualified-name (qp.streaming/export-formats))) ")")))

(def ^:private column-ref-regex #"^\[.+\]$")

(defn- viz-setting-key-fn
  "Key function for parsing JSON visualization settings into the DB form. Converts most keys to
  keywords, but leaves column references as strings."
   [json-key]
   (if (re-matches column-ref-regex json-key)
     json-key
     (keyword json-key)))

(api/defendpoint ^:streaming POST ["/:export-format", :export-format export-format-regex]
  "Execute a query and download the result data as a file in the specified format."
  [export-format :as {{:keys [query visualization_settings] :or {visualization_settings "{}"}} :params}]
  {query                  su/JSONString
   visualization_settings su/JSONString
   export-format          ExportFormat}
  (let [query        (json/parse-string query keyword)
        viz-settings (-> (json/parse-string visualization_settings viz-setting-key-fn)
                         (update-in [:table.columns] mbql.normalize/normalize)
                         mb.viz/db->norm)
        query        (-> (assoc query
                                :async? true
                                :viz-settings viz-settings)
                         (dissoc :constraints)
                         (update :middleware #(-> %
                                                  (dissoc :add-default-userland-constraints? :js-int-to-string?)
                                                  (assoc :process-viz-settings? true
                                                         :skip-results-metadata? true
                                                         :format-rows? false))))]
    (run-query-async
     query
     :export-format export-format
     :context       (export-format->context export-format)
     :qp-runner     qp/process-query-and-save-execution!)))


;;; ------------------------------------------------ Other Endpoints -------------------------------------------------

;; TODO - this is no longer used. Should we remove it?
(api/defendpoint POST "/duration"
  "Get historical query execution duration."
  [:as {{:keys [database], :as query} :body}]
  (api/read-check Database database)
  ;; try calculating the average for the query as it was given to us, otherwise with the default constraints if
  ;; there's no data there. If we still can't find relevant info, just default to 0
  {:average (or
             (some (comp query/average-execution-time-ms qp.util/query-hash)
                   [query
                    (assoc query :constraints qp.constraints/default-query-constraints)])
             0)})

(api/defendpoint POST "/native"
  "Fetch a native version of an MBQL query."
  [:as {query :body}]
  (qp.perms/check-current-user-has-adhoc-native-query-perms query)
  (qp/compile-and-splice-parameters query))

(api/defendpoint ^:streaming POST "/pivot"
  "Generate a pivoted dataset for an ad-hoc query"
  [:as {{:keys [database] :as query} :body}]
  {database (s/maybe s/Int)}
  (when-not database
    (throw (Exception. (str (tru "`database` is required for all queries.")))))
  (api/read-check Database database)
  (let [info {:executed-by api/*current-user-id*
              :context     :ad-hoc}]
    (qp.streaming/streaming-response [context :api]
      (qp.pivot/run-pivot-query (assoc query :async? true) info context))))

(api/define-routes)
