# Reproducible issues with OpenTelemetry Semantic Conventions and Spring

Minimal Spring Boot `4.2.0-RC1` projects reproducing issues with the OpenTelemetry Semantic Conventions `v1.44.0` in upstream projects.
Each project has one failing test per issue, and a README with the issue descriptions.

| Project | Issues in |
|---|---|
| [`datasource-micrometer`](datasource-micrometer) | datasource-micrometer `2.3.0` |
| [`micrometer`](micrometer) | Micrometer `1.18.0-RC1` |
| [`micrometer-tracing`](micrometer-tracing) | Micrometer Tracing `1.8.0-RC1` |
| [`spring-framework`](spring-framework) | Spring Framework `7.1.0-RC1` |
| [`spring-boot`](spring-boot) | Spring Boot `4.2.0-RC1` |

Run the tests of a project with `./gradlew test` from its folder (Java 25, provided by `devenv shell`).
`datasource-micrometer` also needs a container runtime for Testcontainers.
