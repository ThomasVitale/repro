# Micrometer: OpenTelemetry conventions and OTLP registry

Reproduces issues with the OpenTelemetry JVM conventions and the OTLP registry of Micrometer `1.18.0-RC1`, in a Spring Boot `4.2.0-RC1` application,
compared with the [OpenTelemetry Semantic Conventions](https://github.com/open-telemetry/semantic-conventions/blob/v1.44.0/docs/runtime/jvm-metrics.md) `v1.44.0`.

The application enables the OpenTelemetry conventions for the JVM metrics with `management.observations.conventions=opentelemetry` (see `application.properties`).
Each test asserts what the conventions define, so it fails while the issue is open.

```shell
./gradlew test
```

## Issues

### 1. `jvm.memory.limit` is reported as `-1` for memory pools without a defined maximum

Test: `JvmMetricsConventionsTests#memoryLimitNotReportedWithoutMaximum`

`JvmMemoryMetrics` reports `MemoryUsage.getMax()` as it is, and the JDK returns `-1` when a pool has no defined maximum, such as Metaspace without `-XX:MaxMetaspaceSize` or, with G1, `G1 Survivor Space`.
So `jvm.memory.limit` is `-1` for those pools, which also distorts any sum over the pools of a memory type.

The conventions define `jvm.memory.limit` as the "Measure of max obtainable memory": a pool without a maximum has no limit to report, so the measurement should be skipped. The OpenTelemetry Java instrumentation doesn't record it in this case.

### 2. The OpenTelemetry JVM conventions don't use the units defined by the conventions

Test: `JvmMetricsConventionsTests#jvmMetricsUseConventionUnits`

The OpenTelemetry conventions only define the names and tags of the meters, and the binders keep their Micrometer base units, while the conventions define [UCUM](https://ucum.org/) units:

| Metric | Unit | Convention |
|---|---|---|
| `jvm.memory.used`, `jvm.memory.committed`, `jvm.memory.limit`, `jvm.memory.used_after_last_gc` | `bytes` | `By` |
| `jvm.thread.count` | `threads` | `{thread}` |
| `jvm.class.count`, `jvm.class.loaded`, `jvm.class.unloaded` | `classes` | `{class}` |
| `jvm.cpu.count` | none | `{cpu}` |
| `jvm.cpu.recent_utilization` | none | `1` |

The OTLP registry exports the base unit as the metric unit, so these units reach the backend.
It's related to [#6988](https://github.com/micrometer-metrics/micrometer/issues/6988), which is about the units of timers. The conventions would need a way to define the unit of each meter, at least for the meters without a unit.

### 3. The OTLP registry exports meters with the same name but different descriptions as separate metrics

Test: `OtlpMeterRegistryTests#sameNameMetersExportedAsOneMetric`

`OtlpMetricConverter` groups the meters into metrics by name, type, unit, and description (`MetricMetaData`), so two meters with the same name and different descriptions produce two metrics with the same name in the same scope.
It happens, for example, when the GC pause and concurrent phase timers are both reported as `jvm.gc.duration`, as in the OpenTelemetry conventions.

In the OTLP data model, a metric is identified by its name (plus kind and unit) within a scope, and the meters are data points of the same metric with different attributes.
Micrometer's own `PrometheusMeterRegistry` keeps the first description seen for a name, and the OpenTelemetry Micrometer bridge does the same for the same reason (`Bridging.java`), so both export a single metric. The OTLP registry could do the same.

### 4. Make the OpenTelemetry GC conventions public (feature request)

No test.

`JvmGcMeterConventions` is "intentionally not public for now", so the GC metrics can't use the OpenTelemetry conventions, unlike the memory, thread, class loading, and CPU metrics.
The conventions define a single `jvm.gc.duration` histogram, with the `jvm.gc.name` and `jvm.gc.action` attributes (and the opt-in `jvm.gc.cause`), for both the GC pauses and the concurrent phases that `JvmGcMetrics` reports as `jvm.gc.pause` and `jvm.gc.concurrent.phase.time`.
Today, applications need a `MeterFilter` to rename them, which leads to issue 3 since the two timers keep different descriptions.
