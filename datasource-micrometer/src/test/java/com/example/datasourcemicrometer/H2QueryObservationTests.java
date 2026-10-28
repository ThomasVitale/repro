package com.example.datasourcemicrometer;

import java.sql.Connection;
import java.sql.Statement;

import javax.sql.DataSource;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import net.ttddyy.observation.tracing.QueryContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.example.datasourcemicrometer.QueryObservations.attribute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Queries on the in-memory H2 database auto-configured by Spring Boot, instrumented by datasource-micrometer
 * with its OpenTelemetry support. Each test asserts what the OpenTelemetry Semantic Conventions v1.44.0 define.
 */
@SpringBootTest
class H2QueryObservationTests {

	@Autowired
	DataSource dataSource;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	MeterRegistry meterRegistry;

	@Autowired
	QueryObservations observations;

	@BeforeEach
	void setUp() {
		this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS users (id INT PRIMARY KEY, name VARCHAR(20))");
		this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS orders (id INT PRIMARY KEY, user_id INT)");
		this.observations.clear();
	}

	@Test
	@DisplayName("Executing an empty Statement batch fails with an IndexOutOfBoundsException")
	void emptyStatementBatchSucceeds() throws Exception {
		try (Connection connection = this.dataSource.getConnection(); Statement statement = connection.createStatement()) {
			// Without the instrumentation, H2 returns an empty array.
			assertThatCode(statement::executeBatch).doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("error.type is not recorded for failed queries")
	void failedQueryRecordsErrorType() {
		assertThatThrownBy(() -> this.jdbcTemplate.execute("SELECT * FROM missing_table"));

		// error.type: "Conditionally Required: If and only if the operation failed", on spans and metrics.
		assertThat(attribute(this.observations.last(), "error.type")).isNotNull();
		assertThat(operationDurationTimer("SELECT", "missing_table").getId().getTag("error.type")).isNotNull();
	}

	@Test
	@DisplayName("server.port is recorded as -1 without server.address, and network.peer.port as -1")
	void serverPortNotRecordedWithoutServerAddress() {
		this.jdbcTemplate.execute("SELECT * FROM users");

		// server.port: "Conditionally Required: If using a port other than the default port for this DBMS
		// and if server.address is set". The in-memory database has neither an address nor a port.
		QueryContext context = this.observations.last();
		assertThat(attribute(context, "server.address")).isNull();
		assertThat(attribute(context, "server.port")).isNull();
		assertThat(operationDurationTimer("SELECT", "users").getId().getTag("network.peer.port")).isNull();
	}

	@Test
	@DisplayName("db.query.text is recorded on the db.client.operation.duration metric by default")
	void queryTextNotRecordedOnMetricByDefault() {
		this.jdbcTemplate.execute("SELECT * FROM users");

		// db.query.text is "Opt-In" on the db.client.operation.duration metric.
		assertThat(operationDurationTimer("SELECT", "users").getId().getTag("db.query.text")).isNull();
	}

	@Test
	@DisplayName("The query summary of INSERT ... VALUES includes a SELECT operation")
	void insertValuesQuerySummary() {
		this.jdbcTemplate.execute("INSERT INTO users VALUES (1, 'alice')");

		// The summary preserves the operations and targets of the query: there is no SELECT.
		assertThat(attribute(this.observations.last(), "db.query.summary")).isEqualTo("INSERT users");
	}

	@Test
	@DisplayName("db.operation.name doesn't match the query summary for multi-word operations")
	void multiWordOperationNameMatchesQuerySummary() {
		this.jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS products (id INT PRIMARY KEY)");

		// "If the operation and target value is populated on db.operation.name [...], it SHOULD match
		// the value used in the db.query.summary."
		QueryContext context = this.observations.last();
		assertThat(attribute(context, "db.query.summary")).isEqualTo("CREATE TABLE products");
		assertThat(attribute(context, "db.operation.name")).isEqualTo("CREATE TABLE");
	}

	@Test
	@DisplayName("db.system.name is other_sql for H2 instead of h2database")
	void h2SystemName() {
		this.jdbcTemplate.execute("SELECT * FROM users");

		// The conventions define h2database for H2: "If a value defined in this list applies to the DBMS
		// to which the request is sent, this value MUST be used."
		assertThat(attribute(this.observations.last(), "db.system.name")).isEqualTo("h2database");
	}

	private Timer operationDurationTimer(String operationName, String collectionName) {
		Timer timer = this.meterRegistry.find("db.client.operation.duration")
			.tag("db.operation.name", operationName)
			.tag("db.collection.name", collectionName)
			.timer();
		assertThat(timer).as("db.client.operation.duration timer for %s %s", operationName, collectionName).isNotNull();
		return timer;
	}

}
