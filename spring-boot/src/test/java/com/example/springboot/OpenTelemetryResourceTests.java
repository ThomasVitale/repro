package com.example.springboot;

import java.util.Properties;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.resources.Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.info.BuildProperties;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The OpenTelemetry {@link Resource} auto-configured by Spring Boot, compared with the
 * OpenTelemetry Semantic Conventions v1.44.0 for the service attributes.
 */
class OpenTelemetryResourceTests {

	private static final AttributeKey<String> SERVICE_NAME = AttributeKey.stringKey("service.name");

	private static final AttributeKey<String> SERVICE_VERSION = AttributeKey.stringKey("service.version");

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(OpenTelemetrySdkAutoConfiguration.class));

	@Test
	@DisplayName("The default service.name is unknown_service instead of unknown_service:java")
	void defaultServiceName() {
		// The OpenTelemetry SDK follows the conventions in its default resource.
		assertThat(Resource.getDefault().getAttribute(SERVICE_NAME)).isEqualTo("unknown_service:java");

		// service.name: "If the value was not specified, SDKs MUST fallback to unknown_service: concatenated
		// with the process executable name, e.g. unknown_service:bash."
		this.contextRunner.run(context -> assertThat(context.getBean(Resource.class).getAttribute(SERVICE_NAME))
			.isEqualTo("unknown_service:java"));
	}

	@Test
	@DisplayName("service.version is not set from the build information (enhancement)")
	void serviceVersionFromBuildProperties() {
		this.contextRunner.withBean(BuildProperties.class, () -> buildProperties("1.2.3"))
			.run(context -> assertThat(context.getBean(Resource.class).getAttribute(SERVICE_VERSION)).isEqualTo("1.2.3"));
	}

	private static BuildProperties buildProperties(String version) {
		Properties properties = new Properties();
		properties.setProperty("version", version);
		return new BuildProperties(properties);
	}

}
