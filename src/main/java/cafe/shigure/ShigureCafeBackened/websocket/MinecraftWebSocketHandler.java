package cafe.shigure.ShigureCafeBackened.websocket;

import cafe.shigure.ShigureCafeBackened.dto.ChatMessageRequest;
import cafe.shigure.ShigureCafeBackened.dto.ChatMessageResponse;
import cafe.shigure.ShigureCafeBackened.model.User;
import cafe.shigure.ShigureCafeBackened.service.MinecraftService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class MinecraftWebSocketHandler extends TextWebSocketHandler {

    private final MinecraftService minecraftService;
    private final ObjectMapper objectMapper;
    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
        log.info("New Minecraft WebSocket connection: {}", session.getId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("Minecraft WebSocket connection closed: {}, status: {}", session.getId(), status);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            ChatMessageRequest request = objectMapper.readValue(payload, ChatMessageRequest.class);
            
            // Handle regular users authenticated via JWT
            Object userObj = session.getAttributes().get("user");
            if (userObj instanceof User user) {
                // Use bound Minecraft username if available, otherwise system username
                String displayName = user.getMinecraftUsername() != null && !user.getMinecraftUsername().isEmpty()
                        ? user.getMinecraftUsername() 
                        : user.getUsername();
                
                // Override the name to prevent spoofing
                request = new ChatMessageRequest(displayName, request.message(), System.currentTimeMillis());
            }
            
            minecraftService.saveChatMessage(request, session.getId());
        } catch (Exception e) {
            log.error("Error processing WebSocket message from {}: {}", session.getId(), e.getMessage());
        }
    }

    public void handleRedisMessage(String message) {
        try {
            // Redis Serializer might wrap it in quotes or use custom format, 
            // but GenericJackson2JsonRedisSerializer used in RedisConfig will handle it if we use the right template.
            // Since MessageListenerAdapter is used, it might be already deserialized depending on configuration.
            // But let's assume it's a JSON string for now.
            Map<String, Object> data = objectMapper.readValue(message, Map.class);
            ChatMessageResponse response = objectMapper.convertValue(data.get("message"), ChatMessageResponse.class);
            
            // Broadcast to all local sessions, including the sender if they are on this instance.
            // This allows the sender to receive the message with its database-generated ID.
            broadcast(response);
        } catch (Exception e) {
            log.error("Error handling Redis message: {}", e.getMessage());
        }
    }

    private void broadcast(ChatMessageResponse message) {
        try {
            String payload = objectMapper.writeValueAsString(message);
            TextMessage textMessage = new TextMessage(payload);
            for (WebSocketSession session : sessions) {
                if (session.isOpen()) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.error("Failed to send message to session {}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage());
        }
    }
}
