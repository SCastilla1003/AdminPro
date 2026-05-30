package com.adminpro;

import com.adminpro.service.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AdminproApplication {

	private static final Logger log = LoggerFactory.getLogger(AdminproApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(AdminproApplication.class, args);
	}

	@Bean
	CommandLineRunner showStorageBackend(StorageService storageService) {
		return args -> log.info(">>> STORAGE BACKEND ACTIVE: {}", storageService.getClass().getSimpleName());
	}
}
