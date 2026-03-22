package com.notification.platform.dispatcher;

public interface PresenceManager {
    /**
     * Checks if a user is currently online.
     * @param userId The user ID to check.
     * @return true if online, false otherwise.
     */
    boolean isOnline(String userId);

    /**
     * Marks a user session as online.
     * @param userId The user ID.
     * @param sessionId The WebSocket session ID.
     */
    void setOnline(String userId, String sessionId);

    /**
     * Marks a user session as offline.
     * @param userId The user ID.
     * @param sessionId The WebSocket session ID.
     */
    void setOffline(String userId, String sessionId);
}
