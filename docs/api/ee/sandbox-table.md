# Sandbox table

  - [GET /api/ee/sandbox/table/:id/query_metadata](#get-apieesandboxtableidquery_metadata)

## `GET /api/ee/sandbox/table/:id/query_metadata`

This endpoint essentially acts as a wrapper for the OSS version of this route. When a user has segmented permissions
  that only gives them access to a subset of columns for a given table, those inaccessable columns should also be
  excluded from what is show in the query builder. When the user has full permissions (or no permissions) this route
  doesn't add/change anything from the OSS version. See the docs on the OSS version of the endpoint for more
  information.

### PARAMS:

*  **`id`** 

*  **`include_sensitive_fields`** value may be nil, or if non-nil, value must be a valid boolean string ('true' or 'false').

*  **`include_hidden_fields`** value may be nil, or if non-nil, value must be a valid boolean string ('true' or 'false').

---

[<< Back to API index](../../api-documentation.md)