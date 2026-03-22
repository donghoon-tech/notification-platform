package com.notification.platform.dispatcher;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisPresenceManagerTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RedisPresenceManager presenceManager;

    @BeforeEach
    void setUp() {
        presenceManager = new RedisPresenceManager(redisTemplate);
    }

    @Test
    void setOnline_shouldStoreInRedisWithTTL() {
        String userId = "user123";
        String sessionId = "sessionABC";
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        presenceManager.setOnline(userId, sessionId);

        verify(setOperations).add(eq("presence:user123"), eq("sessionABC"));
        verify(redisTemplate).expire(eq("presence:user123"), eq(Duration.ofMinutes(5)));
        verify(valueOperations).set(eq("session:sessionABC"), eq("user123"), eq(Duration.ofMinutes(5)));
    }

    @Test
    void isOnline_shouldReturnTrueIfKeyExists() {
        String userId = "user123";
        when(redisTemplate.hasKey("presence:user123")).thenReturn(true);

        boolean result = presenceManager.isOnline(userId);

        assertThat(result).isTrue();
    }

    @Test
    void setOffline_shouldExecuteLuaScript() {
        String userId = "user123";
        String sessionId = "sessionABC";

        presenceManager.setOffline(userId, sessionId);

        verify(redisTemplate).execute(any(RedisScript.class), anyList(), eq(sessionId));
    }
}
