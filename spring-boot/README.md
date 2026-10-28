# Spring Boot: OpenTelemetry resource

Reproduces issues with the OpenTelemetry `Resource` auto-configured by Spring Boot `4.2.0-RC1` (`spring-boot-starter-opentelemetry`),
compared with the [OpenTelemetry Semantic Conventions for services](https://github.com/open-telemetry/semantic-conventions/blob/v1.44.0/docs/registry/attributes/service.md) `v1.44.0`.

Each test asserts what the conventions define (or what's proposed), so it fails while the issue is open.

```shell
./gradlew test
```

## Issues

### 1. The default `service.name` is `unknown_service` instead of `unknown_service:java`

Test: `OpenTelemetryResourceTests#defaultServiceName`

Without `spring.application.name`, `OpenTelemetryResourceAttributes` sets `service.name` to `unknown_service` (`DEFAULT_SERVICE_NAME`), which replaces the `unknown_service:java` of the OpenTelemetry SDK default resource.

The conventions: "If the value was not specified, SDKs MUST fallback to `unknown_service:` concatenated with the process executable name, e.g. `unknown_service:bash`. If the process executable name is not available, the value MUST be set to `unknown_service`."
The default was introduced in [#38218](https://github.com/spring-projects/spring-boot/issues/38218) / [#38219](https://github.com/spring-projects/spring-boot/issues/38219), which considered only the `unknown_service` part of the rule. Keeping the SDK value (`Resource.getDefault()`) would align Spring Boot with the conventions and with other OpenTelemetry SDK-based applications.

### 2. Set `service.version` from the build information (enhancement)

Test: `OpenTelemetryResourceTests#serviceVersionFromBuildProperties`

Spring Boot sets `service.name` and `service.namespace` from `spring.application.name` and `spring.application.group`, but not `service.version`, a stable attribute ("The version string of the service API or implementation") that backends use to compare releases.
When the build information is available (`BuildProperties`, from the `build-info` goal or task of the Spring Boot build plugins), its version could be the default value of `service.version`, as `spring.application.name` is for `service.name`.
