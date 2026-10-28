package com.example.springframework;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.RouterFunctions;
import org.springframework.web.servlet.function.ServerResponse;

/**
 * Functional endpoint for the root path.
 */
@Configuration(proxyBeanMethods = false)
class RootRoute {

	@Bean
	RouterFunction<ServerResponse> rootRouterFunction() {
		return RouterFunctions.route().GET("", request -> ServerResponse.ok().body("Welcome")).build();
	}

}
