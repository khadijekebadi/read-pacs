package com.pacs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

@SpringBootApplication
public class PacsApplication extends SpringBootServletInitializer {

    // Nécessaire pour Tomcat externe
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(PacsApplication.class);
    }

    public static void main(String[] args) {
        SpringApplication.run(PacsApplication.class, args);
    }
}
