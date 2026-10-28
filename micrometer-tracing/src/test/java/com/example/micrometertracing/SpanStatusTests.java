package com.example.micrometertracing;

import java.util.List;
import java.util.concurrent.TimeUnit;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.micrometer.tracing.test.autoconfigure.AutoConfigureTracing;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Status of the OpenTelemetry spans created by the Micrometer Tracing OpenTelemetry bridge for operations
 * ending without an error, in a Spring Boot application with {@code spring-boot-starter-opentelemetry}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
		properties = "management.tracing.sampling.probability=1.0")
@AutoConfigureTracing
@Import(SpanStatusTests.ExporterConfiguration.class)
class SpanStatusTests {

	@Autowired
	ObservationRegistry observationRegistry;

	@Autowired
	RestClient.Builder restClientBuilder;

	@Autowired
	SdkTracerProvider tracerProvider;

	@Autowired
	InMemorySpanExporter spanExporter;

	@LocalServerPort
	int port;

	@Test
	@DisplayName("Spans of operations ending without an error get the Ok status, depending on how they're created")
	void statusLeftUnsetWithoutError() {
		RestClient restClient = this.restClientBuilder.baseUrl("http://localhost:" + this.port).build();

		Observation.createNotStarted("parent.operation", this.observationRegistry).observe(() -> {
			Observation.createNotStarted("child.operation", this.observationRegistry).observe(() -> {
			});
			restClient.get().uri("/greeting").retrieve().toBodilessEntity();
		});
		this.tracerProvider.forceFlush().join(10, TimeUnit.SECONDS);

		List<SpanData> spans = this.spanExporter.getFinishedSpanItems();
		assertThat(spans).hasSize(4);

		// OpenTelemetry Trace API, Set Status: "Instrumentation Libraries SHOULD NOT set the status code to Ok,
		// unless explicitly configured to do so." Today, the status depends on how the bridge creates the span:
		// the spans of the plain observations get Ok, while the HTTP client and server spans are left unset.
		SoftAssertions softly = new SoftAssertions();
		for (SpanData span : spans) {
			softly.assertThat(span.getStatus().getStatusCode()).as("status of span '%s' (%s)", span.getName(), span.getKind())
				.isEqualTo(StatusCode.UNSET);
		}
		softly.assertAll();
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ExporterConfiguration {

		@Bean
		InMemorySpanExporter inMemorySpanExporter() {
			return InMemorySpanExporter.create();
		}

	}

}
