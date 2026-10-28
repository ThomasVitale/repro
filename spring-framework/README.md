# Spring Framework: `OpenTelemetryServerRequestObservationConvention`

Reproduces issues with `OpenTelemetryServerRequestObservationConvention` in Spring Framework `7.1.0-RC1`, in a Spring Boot `4.2.0-RC1` application,
compared with the [OpenTelemetry Semantic Conventions for HTTP](https://github.com/open-telemetry/semantic-conventions/blob/v1.44.0/docs/http/http-spans.md) `v1.44.0`.

The application enables the convention for Spring MVC with `management.observations.conventions=opentelemetry` (see `application.properties`).
Each test asserts what the conventions define, so it fails while the issue is open.

```shell
./gradlew test
```

None of these issues changes the set of low-cardinality key values, so fixing them doesn't affect the consistency of the metric tags discussed in [#36159](https://github.com/spring-projects/spring-framework/issues/36159).

## Issues

### 1. Requests to the root path of functional endpoints get the span name `GET ` and the `http.route` `root`

Test: `ServerRequestObservationTests#rootRoute`

For a functional endpoint mapped to the root path (`RouterFunctions.route().GET("", ...)`, see `RootRoute`), the path pattern is empty, and the convention reports:

- the span name `GET ` (with a trailing space), from `"%s %s".formatted(method, "")`;
- the `http.route` `root` (`ROUTE_ROOT`), a placeholder instead of a route template.

The conventions define `http.route` as "The matched route template for the request", which is `/` for the root path, and the span name as `{method} {target}`, with `http.route` as the target: `GET /`.
Annotated controllers aren't affected: Spring MVC reports a `@GetMapping` without a path as `/`.

### 2. `CONNECT` requests are reported as `_OTHER`, and the list of known methods can't be overridden

Test: `ServerRequestObservationTests#connectIsKnownMethod`

The convention considers known the methods in `HttpMethod.values()`, which doesn't include `CONNECT`, so `CONNECT` requests get `http.request.method` `_OTHER` (and `http.request.method_original` `CONNECT`).

The conventions: "By default, this convention defines 'known' methods as the ones listed in [RFC9110](https://www.rfc-editor.org/rfc/rfc9110.html#name-methods), the PATCH method defined in RFC5789 and the QUERY method defined in httpbis-safe-method-w-body." RFC 9110 includes `CONNECT`.
The conventions also require a way to change the list: "If the HTTP instrumentation could end up converting valid HTTP request methods to `_OTHER`, then it MUST provide a way to override the list of known HTTP methods." With such an option, applications using other methods, such as the WebDAV ones (`PROPFIND`, `MKCOL`, ...), could report them as well.

### 3. `http.request.method_original` is recorded also when it's the same as `http.request.method`

Test: `ServerRequestObservationTests#methodOriginalOnlyWhenDifferent`

`methodOriginal()` always records `http.request.method_original`, so a `GET` request gets `http.request.method_original` `GET`.

The conventions define it as "Conditionally Required: If and only if it's different than `http.request.method`", that is when the method isn't known and `http.request.method` is `_OTHER`.
It's a high-cardinality key value, so omitting it only affects the spans.
