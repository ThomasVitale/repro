package com.example.datasourcemicrometer;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import net.ttddyy.observation.tracing.QueryContext;

import org.springframework.stereotype.Component;

/**
 * Records the stopped JDBC query observations, to inspect the attributes the instrumentation records.
 */
@Component
class QueryObservations implements ObservationHandler<QueryContext> {

	private final List<QueryContext> contexts = new CopyOnWriteArrayList<>();

	@Override
	public void onStop(QueryContext context) {
		this.contexts.add(context);
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof QueryContext;
	}

	QueryContext last() {
		return this.contexts.getLast();
	}

	void clear() {
		this.contexts.clear();
	}

	static String attribute(QueryContext context, String key) {
		KeyValue keyValue = context.getAllKeyValues().stream().filter(kv -> kv.getKey().equals(key)).findFirst().orElse(null);
		return (keyValue != null) ? keyValue.getValue() : null;
	}

}
