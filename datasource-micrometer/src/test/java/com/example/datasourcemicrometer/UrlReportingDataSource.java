package com.example.datasourcemicrometer;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;

import javax.sql.DataSource;

import org.h2.jdbcx.JdbcDataSource;

/**
 * Creates an in-memory H2 {@link DataSource} whose connections report the given JDBC URL in their metadata,
 * which is where the instrumentation reads the URL from, to reproduce the behavior for other databases
 * without running them.
 */
final class UrlReportingDataSource {

	private UrlReportingDataSource() {
	}

	static DataSource create(String reportedUrl) {
		JdbcDataSource h2 = new JdbcDataSource();
		h2.setURL("jdbc:h2:mem:" + reportedUrl.hashCode() + ";DB_CLOSE_DELAY=-1");
		return proxy(DataSource.class, h2, (method, args, result) -> (result instanceof Connection connection)
				? proxy(Connection.class, connection, (connectionMethod, connectionArgs, connectionResult) ->
						(connectionResult instanceof DatabaseMetaData metaData)
								? proxy(DatabaseMetaData.class, metaData, (metaDataMethod, metaDataArgs, metaDataResult) ->
										metaDataMethod.getName().equals("getURL") ? reportedUrl : metaDataResult)
								: connectionResult)
				: result);
	}

	@FunctionalInterface
	private interface ResultMapper {

		Object map(java.lang.reflect.Method method, Object[] args, Object result) throws Exception;

	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, T target, ResultMapper mapper) {
		return (T) Proxy.newProxyInstance(UrlReportingDataSource.class.getClassLoader(), new Class<?>[] { type },
				(instance, method, args) -> {
					try {
						return mapper.map(method, args, method.invoke(target, args));
					}
					catch (InvocationTargetException ex) {
						throw ex.getCause();
					}
				});
	}

}
