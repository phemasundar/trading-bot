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
        SpringApplication.run(TradingBotApplication.class, args);
    }
}
