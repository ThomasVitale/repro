package com.example.micrometer;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import io.micrometer.core.instrument.Timer;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.proto.collector.metrics.v1.ExportMetricsServiceRequest;
import io.opentelemetry.proto.metrics.v1.Metric;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metrics exported by the Micrometer OTLP registry.
 */
class OtlpMeterRegistryTests {

	@Test
	@DisplayName("Meters with the same name but different descriptions are exported as separate metrics")
	void sameNameMetersExportedAsOneMetric() throws Exception {
		AtomicReference<byte[]> exported = new AtomicReference<>();
		OtlpMeterRegistry registry = OtlpMeterRegistry.builder(OtlpConfig.DEFAULT)
			.metricsSender(request -> exported.set(request.getMetricsData()))
			.build();

		// Two meters of the same metric, with different attributes and descriptions, such as GC pauses
		// and concurrent GC phases, both reported as jvm.gc.duration by the OpenTelemetry conventions.
		Timer.builder("jvm.gc.duration")
			.description("Time spent in GC pause")
			.tag("jvm.gc.name", "G1 Young Generation")
			.register(registry)
			.record(Duration.ofMillis(5));
		Timer.builder("jvm.gc.duration")
			.description("Time spent in concurrent phase")
			.tag("jvm.gc.name", "G1 Concurrent GC")
			.register(registry)
			.record(Duration.ofMillis(50));
		registry.close();

		List<Metric> metrics = ExportMetricsServiceRequest.parseFrom(exported.get())
			.getResourceMetricsList()
			.stream()
			.flatMap(resourceMetrics -> resourceMetrics.getScopeMetricsList().stream())
			.flatMap(scopeMetrics -> scopeMetrics.getMetricsList().stream())
			.filter(metric -> metric.getName().equals("jvm.gc.duration"))
			.toList();

		// The OTLP data model identifies a metric stream by name (plus kind and unit) within a scope: the two meters
		// are two data points of the same metric, as the Micrometer OpenTelemetry bridge exports them.
		assertThat(metrics).hasSize(1);
	}

}
