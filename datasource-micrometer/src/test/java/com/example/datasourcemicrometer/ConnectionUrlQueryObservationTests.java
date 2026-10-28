package com.example.datasourcemicrometer;

import javax.sql.DataSource;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static com.example.datasourcemicrometer.QueryObservations.attribute;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Queries on connections reporting the JDBC URL of other databases (see {@link UrlReportingDataSource}).
 */
class ConnectionUrlQueryObservationTests {

	@Nested
	@SpringBootTest
	@Import(ShortFormPostgresqlUrl.DataSourceConfiguration.class)
	class ShortFormPostgresqlUrl {

		@Autowired
		JdbcTemplate jdbcTemplate;

		@Test
		@DisplayName("Queries fail with a NullPointerException on PostgreSQL connections with a short-form URL")
		void queryOnShortFormUrlSucceeds() {
			// The PostgreSQL JDBC driver accepts the short form jdbc:postgresql:database (localhost:5432).
			assertThatCode(() -> this.jdbcTemplate.execute("SELECT 1")).doesNotThrowAnyException();
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class DataSourceConfiguration {

			@Bean
			DataSource dataSource() {
				return UrlReportingDataSource.create("jdbc:postgresql:mydb");
			}

		}

	}

	@Nested
	@SpringBootTest
	@Import(SqlServerUrl.DataSourceConfiguration.class)
	class SqlServerUrl {

		@Autowired
		JdbcTemplate jdbcTemplate;

		@Autowired
		QueryObservations observations;

		@Test
		@DisplayName("db.namespace is not recorded for Microsoft SQL Server")
		void sqlServerNamespace() {
			this.observations.clear();

			this.jdbcTemplate.execute("SELECT 1");

			assertThat(attribute(this.observations.last(), "db.system.name")).isEqualTo("microsoft.sql_server");
			assertThat(attribute(this.observations.last(), "db.namespace")).isEqualTo("orders");
		}

		@TestConfiguration(proxyBeanMethods = false)
		static class DataSourceConfiguration {

			@Bean
			DataSource dataSource() {
				return UrlReportingDataSource.create("jdbc:sqlserver://localhost:1433;databaseName=orders");
			}

		}

	}

}
