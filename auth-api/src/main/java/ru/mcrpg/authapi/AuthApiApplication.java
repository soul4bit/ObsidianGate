package ru.mcrpg.authapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import ru.mcrpg.authapi.config.AuthApiProperties;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AuthApiProperties.class)
public class AuthApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthApiApplication.class, args);
    }
}
