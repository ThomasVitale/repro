# Micrometer Tracing: `Ok` span status

Reproduces how the Micrometer Tracing OpenTelemetry bridge sets the span status, with Micrometer Tracing `1.8.0-RC1`
in a Spring Boot `4.2.0-RC1` application (`spring-boot-starter-opentelemetry`).

The test fails while the issue is open.

```shell
./gradlew test
```

## Issue

### Spans ending without an error get the `Ok` status, or not, depending on how the bridge creates them

Test: `SpanStatusTests#statusLeftUnsetWithoutError`

The test runs an observation (`parent.operation`) that contains another observation (`child.operation`) and a `RestClient` call to the application itself, then checks the status of the four spans exported:

| Span | Kind | Status |
|---|---|---|
| `parent.operation` | `INTERNAL` | `Ok` |
| `child.operation` | `INTERNAL` | `Ok` |
| HTTP client request | `CLIENT` | unset |
| HTTP server request | `SERVER` | unset |

All four operations end without an error, but two spans get the `Ok` status and two are left unset.

Since [#890](https://github.com/micrometer-metrics/tracing/issues/890) ([#896](https://github.com/micrometer-metrics/tracing/pull/896)), `OtelSpan.end()` sets `Ok` when the status is unset, but it can only check the status when the OpenTelemetry span is a `ReadableSpan`.
The spans created with a parent through `OtelSpanBuilder`, which the propagating handlers use for HTTP clients and servers, are wrapped in a `SpanFromSpanContext`, which isn't a `ReadableSpan`, so they're left unset.
The spans created by `DefaultTracingObservationHandler`, with `OtelTracer.nextSpan(parent)` or without a parent, are not wrapped, so they get `Ok`.
The resulting status depends on an implementation detail rather than on the operation, and it differs between the spans of the same trace.

The OpenTelemetry [Trace API](https://opentelemetry.io/docs/specs/otel/trace/api/#set-status) says that "Instrumentation Libraries SHOULD NOT set the status code to Ok, unless explicitly configured to do so", and the semantic conventions require leaving it unset for successful operations, for example for HTTP: "Span Status MUST be left unset if HTTP status code was in the 1xx, 2xx or 3xx ranges".

Expected: the bridge leaves the status of spans ending without an error unset, consistently for all spans, and sets `Ok` only when configured to do so, which could be an option for the users who asked for it in #890.
