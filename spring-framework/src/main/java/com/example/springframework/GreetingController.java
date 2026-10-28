package com.example.springframework;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GreetingController {

	@GetMapping("/greeting")
	String greeting() {
		return "Hello";
	}

}
