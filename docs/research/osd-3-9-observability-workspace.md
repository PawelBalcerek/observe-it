# OpenSearch Dashboards 3.9.0 Observability Workspace: Flags, Datasets, and Bootstrap APIs

Research notes for wayfinder ticket [#161](https://github.com/PawelBalcerek/observe-it/issues/161) (map
[#160](https://github.com/PawelBalcerek/observe-it/issues/160)). Every claim is verified against primary sources for
OpenSearch & OpenSearch Dashboards `3.9.0` (`opensearch-project/opensearch-dashboards` tag `3.9.0`,
`opensearch-project/dashboards-observability` branch `3.9`, `opensearch-project/sql` branch `3.9`,
`opensearch-project/opensearch-build`, and `opensearch-project/documentation-website`).

## 1. OpenSearch Dashboards `3.9.0` Feature Flags & Docker Configuration

### Exact `opensearch_dashboards.yml` Flags

All Observability Workspace apps default to `false` in `3.9.0` and must be explicitly enabled:

| Flag                                     | Default | Purpose                                                                                                                                                                          | Primary Source                                                                                                                                                                                                                                                                                                                                                                       |
| ---------------------------------------- | ------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `workspace.enabled`                      | `false` | Enables Workspaces (including `use-case-observability`). Incompatible with security multi-tenancy.                                                                               | [`src/plugins/workspace/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/workspace/config.ts)                                                                                                                                                                                                                                          |
| `explore.enabled`                        | `false` | Enables Discover **Logs** (Explore) and auto-overrides UI settings (`home:useNewHomePage`, `query:enhancements:enabled`, `theme:version: v9`, `data.savedQueriesNewUI.enabled`). | [`src/plugins/explore/common/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/common/config.ts), [`src/plugins/explore/server/index.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/server/index.ts)                                                                           |
| `explore.discoverTraces.enabled`         | `false` | Enables Discover **Traces** and gates APM **Services** / **Topology Map** visibility (combined with `observability:apmEnabled`, which defaults to `true`).                       | [`src/plugins/explore/common/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/common/config.ts), [`dashboards-observability/server/plugin_helper/register_settings.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/server/plugin_helper/register_settings.ts)                                     |
| `explore.discoverMetrics.enabled`        | `false` | Enables Discover **Metrics** (PromQL against Prometheus data connections) and APM metrics views.                                                                                 | [`src/plugins/explore/common/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/common/config.ts)                                                                                                                                                                                                                                |
| `datasetManagement.enabled`              | `false` | Enables **Datasets** and **Correlations** management UI inside the Observability workspace.                                                                                      | [`src/plugins/dataset_management/common/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/dataset_management/common/config.ts)                                                                                                                                                                                                          |
| `data_source.enabled`                    | `false` | Enables Multiple Data Sources (MDS) and causes `POST /api/directquery/dataconnections` to auto-create the `data-connection` saved object for Prometheus.                         | [`src/plugins/data_source/config.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/data_source/config.ts), [`src/plugins/data_source_management/server/routes/data_connections_router.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/data_source_management/server/routes/data_connections_router.ts) |
| `observability.slo.enabled` *(optional)* | `false` | Enables APM **SLOs** (requires `explore.discoverTraces.enabled: true` as well).                                                                                                  | [`dashboards-observability/server/index.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/server/index.ts)                                                                                                                                                                                                                                                |

Recommended minimal `opensearch_dashboards.yml` for Observe-It:

```yaml
server.host: "0.0.0.0"
opensearch.hosts: ["http://opensearch:9200"]

workspace.enabled: true
data_source.enabled: true
explore.enabled: true
explore.discoverTraces.enabled: true
explore.discoverMetrics.enabled: true
datasetManagement.enabled: true
```

> [!NOTE]
> `explore.enabled: true` automatically enables `data.savedQueriesNewUI.enabled: true`,
> `opensearchDashboards.branding.useExpandedHeader: false`, and `uiSettings.overrides` for `theme:version: 'v9'`,
> `home:useNewHomePage: true`, and `query:enhancements:enabled: true` via its server config hook in
> [`src/plugins/explore/server/index.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/server/index.ts).
> In `dashboards-observability` `3.9`, the UI setting `observability:apmEnabled` defaults to `true`
> ([`register_settings.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/server/plugin_helper/register_settings.ts)),
> so enabling `explore.discoverTraces.enabled: true` automatically exposes APM **Services** and **Topology Map**
> (replacing legacy Trace Analytics in the Observability workspace).

### Docker Env Vars vs. Mounted `opensearch_dashboards.yml`

The `3.x` Docker entrypoint
([`opensearch-dashboards-docker-entrypoint-3.x.sh`](https://github.com/opensearch-project/opensearch-build/blob/main/docker/release/config/opensearch-dashboards/opensearch-dashboards-docker-entrypoint-3.x.sh))
only translates an **explicit allowlist** (`opensearch_dashboards_vars`) of environment variables into `--<key>=<value>`
CLI flags:

- **In allowlist**: `WORKSPACE_ENABLED=true`, `DATA_SOURCE_ENABLED=true`, `OPENSEARCH_HOSTS=...`.
- **NOT in allowlist**: `explore.enabled`, `explore.discoverTraces.enabled`, `explore.discoverMetrics.enabled`,
  `datasetManagement.enabled`, `observability.slo.enabled`.

Therefore, **Docker environment variables alone cannot enable the Observability Workspace**. You must either mount a
configuration file or pass CLI arguments via `command:`.

> [!WARNING]
> **Read-only mount trap with `DISABLE_SECURITY_DASHBOARDS_PLUGIN=true`:** When
> `DISABLE_SECURITY_DASHBOARDS_PLUGIN=true` is set on a fresh container, `setupSecurityDashboardsPlugin` in
> `opensearch-dashboards-docker-entrypoint-3.x.sh` removes the `securityDashboards` plugin and executes
> `echo "$UPDATED_CONFIG" > $OPENSEARCH_DASHBOARDS_HOME/config/opensearch_dashboards.yml`. Mounting a file read-only
> (`:ro`) directly over `/usr/share/opensearch-dashboards/config/opensearch_dashboards.yml` fails container startup with
> `Read-only file system`. Either mount the config to a separate read-only path (e.g.
> `/usr/share/opensearch-dashboards/config/observe-it.yml:ro`) with
> `command: ["opensearch-dashboards", "-c", "/usr/share/opensearch-dashboards/config/observe-it.yml"]`, or pass the
> `--explore.*` / `--datasetManagement.enabled=true` flags via `command:`.

## 2. Impact of `DISABLE_SECURITY_PLUGIN=true` and `DISABLE_SECURITY_DASHBOARDS_PLUGIN=true`

| Area                                                                 | Behavior with Security Plugins Disabled                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        | Primary Source                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| -------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| **Workspaces & Saved Object Permissions**                            | Workspaces work normally. `savedObjects.permission.enabled` takes no effect when the security plugin is absent; multi-tenancy conflict does not arise because `securityDashboards` is removed.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 | [`config/opensearch_dashboards.yml`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/config/opensearch_dashboards.yml)                                                                                                                                                                                                                                                                                                                                                                                                      |
| **Datasets & Correlations**                                          | Work normally via `/w/<workspaceId>/api/saved_objects/...` with `-H "osd-xsrf: true"` and no auth headers.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                     | [`src/plugins/workspace/server/saved_objects/workspace_id_consumer_wrapper.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/workspace/server/saved_objects/workspace_id_consumer_wrapper.ts)                                                                                                                                                                                                                                                                                                                |
| **OpenSearch SQL Datasources (`plugins.query.datasources.enabled`)** | Enabled by default (`true` in `OpenSearchSettings.DATASOURCE_ENABLED_SETTING`).                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                | [`opensearch-project/sql` `OpenSearchSettings.java`](https://github.com/opensearch-project/sql/blob/3.9/opensearch/src/main/java/org/opensearch/sql/opensearch/setting/OpenSearchSettings.java)                                                                                                                                                                                                                                                                                                                                                    |
| **`plugins.query.datasources.encryption.masterkey`**                 | **Independent of the Security plugin.** `OpenSearchDataSourceMetadataStorage.encryptDecryptAuthenticationData` calls `EncryptorImpl.encrypt()` (which throws `IllegalStateException` if `masterkey` is empty) only for properties ending in `auth.username`, `auth.password`, `auth.access_key`, or `auth.secret_key`. Unauthenticated Prometheus (`prometheus.uri` only) does not trigger encryption in code, **but official docs list `masterkey` (16, 24, or 32 chars, node-scoped `opensearch.yml` / env var) as a prerequisite**. Setting a static 24-char dev key (e.g. `plugins.query.datasources.encryption.masterkey=0123456789abcdef01234567`) in `docker-compose.yml` is harmless and future-proof. | [`OpenSearchDataSourceMetadataStorage.java`](https://github.com/opensearch-project/sql/blob/3.9/datasources/src/main/java/org/opensearch/sql/datasources/storage/OpenSearchDataSourceMetadataStorage.java), [`EncryptorImpl.java`](https://github.com/opensearch-project/sql/blob/3.9/datasources/src/main/java/org/opensearch/sql/datasources/encryptor/EncryptorImpl.java), [`_dashboards/management/connect-prometheus.md`](https://github.com/opensearch-project/documentation-website/blob/main/_dashboards/management/connect-prometheus.md) |

## 3. Idempotent Bootstrap HTTP/REST APIs (`docker/opensearch/bootstrap.sh`)

All Dashboards HTTP requests require `-H "osd-xsrf: true"` and `-H "Content-Type: application/json"`.

### A. Create or Update an Observability Workspace

- **Endpoints** ([`src/plugins/workspace/server/routes/index.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/workspace/server/routes/index.ts)):
  - `GET /api/workspaces/{id}` — check existence (`result.success == true`).
  - `POST /api/workspaces` — create with a deterministic `attributes.id` (`6–36` chars matching `[A-Za-z0-9_-]`).
    Returns `{ "success": false, "error": ... }` if the ID or name already exists.
  - `PUT /api/workspaces/{id}` — update attributes and `settings.dataConnections` / `settings.dataSources`.
- **Use case feature**: `features: ["use-case-observability"]` (exactly one `use-case-*` value is required).
- **Important**: `settings` is a required object in the `POST` / `PUT` body schema (pass `{}` if empty).

```bash
# Idempotent create-or-update (check GET first, then POST or PUT)
curl -fsS -X POST "http://opensearch-dashboards:5601/api/workspaces" \
  -H "osd-xsrf: true" \
  -H "Content-Type: application/json" \
  -d '{
    "attributes": {
      "id": "observe-it",
      "name": "Observe-It",
      "description": "Observe-It local observability workspace",
      "features": ["use-case-observability"]
    },
    "settings": {
      "dataConnections": ["observe-it-prometheus"]
    }
  }'
```

### B. Register a Prometheus Data Source and Attach It to the Workspace

Discover Metrics (`explore.discoverMetrics.enabled: true`) and APM rely on **two** resources:

1. **OpenSearch SQL Datasource** (stored in `.ql-datasources`, queried by OpenSearch SQL/PPL/PromQL engine):
   - Endpoint: `POST http://opensearch:9200/_plugins/_query/_datasources` (and `PUT` to update,
     `GET /_plugins/_query/_datasources/{name}` to check existence).
   - Source: [`_observing-your-data/prometheusmetrics.md`](https://github.com/opensearch-project/documentation-website/blob/main/_observing-your-data/prometheusmetrics.md).

   ```bash
   curl -fsS -X POST "http://opensearch:9200/_plugins/_query/_datasources" \
     -H "Content-Type: application/json" \
     -d '{
       "name": "observe_it_prometheus",
       "connector": "prometheus",
       "properties": {
         "prometheus.uri": "http://prometheus:9090"
       }
     }'
   ```

2. **OpenSearch Dashboards `data-connection` Saved Object** (queried by `prometheus_type.ts` via
   `savedObjects.client.find({ type: 'data-connection' })` filtering `attributes.type === 'Prometheus'`, and referenced
   by `APM-Config` correlations):
   - Source: [`src/plugins/query_enhancements/public/datasets/prometheus_type.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/query_enhancements/public/datasets/prometheus_type.ts)
     and [`src/plugins/data_source/common/data_connections/data_connection_saved_object_attributes.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/data_source/common/data_connections/data_connection_saved_object_attributes.ts).
   - **Why Dashboards `POST /api/directquery/dataconnections` is not enough alone for deterministic IDs**:
     `data_connections_router.ts` creates the SQL datasource and (when `data_source.enabled: true`) calls
     `savedObjects.client.create('data-connection', ...)` with a random UUID and no workspace association.
   - **Recommended idempotent bootstrap pattern**:
     1. Upsert the SQL datasource via OpenSearch `GET`/`POST`/`PUT /_plugins/_query/_datasources`.
     2. Upsert the `data-connection` saved object with a deterministic ID directly inside the workspace (using
        `?overwrite=true`):

   ```bash
   curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/data-connection/observe-it-prometheus?overwrite=true" \
     -H "osd-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{
       "attributes": {
         "connectionId": "observe_it_prometheus",
         "type": "Prometheus"
       }
     }'
   ```

   Alternatively, create it globally at `/api/saved_objects/data-connection/observe-it-prometheus?overwrite=true` and
   attach it via `settings.dataConnections: ["observe-it-prometheus"]` on `POST`/`PUT /api/workspaces` or via
   `POST /api/workspaces/_associate` with `workspaceId: "observe-it"` and
   `savedObjects: [{ "type": "data-connection", "id": "observe-it-prometheus" }]`.
   Note that `WorkspaceIdConsumerWrapper.validateObjectInAWorkspace` explicitly denies workspace access to global
   `data-connection` objects that have no `workspaces` assigned.

### C. Create Log, Trace, and Metric Datasets + Cross-Signal Correlations

Datasets in OpenSearch Dashboards `3.9.0` are `index-pattern` saved objects created in the workspace scope
(`/w/<workspaceId>/api/saved_objects/index-pattern/<id>?overwrite=true`) with `signalType` (`"logs"` or `"traces"`),
optional `displayName`, and optional `schemaMappings` (for OTel log field mapping). For the local OpenSearch cluster,
`references` is omitted or `[]`.

1. **Logs Dataset (`ss4o_logs-*`)**
   ([`create_auto_datasets.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/public/utils/create_auto_datasets.ts),
   [`explore/public/application/pages/traces/README.md`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/public/application/pages/traces/README.md)):

   ```bash
   curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/index-pattern/ss4o_logs?overwrite=true" \
     -H "osd-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{
       "attributes": {
         "title": "ss4o_logs-*",
         "displayName": "Observe-It Logs",
         "timeFieldName": "@timestamp",
         "signalType": "logs",
         "schemaMappings": "{\"otelLogs\":{\"timestamp\":\"@timestamp\",\"traceId\":\"traceId\",\"spanId\":\"spanId\",\"serviceName\":\"serviceName\"}}"
       }
     }'
   ```

2. **Traces Dataset (`ss4o_traces-*`)**:

   ```bash
   curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/index-pattern/ss4o_traces?overwrite=true" \
     -H "osd-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{
       "attributes": {
         "title": "ss4o_traces-*",
         "displayName": "Observe-It Traces",
         "timeFieldName": "startTime",
         "signalType": "traces"
       }
     }'
   ```

3. **Metrics Dataset**:
   - **Prometheus Metrics (Discover Metrics)**: Automatically surfaced from the `data-connection` saved object
     (`type: "Prometheus"`) associated with the workspace — no separate `index-pattern` is needed for PromQL
     ([`prometheus_type.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/query_enhancements/public/datasets/prometheus_type.ts)).
   - **SS4O Metrics (`ss4o_metrics-*`)**: Remains an `index-pattern` saved object for classic visualizations/dashboards.

4. **Traces-to-Logs Correlation** (`type: "correlations"`):
   Created via `POST /w/<workspaceId>/api/saved_objects/correlations/<id>?overwrite=true`
   ([`explore/public/application/pages/traces/README.md`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/public/application/pages/traces/README.md),
   [`create_auto_datasets.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/explore/public/utils/create_auto_datasets.ts)):

   ```bash
   curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/correlations/trace-to-logs-ss4o?overwrite=true" \
     -H "osd-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{
       "attributes": {
         "title": "trace-to-logs_ss4o_traces-*",
         "correlationType": "trace-to-logs-ss4o_traces-*",
         "version": "1.0.0",
         "entities": [
           { "tracesDataset": { "id": "references[0].id" } },
           {
             "logsDataset": {
               "id": "references[1].id",
               "meta": {
                 "logSpanIdField": "spanId",
                 "logTraceIdField": "traceId",
                 "logMessage": "message",
                 "timestamp": "@timestamp"
               }
             }
           }
         ]
       },
       "references": [
         { "name": "entities[0].index", "type": "index-pattern", "id": "ss4o_traces" },
         { "name": "entities[1].index", "type": "index-pattern", "id": "ss4o_logs" }
       ]
     }'
   ```

5. **APM Configuration Correlation** (`correlationType: "APM-Config-<workspaceId>"`):
   Links the Traces dataset, Service Map dataset, and Prometheus `data-connection` for APM **Services** and **Topology
   Map** ([`dashboards-observability/public/components/apm/config/README.md`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/components/apm/config/README.md),
   [`apm_config.ts`](https://github.com/opensearch-project/dashboards-observability/blob/3.9/public/services/saved_objects/saved_object_client/osd_saved_objects/apm_config.ts)):

   ```bash
   curl -fsS -X POST "http://opensearch-dashboards:5601/w/observe-it/api/saved_objects/correlations/apm-config-observe-it?overwrite=true" \
     -H "osd-xsrf: true" \
     -H "Content-Type: application/json" \
     -d '{
       "attributes": {
         "correlationType": "APM-Config-observe-it",
         "version": "1.0.0",
         "entities": [
           { "tracesDataset": { "id": "references[0].id" } },
           { "serviceMapDataset": { "id": "references[1].id" } },
           { "prometheusDataSource": { "id": "references[2].id" } },
           { "windowDuration": 60 }
         ]
       },
       "references": [
         { "name": "entities[0].index", "type": "index-pattern", "id": "ss4o_traces" },
         { "name": "entities[1].index", "type": "index-pattern", "id": "ss4o_service_map" },
         { "name": "entities[2].dataConnection", "type": "data-connection", "id": "observe-it-prometheus" }
       ]
     }'
   ```

### D. Classic Saved Searches and Dashboards (`docker/opensearch/saved-objects.ndjson`)

When `workspace.enabled: true` and `explore.enabled: true`:

1. **Workspace scoping of `saved-objects.ndjson`**:
   - There is **no automatic default workspace**. Objects imported via the global
     `POST /api/saved_objects/_import?overwrite=true` endpoint have no `workspaces` array (`[]`) and will **not** show
     up in workspace-scoped `find()` listings (which filter by `workspaces: [workspaceId]` in
     [`workspace_id_consumer_wrapper.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/workspace/server/saved_objects/workspace_id_consumer_wrapper.ts)).
   - To make `saved-objects.ndjson` appear inside the Observability workspace, import directly into the workspace path:
     `POST /w/<workspaceId>/api/saved_objects/_import?overwrite=true` (or associate existing objects via
     `POST /api/workspaces/_associate`).
2. **Classic Dashboards & Visualizations**:
   - The **Dashboards** and **Visualize** apps remain available inside `use-case-observability` workspaces, so
     `observe-it-metrics-overview`, `observe-it-traces-overview`, and all visualizations in `saved-objects.ndjson`
     continue to work when imported into `/w/<workspaceId>/api/saved_objects/_import?overwrite=true`.
3. **Classic Discover Saved Searches (`type: "search"`)**:
   - In [`src/plugins/discover/public/plugin.ts`](https://github.com/opensearch-project/opensearch-dashboards/blob/3.9.0/src/plugins/discover/public/plugin.ts)
     (lines 321–329), when `explore.enabled: true`, **classic Discover is intentionally hidden from the
     `use-case-observability` left navigation** (replaced by Explore's Discover Logs/Traces/Metrics).
   - Classic Discover remains registered in `use-case-all` and `use-case-essentials` workspaces, and `search` objects
     imported into the Observability workspace remain accessible via direct URL or **Assets** / **Saved Objects** (or
     can be migrated to `explore` saved objects / kept in an optional `use-case-all` workspace if classic Discover nav
     is desired).
