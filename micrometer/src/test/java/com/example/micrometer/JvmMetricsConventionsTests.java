package com.example.micrometer;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JVM metrics with the OpenTelemetry conventions, enabled in Spring Boot with
 * {@code management.observations.conventions=opentelemetry} (see application.properties), compared with the
 * OpenTelemetry Semantic Conventions v1.44.0 for JVM metrics.
 */
@SpringBootTest
class JvmMetricsConventionsTests {

	@Autowired
	MeterRegistry meterRegistry;

	@Test
	@DisplayName("jvm.memory.limit is reported as -1 for memory pools without a defined maximum")
	void memoryLimitNotReportedWithoutMaximum() {
		// Metaspace has no maximum unless -XX:MaxMetaspaceSize is set, so MemoryUsage.getMax() returns -1.
		assertThat(ManagementFactory.getMemoryPoolMXBeans().stream().map(MemoryPoolMXBean::getUsage))
			.anyMatch(usage -> usage.getMax() == -1);

		// jvm.memory.limit: "Measure of max obtainable memory." A pool without a maximum has no limit to report.
		assertThat(this.meterRegistry.find("jvm.memory.limit").gauges())
			.isNotEmpty()
			.allSatisfy(gauge -> assertThat(gauge.value()).as("jvm.memory.limit %s", gauge.getId().getTags()).isNotEqualTo(-1));
	}

	@Test
	@DisplayName("JVM metrics don't use the UCUM units defined by the conventions")
	void jvmMetricsUseConventionUnits() {
		SoftAssertions softly = new SoftAssertions();
		softly.assertThat(baseUnit("jvm.memory.used")).as("jvm.memory.used").isEqualTo("By");
		softly.assertThat(baseUnit("jvm.memory.limit")).as("jvm.memory.limit").isEqualTo("By");
		softly.assertThat(baseUnit("jvm.thread.count")).as("jvm.thread.count").isEqualTo("{thread}");
		softly.assertThat(baseUnit("jvm.class.count")).as("jvm.class.count").isEqualTo("{class}");
		softly.assertThat(baseUnit("jvm.cpu.count")).as("jvm.cpu.count").isEqualTo("{cpu}");
		softly.assertThat(baseUnit("jvm.cpu.recent_utilization")).as("jvm.cpu.recent_utilization").isEqualTo("1");
		softly.assertAll();
	}

	private String baseUnit(String meterName) {
		Meter meter = this.meterRegistry.find(meterName).meter();
		assertThat(meter).as("meter %s", meterName).isNotNull();
		return meter.getId().getBaseUnit();
	}

}
