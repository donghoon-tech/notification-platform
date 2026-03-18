package com.notification.platform.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SnowflakeIdGeneratorTest {

    @Test
    @DisplayName("Should generate unique IDs sequentially")
    void shouldGenerateUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        long id1 = generator.nextId();
        long id2 = generator.nextId();
        assertThat(id1).isLessThan(id2);
        assertThat(id1).isPositive();
    }
    
    @Test
    @DisplayName("Should handle concurrent ID generation without duplicates")
    void shouldHandleConcurrency() throws InterruptedException {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(1L);
        int threadCount = 100;
        int idsPerThread = 1000;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Long> generatedIds = new HashSet<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                for (int j = 0; j < idsPerThread; j++) {
                    synchronized (generatedIds) {
                        generatedIds.add(generator.nextId());
                    }
                }
                latch.countDown();
            });
        }
        latch.await();
        assertThat(generatedIds).hasSize(threadCount * idsPerThread);
    }
    
    @Test
    @DisplayName("Should throw exception if machine ID is out of bounds")
    void shouldValidateMachineId() {
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(1024L));
        assertThrows(IllegalArgumentException.class, () -> new SnowflakeIdGenerator(-1L));
    }
}
