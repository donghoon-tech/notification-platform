package com.notification.platform.config;

import com.notification.platform.dispatcher.PresenceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketPresenceInterceptor implements ChannelInterceptor {

    private final PresenceManager presenceManager;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && accessor.getCommand() != null) {
            if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                String userId = accessor.getFirstNativeHeader("userId");
                if (userId != null) {
                    presenceManager.setOnline(userId, accessor.getSessionId());
                    accessor.getSessionAttributes().put("userId", userId);
                    log.info("User {} connected with session {}", userId, accessor.getSessionId());
                } else {
                    log.warn("Connect attempt without userId header for session {}", accessor.getSessionId());
                }
            } else if (StompCommand.DISCONNECT.equals(accessor.getCommand())) {
                String userId = (String) accessor.getSessionAttributes().get("userId");
                if (userId != null) {
                    presenceManager.setOffline(userId, accessor.getSessionId());
                    log.info("User {} disconnected from session {}", userId, accessor.getSessionId());
                }
            }
        }

        return message;
    }
}
