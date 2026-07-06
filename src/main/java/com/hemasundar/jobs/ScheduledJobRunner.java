package com.hemasundar.jobs;

import com.hemasundar.config.properties.JobConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@Log4j2
@RequiredArgsConstructor
public class ScheduledJobRunner implements CommandLineRunner {

    private final JobConfig jobConfig;
    private final IVDataJobService ivDataJobService;
    private final ScreenerJobService screenerJobService;
    private final com.hemasundar.utils.SchwabTokenGenerator schwabTokenGenerator;

    @Override
    public void run(String... args) throws Exception {
        String jobName = jobConfig.getName();
        if ("NONE".equalsIgnoreCase(jobName)) {
            // Normal Web Application Startup
            return;
        }

        log.info("Starting Scheduled Job execution for job: {}", jobName);

        int exitCode = 0;
        try {
            if ("IV_DATA".equalsIgnoreCase(jobName)) {
                ivDataJobService.runIVDataCollection();
            } else if ("SCREENER".equalsIgnoreCase(jobName)) {
                screenerJobService.runScheduledScreeners();
            } else if ("GENERATE_TOKEN".equalsIgnoreCase(jobName)) {
                schwabTokenGenerator.runInteractiveGenerator();
            } else {
                log.error("Unknown job name provided: {}", jobName);
                exitCode = 1;
            }
        } catch (Exception e) {
            log.error("Job encountered a critical error: {}", e.getMessage(), e);
            exitCode = 1;
        } finally {
            log.info("Job {} execution finished. System exiting...", jobName);
            this.exit(exitCode); // Exit the application explicitly when running as a scheduled job
        }
    }

    protected void exit(int status) {
        System.exit(status);
    }
}
