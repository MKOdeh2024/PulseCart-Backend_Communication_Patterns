package com.example.PulseCart_Backend_Communication_Patterns;

import org.springframework.boot.SpringApplication;

public class TestPulseCartBackendCommunicationPatternsApplication {

	public static void main(String[] args) {
		SpringApplication.from(PulseCartBackendCommunicationPatternsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
