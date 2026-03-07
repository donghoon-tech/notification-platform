package com.notification.platform.config.batch;

import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import com.notification.platform.messaging.event.NotificationRequestEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.batch.item.data.RepositoryItemReader;
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.Sort;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationReaperJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final NotificationRequestRepository repository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    private static final String TOPIC = "notification.requests";

    @Bean
    public Job reaperJob() {
        return new JobBuilder("reaperJob", jobRepository)
                .start(reaperStep())
                .build();
    }

    @Bean
    public Step reaperStep() {
        return new StepBuilder("reaperStep", jobRepository)
                .<NotificationRequest, NotificationRequest>chunk(10, transactionManager)
                .reader(reaperReader(null))
                .processor(reaperProcessor())
                .writer(reaperWriter())
                .build();
    }

    @Bean
    @StepScope
    public RepositoryItemReader<NotificationRequest> reaperReader(
            @Value("#{jobParameters['thresholdDateTime']}") String thresholdStr) {
        
        OffsetDateTime threshold = (thresholdStr != null) 
                ? OffsetDateTime.parse(thresholdStr) 
                : OffsetDateTime.now().minusMinutes(5);

        return new RepositoryItemReaderBuilder<NotificationRequest>()
                .name("reaperReader")
                .repository(repository)
                .methodName("findAllByStatusAndRequestedAtBefore")
                .arguments(NotificationIngressStatus.ACCEPTED, threshold)
                .pageSize(10)
                .sorts(Collections.singletonMap("requestedAt", Sort.Direction.ASC))
                .build();
    }

    @Bean
    public ItemProcessor<NotificationRequest, NotificationRequest> reaperProcessor() {
        return request -> {
            log.info("Processing stale request for reaper: {}", request.getId());
            return request;
        };
    }

    @Bean
    public ItemWriter<NotificationRequest> reaperWriter() {
        return items -> {
            for (NotificationRequest request : items) {
                try {
                    NotificationRequestEvent event = NotificationRequestEvent.builder()
                            .requestId(request.getId())
                            .recipientId(request.getRecipientId())
                            .channel(request.getChannel())
                            .targetAddress(request.getTargetAddress())
                            .priority(request.getPriority())
                            .payload(request.getPayload())
                            .build();

                    kafkaTemplate.send(TOPIC, request.getRecipientId(), event);
                    request.updateStatus(NotificationIngressStatus.DISPATCHED);
                    repository.save(request);
                    log.info("Reaper successfully re-dispatched request: {}", request.getId());
                } catch (Exception e) {
                    log.error("Reaper failed to re-dispatch request: {}", request.getId(), e);
                    // Leave status as ACCEPTED for next reaper run
                }
            }
        };
    }
}
