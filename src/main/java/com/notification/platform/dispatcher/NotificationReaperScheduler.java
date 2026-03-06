package com.notification.platform.dispatcher;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.OffsetDateTime;

@Slf4j
@Configuration
@EnableScheduling
@RequiredArgsConstructor
public class NotificationReaperScheduler {

    private final JobLauncher jobLauncher;
    private final Job reaperJob;

    @Scheduled(cron = "0 0/10 * * * ?") // Every 10 minutes
    public void runReaperJob() {
        log.info("Starting Notification Reaper Batch Job...");
        
        try {
            JobParameters jobParameters = new JobParametersBuilder()
                    .addString("thresholdDateTime", OffsetDateTime.now().minusMinutes(5).toString())
                    .addLong("time", System.currentTimeMillis()) // Ensure unique run
                    .toJobParameters();
            
            jobLauncher.run(reaperJob, jobParameters);
        } catch (Exception e) {
            log.error("Failed to run Notification Reaper Job", e);
        }
    }
}
