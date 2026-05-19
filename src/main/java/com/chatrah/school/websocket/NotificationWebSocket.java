package com.chatrah.school.websocket;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.websocket.*;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

@ServerEndpoint("/ws/notifications/{userId}")
@ApplicationScoped
public class NotificationWebSocket {

    private static final Logger LOG = Logger.getLogger(NotificationWebSocket.class.getName());
    private static final Map<String, Session> sessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") String userId) {
        sessions.put(userId, session);
        LOG.info("WebSocket connected: " + userId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") String userId) {
        sessions.remove(userId);
    }

    @OnError
    public void onError(Session session, @PathParam("userId") String userId, Throwable error) {
        sessions.remove(userId);
    }

    /**
     * Send notification to a specific user
     */
    public static void sendToUser(String userId, String message) {
        Session session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            session.getAsyncRemote().sendText(message);
        }
    }

    /**
     * Send notification to all connected users with a specific role
     */
    public static void broadcast(String message) {
        sessions.values().forEach(s -> {
            if (s.isOpen()) s.getAsyncRemote().sendText(message);
        });
    }

    /**
     * Send to multiple specific users
     */
    public static void sendToUsers(java.util.List<String> userIds, String message) {
        userIds.forEach(id -> sendToUser(id, message));
    }

    public static int getConnectedCount() {
        return sessions.size();
    }
}
