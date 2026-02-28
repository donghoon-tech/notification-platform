package com.notification.platform;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class NotificationPlatformApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificationPlatformApplication.class, args);
	}

}
