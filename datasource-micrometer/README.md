# datasource-micrometer: OpenTelemetry conventions

Reproduces issues with the OpenTelemetry support of datasource-micrometer `2.3.0`
(`datasource-micrometer-spring-boot` + `datasource-micrometer-opentelemetry`) in a Spring Boot `4.2.0-RC1` application,
compared with the [OpenTelemetry Semantic Conventions](https://github.com/open-telemetry/semantic-conventions/tree/v1.44.0/docs/db) `v1.44.0`.

Each test asserts what the conventions define, so it fails while the issue is open.

```shell
./gradlew test
```

The tests use the in-memory H2 database auto-configured by Spring Boot, except `PostgresqlQueryObservationTests`, which starts PostgreSQL with Testcontainers.
`ConnectionUrlQueryObservationTests` uses H2 connections that report the JDBC URL of another database in their metadata, which is where the instrumentation reads the URL from.

## Issues

### 1. Queries fail on empty `Statement` batches and on short-form PostgreSQL, MySQL, and MariaDB URLs

Tests: `H2QueryObservationTests#emptyStatementBatchSucceeds`, `ConnectionUrlQueryObservationTests.ShortFormPostgresqlUrl#queryOnShortFormUrlSucceeds`

With the OpenTelemetry support enabled, two kinds of JDBC calls that work without the instrumentation fail with an exception thrown by the instrumentation:

- `Statement.executeBatch()` with no statement added to the batch fails with `IndexOutOfBoundsException: Index 0 out of bounds for length 0`.
  The query list of the execution is empty, and `OpenTelemetryAttributesManager.getAttributes()` reads its first element (`queryContext.getQueryInfoList().get(0)`).
- Queries on connections whose JDBC URL has no `//` after the driver name, such as the short form `jdbc:postgresql:mydb` accepted by the PostgreSQL driver, fail with
  `NullPointerException: Cannot invoke "String.startsWith(String)" because "path" is null`.
  `SimpleDatabaseNamespaceRetriever` parses the URL as a `URI`, whose path is `null` for such opaque URIs.

Expected: the instrumentation never makes the instrumented call fail. The conventions also define the empty batch case: "A request to execute a batch operation with no operations SHOULD also be treated as a batch operation, and `db.operation.batch.size` SHOULD be set to `0`."

### 2. `error.type` and `db.response.status_code` are never recorded for failed queries

Test: `H2QueryObservationTests#failedQueryRecordsErrorType`

When a query fails, neither the span nor the `db.client.operation.duration` metric get `error.type` or `db.response.status_code`, which the conventions require for failed operations ("Conditionally Required: If and only if the operation failed" for `error.type`).

`OpenTelemetryAttributesManager.getOrCreateAttributes()` computes the attributes once and caches them in the `QueryContext`. The first call happens when the observation starts (`SimpleObservation.start()` calls the convention), when `ExecutionInfo.getThrowable()` is still `null`, so the error is never part of the attributes used when the observation stops, by both the convention and `OpenTelemetryMeterObservationHandler`.

### 3. `db.response.status_code` uses the vendor error code instead of the SQLSTATE

Test: `PostgresqlQueryObservationTests#responseStatusCodeUsesSqlState`

`db.response.status_code` comes from `SQLException.getErrorCode()` only. PostgreSQL reports errors with a SQLSTATE and no vendor code, so a syntax error gets `0` instead of `42601`.
The test computes the attributes after the failure, since issue 2 prevents them from being recorded.

[`sql.md`](https://github.com/open-telemetry/semantic-conventions/blob/v1.44.0/docs/db/sql.md): "Instrumentations SHOULD populate the `db.response.status_code` with the most specific code available to them. [...] If only SQLSTATE is available, it SHOULD be used. When multiple error codes are available and specificity is unclear, instrumentation SHOULD set the `db.response.status_code` to the concatenated string of all codes with '/' used as a separator."

### 4. `server.port` and `network.peer.port` are recorded as `-1`

Test: `H2QueryObservationTests#serverPortNotRecordedWithoutServerAddress`

`OpenTelemetryAttributes.populateFromQueryContext()` always sets `server.port` from the port of the connection URL, which is `-1` when the URL has no port, such as `jdbc:postgresql://localhost/db` or an embedded database. It's also set for the default port of the database and without `server.address`.
`OpenTelemetryMeterObservationHandler` also adds `network.peer.address` and `network.peer.port` from the connection URL to the metric.

Expected: `server.port` is "Conditionally Required: If using a port other than the default port for this DBMS and if `server.address` is set". `network.peer.*` describe the peer of the network connection, not the host and port of the connection URL, so they shouldn't be derived from it.

### 5. `db.query.text` is recorded on the `db.client.operation.duration` metric by default

Test: `H2QueryObservationTests#queryTextNotRecordedOnMetricByDefault`

The conventions make `db.query.text` "Opt-In" on the metric: even sanitized, it "could still have high cardinality". The metric publishes a histogram, so each distinct query creates a full set of bucket time series.
For prepared statements, the query text is not sanitized (as the conventions require for parameterized queries on spans), so any literal in a prepared statement also becomes a metric tag.

### 6. Wrong query summary for `INSERT ... VALUES`, and `db.operation.name` doesn't match the summary

Tests: `H2QueryObservationTests#insertValuesQuerySummary`, `H2QueryObservationTests#multiWordOperationNameMatchesQuerySummary`

- `INSERT INTO users VALUES (1, 'alice')` gets `db.query.summary` (and span name) `INSERT users SELECT`. The query has no `SELECT`: the summary should be `INSERT users`.
- `CREATE TABLE products (...)` gets `db.operation.name` `CREATE_TABLE`, while the summary is `CREATE TABLE products`. The conventions: "If the operation and target value is populated on `db.operation.name` [...], it SHOULD match the value used in the `db.query.summary`."

### 7. `db.system.name` is `other_sql` for databases defined by the conventions

Test: `H2QueryObservationTests#h2SystemName`

`SimpleOpenTelemetryConnectionUrlParser` only maps PostgreSQL, MySQL, MariaDB, and Microsoft SQL Server, and uses `other_sql` for all the other databases.
The conventions define values for many more, such as `h2database`, `oracle.db`, `ibm.db2`, `hsqldb`, `derby`, and `sqlite`, and "If a value defined in this list applies to the DBMS to which the request is sent, this value MUST be used."

### 8. `db.namespace` is never recorded for Microsoft SQL Server

Test: `ConnectionUrlQueryObservationTests.SqlServerUrl#sqlServerNamespace`

`DataSourceObservationOpenTelemetryAutoConfiguration` registers the SQL Server namespace retriever under the key `"sqlserver"`, while `SimpleOpenTelemetryConnectionUrlParser` looks retrievers up by system name, which is `"microsoft.sql_server"`, so the retriever is never used.

### 9. `db.operation.name` and `db.collection.name` are extracted from the query text (discussion)

No test: it's a design question rather than a defect.

[`sql.md`](https://github.com/open-telemetry/semantic-conventions/blob/v1.44.0/docs/db/sql.md) says that "The operation name SHOULD NOT be extracted from `db.query.text`" and "The collection name SHOULD NOT be extracted from `db.query.text`", while `JSqlParserQueryAnalyzer` derives both from the parsed query. Since both are low-cardinality attributes, they're also recorded on the metric.

### 10. `jdbc.query` timer recorded alongside `db.client.operation.duration` (question)

No test: it's a question.

With the OpenTelemetry support, each query is recorded twice: as `db.client.operation.duration` by `OpenTelemetryMeterObservationHandler`, and as `jdbc.query` (plus the `jdbc.query.active` long task timer) by Spring Boot's `DefaultMeterObservationHandler`, since the observation keeps the `jdbc.query` name. Is the `jdbc.query` timer expected when the OpenTelemetry conventions are in use?
