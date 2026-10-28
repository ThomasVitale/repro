package com.example.datasourcemicrometer;

import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryAttributes;
import net.ttddyy.observation.tracing.opentelemetry.OpenTelemetryAttributesManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Queries on a PostgreSQL database started with Testcontainers.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PostgresqlQueryObservationTests {

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	QueryObservations observations;

	@Autowired
	OpenTelemetryAttributesManager attributesManager;

	@Test
	@DisplayName("db.response.status_code uses the vendor error code instead of the SQLSTATE")
	void responseStatusCodeUsesSqlState() {
		this.observations.clear();

		// PostgreSQL reports a syntax error with SQLSTATE 42601 and no vendor error code (0).
		assertThatThrownBy(() -> this.jdbcTemplate.execute("SELEC 1"));

		// The attributes are computed again here, after the failure, since the attributes recorded on the
		// observation never include the error (see the failed query test in H2QueryObservationTests).
		// sql.md: "Instrumentations SHOULD populate the db.response.status_code with the most specific code
		// available to them [...] If only SQLSTATE is available, it SHOULD be used."
		OpenTelemetryAttributes attributes = this.attributesManager.getAttributes(this.observations.last());
		assertThat(attributes.getResponseStatusCode()).isEqualTo("42601");
	}

}
