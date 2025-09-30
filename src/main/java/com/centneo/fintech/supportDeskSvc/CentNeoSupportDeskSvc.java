package com.centneo.fintech.supportDeskSvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class CentNeoSupportDeskSvc {

	public static void main(String[] args) {
		SpringApplication.run(CentNeoSupportDeskSvc.class, args);
	}

}
