package com.hemasundar.jobs;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class ScheduledJobRunner implements CommandLineRunner {

    @Value("${app.job.name:NONE}")
    private String jobName;

    @Autowired
    private IVDataJobService ivDataJobService;

    @Autowired
    private ScreenerJobService screenerJobService;

    @Override
    public void run(String... args) throws Exception {
        if ("NONE".equalsIgnoreCase(jobName)) {
            // Normal Web Application Startup
            return;
        }

        log.info("Starting Scheduled Job execution for job: {}", jobName);

        try {
            if ("IV_DATA".equalsIgnoreCase(jobName)) {
                ivDataJobService.runIVDataCollection();
            } else if ("SCREENER".equalsIgnoreCase(jobName)) {
                screenerJobService.runScheduledScreeners();
            } else {
                log.error("Unknown job name provided: {}", jobName);
            }
        } catch (Exception e) {
            log.error("Job encountered a critical error: {}", e.getMessage(), e);
        } finally {
            log.info("Job {} execution finished. System exiting...", jobName);
            System.exit(0); // Exit the application explicitly when running as a scheduled job
        }
    }
}
