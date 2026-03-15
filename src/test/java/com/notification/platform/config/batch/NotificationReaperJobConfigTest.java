package com.notification.platform.config.batch;

import com.notification.platform.domain.entity.NotificationRequest;
import com.notification.platform.domain.enums.NotificationChannel;
import com.notification.platform.domain.enums.NotificationIngressStatus;
import com.notification.platform.domain.repository.NotificationRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.ExitStatus;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.test.JobLauncherTestUtils;
import org.springframework.batch.test.context.SpringBatchTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@SpringBatchTest
@SpringBootTest
@ActiveProfiles("test")
class NotificationReaperJobConfigTest {

    @Autowired
    private JobLauncherTestUtils jobLauncherTestUtils;

    @Autowired
    private NotificationRequestRepository repository;

    @MockBean(name = "defaultRetryTopicKafkaTemplate")
    private KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    @DisplayName("Reaper Job should process ACCEPTED requests older than 5 minutes based on requestedAt")
    void reaperJob_Success() throws Exception {
        // given: 1 old ACCEPTED request, 1 new ACCEPTED request
        UUID oldId = UUID.randomUUID();
        NotificationRequest oldRequest = NotificationRequest.builder()
                .id(oldId)
                .idempotencyKey("old-key")
                .recipientId("user-1")
                .channel(NotificationChannel.EMAIL)
                .producerName("TEST")
                .priority("NORMAL")
                .status(NotificationIngressStatus.ACCEPTED)
                .requestedAt(OffsetDateTime.now().minusMinutes(10)) // Cleanly set business time
                .build();
        repository.save(oldRequest);

        UUID newId = UUID.randomUUID();
        NotificationRequest newRequest = NotificationRequest.builder()
                .id(newId)
                .idempotencyKey("new-key")
                .recipientId("user-2")
                .channel(NotificationChannel.EMAIL)
                .producerName("TEST")
                .priority("NORMAL")
                .status(NotificationIngressStatus.ACCEPTED)
                .requestedAt(OffsetDateTime.now()) // New request
                .build();
        repository.save(newRequest); 

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("thresholdDateTime", OffsetDateTime.now().minusMinutes(5).toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED);
        
        NotificationRequest processedOld = repository.findById(oldId).orElseThrow();
        assertThat(processedOld.getStatus()).isEqualTo(NotificationIngressStatus.DISPATCHED);
        
        NotificationRequest processedNew = repository.findById(newId).orElseThrow();
        assertThat(processedNew.getStatus()).isEqualTo(NotificationIngressStatus.ACCEPTED);

        verify(kafkaTemplate, times(1)).send(anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Reaper Job should keep status as ACCEPTED if Kafka dispatch fails")
    void reaperJob_KafkaFailure() throws Exception {
        // given
        UUID id = UUID.randomUUID();
        NotificationRequest request = NotificationRequest.builder()
                .id(id)
                .idempotencyKey("fail-key")
                .recipientId("user-1")
                .channel(NotificationChannel.EMAIL)
                .producerName("TEST")
                .priority("NORMAL")
                .status(NotificationIngressStatus.ACCEPTED)
                .requestedAt(OffsetDateTime.now().minusMinutes(10))
                .build();
        repository.save(request);

        doThrow(new RuntimeException("Kafka Down")).when(kafkaTemplate).send(anyString(), anyString(), any());

        JobParameters jobParameters = new JobParametersBuilder()
                .addString("thresholdDateTime", OffsetDateTime.now().minusMinutes(5).toString())
                .addLong("time", System.currentTimeMillis())
                .toJobParameters();

        // when
        JobExecution jobExecution = jobLauncherTestUtils.launchJob(jobParameters);

        // then
        assertThat(jobExecution.getExitStatus()).isEqualTo(ExitStatus.COMPLETED); 
        
        NotificationRequest result = repository.findById(id).orElseThrow();
        assertThat(result.getStatus()).isEqualTo(NotificationIngressStatus.ACCEPTED); 
    }
}
