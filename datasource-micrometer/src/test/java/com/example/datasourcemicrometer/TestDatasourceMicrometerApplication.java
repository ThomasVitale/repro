package com.example.datasourcemicrometer;

import org.springframework.boot.SpringApplication;

public class TestDatasourceMicrometerApplication {

	public static void main(String[] args) {
		SpringApplication.from(DatasourceMicrometerApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
