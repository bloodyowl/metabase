# Metric

/api/metric endpoints.

  - [DELETE /api/metric/:id](#delete-apimetricid)
  - [GET /api/metric/](#get-apimetric)
  - [GET /api/metric/:id](#get-apimetricid)
  - [GET /api/metric/:id/related](#get-apimetricidrelated)
  - [GET /api/metric/:id/revisions](#get-apimetricidrevisions)
  - [POST /api/metric/](#post-apimetric)
  - [POST /api/metric/:id/revert](#post-apimetricidrevert)
  - [PUT /api/metric/:id](#put-apimetricid)
  - [PUT /api/metric/:id/important_fields](#put-apimetricidimportant_fields)

## `DELETE /api/metric/:id`

Archive a Metric. (DEPRECATED -- Just pass updated value of `:archived` to the `PUT` endpoint instead.).

### PARAMS:

*  **`id`** 

*  **`revision_message`** value must be a non-blank string.

## `GET /api/metric/`

Fetch *all* `Metrics`.

## `GET /api/metric/:id`

Fetch `Metric` with ID.

### PARAMS:

*  **`id`**

## `GET /api/metric/:id/related`

Return related entities.

### PARAMS:

*  **`id`**

## `GET /api/metric/:id/revisions`

Fetch `Revisions` for `Metric` with ID.

### PARAMS:

*  **`id`**

## `POST /api/metric/`

Create a new `Metric`.

### PARAMS:

*  **`name`** value must be a non-blank string.

*  **`description`** value may be nil, or if non-nil, value must be a string.

*  **`table_id`** value must be an integer greater than zero.

*  **`definition`** value must be a map.

## `POST /api/metric/:id/revert`

Revert a `Metric` to a prior `Revision`.

### PARAMS:

*  **`id`** 

*  **`revision_id`** value must be an integer greater than zero.

## `PUT /api/metric/:id`

Update a `Metric` with ID.

### PARAMS:

*  **`points_of_interest`** value may be nil, or if non-nil, value must be a string.

*  **`description`** value may be nil, or if non-nil, value must be a string.

*  **`archived`** value may be nil, or if non-nil, value must be a boolean.

*  **`definition`** value may be nil, or if non-nil, value must be a map.

*  **`revision_message`** value must be a non-blank string.

*  **`show_in_getting_started`** value may be nil, or if non-nil, value must be a boolean.

*  **`name`** value may be nil, or if non-nil, value must be a non-blank string.

*  **`caveats`** value may be nil, or if non-nil, value must be a string.

*  **`id`** 

*  **`how_is_this_calculated`** value may be nil, or if non-nil, value must be a string.

## `PUT /api/metric/:id/important_fields`

Update the important `Fields` for a `Metric` with ID.
   (This is used for the Getting Started guide).

You must be a superuser to do this.

### PARAMS:

*  **`id`** 

*  **`important_field_ids`** value must be an array. Each value must be an integer greater than zero.

---

[<< Back to API index](../api-documentation.md)