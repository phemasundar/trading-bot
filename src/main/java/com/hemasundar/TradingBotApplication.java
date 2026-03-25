package com.hemasundar;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main Spring Boot application class for Trading Bot Web UI.
 * Integrates Vaadin Flow for web interface while maintaining existing TestNG
 * functionality.
 */
@SpringBootApplication
public class TradingBotApplication {

    public static void main(String[] args) {
        SpringApplication app = new SpringApplication(TradingBotApplication.class);
        
        // If this is running as a scheduled job, don't boot an unnecessary Tomcat instance on port 8080
        for (String arg : args) {
            if (arg.contains("app.job.name") && !arg.contains("NONE")) {
                app.setWebApplicationType(org.springframework.boot.WebApplicationType.NONE);
                break;
            }
        }
        
        app.run(args);
    }
}
