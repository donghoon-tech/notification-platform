package com.notification.platform.dispatcher;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class RedisPresenceManager implements PresenceManager {

    private final StringRedisTemplate redisTemplate;

    private static final String PRESENCE_PREFIX = "presence:";
    private static final String SESSION_PREFIX = "session:";
    private static final Duration TTL = Duration.ofMinutes(5);

    private static final String SET_OFFLINE_LUA = 
        "redis.call('SREM', KEYS[1], ARGV[1]); " +
        "if redis.call('SCARD', KEYS[1]) == 0 then " +
        "  redis.call('DEL', KEYS[1]); " +
        "end; " +
        "redis.call('DEL', KEYS[2]); " +
        "return 1;";

    @Override
    public boolean isOnline(String userId) {
        Boolean hasKey = redisTemplate.hasKey(PRESENCE_PREFIX + userId);
        return Boolean.TRUE.equals(hasKey);
    }

    @Override
    public void setOnline(String userId, String sessionId) {
        String userKey = PRESENCE_PREFIX + userId;
        String sessionKey = SESSION_PREFIX + sessionId;
        
        redisTemplate.opsForSet().add(userKey, sessionId);
        redisTemplate.expire(userKey, TTL);
        redisTemplate.opsForValue().set(sessionKey, userId, TTL);
    }

    @Override
    public void setOffline(String userId, String sessionId) {
        String userKey = PRESENCE_PREFIX + userId;
        String sessionKey = SESSION_PREFIX + sessionId;

        redisTemplate.execute(new DefaultRedisScript<>(SET_OFFLINE_LUA, Long.class),
                List.of(userKey, sessionKey), sessionId);
    }
}
