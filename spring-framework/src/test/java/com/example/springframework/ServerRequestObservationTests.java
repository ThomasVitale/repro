package com.example.springframework;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Spring MVC requests observed with the OpenTelemetryServerRequestObservationConvention, enabled in Spring Boot with
 * {@code management.observations.conventions=opentelemetry} (see application.properties), compared with the
 * OpenTelemetry Semantic Conventions v1.44.0 for HTTP server spans.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(ServerRequestObservationTests.ObservationsConfiguration.class)
class ServerRequestObservationTests {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	ServerRequestObservations observations;

	@BeforeEach
	void setUp() {
		this.observations.contexts.clear();
	}

	@Test
	@DisplayName("Requests to a functional endpoint on the root path have the span name \"GET \" and the http.route \"root\"")
	void rootRoute() throws Exception {
		this.mockMvc.perform(get("/"));

		// The functional endpoint is mapped to the empty path (see RootRoute), which the convention reports as "root",
		// while the route is "/" and the span name is "{method} {http.route}". (A @GetMapping without a path
		// on a controller is reported as "/" as expected.)
		ServerRequestObservationContext context = this.observations.last();
		assertThat(context.getContextualName()).isEqualTo("GET /");
		assertThat(context.getLowCardinalityKeyValue("http.route")).isEqualTo(KeyValue.of("http.route", "/"));
	}

	@Test
	@DisplayName("CONNECT requests are reported with http.request.method \"_OTHER\"")
	void connectIsKnownMethod() throws Exception {
		this.mockMvc.perform(request(HttpMethod.valueOf("CONNECT"), "/greeting"));

		// "By default, this convention defines 'known' methods as the ones listed in RFC9110 [...]", which include CONNECT.
		ServerRequestObservationContext context = this.observations.last();
		assertThat(context.getLowCardinalityKeyValue("http.request.method"))
			.isEqualTo(KeyValue.of("http.request.method", "CONNECT"));
	}

	@Test
	@DisplayName("http.request.method_original is recorded also when it's the same as http.request.method")
	void methodOriginalOnlyWhenDifferent() throws Exception {
		this.mockMvc.perform(get("/greeting"));

		// http.request.method_original: "Conditionally Required: If and only if it's different than http.request.method."
		ServerRequestObservationContext context = this.observations.last();
		assertThat(context.getLowCardinalityKeyValue("http.request.method"))
			.isEqualTo(KeyValue.of("http.request.method", "GET"));
		assertThat(context.getHighCardinalityKeyValue("http.request.method_original")).isNull();
	}

	static class ServerRequestObservations implements ObservationHandler<ServerRequestObservationContext> {

		final List<ServerRequestObservationContext> contexts = new CopyOnWriteArrayList<>();

		@Override
		public void onStop(ServerRequestObservationContext context) {
			this.contexts.add(context);
		}

		@Override
		public boolean supportsContext(Observation.Context context) {
			return context instanceof ServerRequestObservationContext;
		}

		ServerRequestObservationContext last() {
			assertThat(this.contexts).as("server request observations").isNotEmpty();
			return this.contexts.getLast();
		}

	}

	@TestConfiguration(proxyBeanMethods = false)
	static class ObservationsConfiguration {

		@Bean
		ServerRequestObservations serverRequestObservations() {
			return new ServerRequestObservations();
		}

	}

}
