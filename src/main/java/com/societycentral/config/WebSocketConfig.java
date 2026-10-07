package com.societycentral.config;

import com.societycentral.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.security.Principal;
import java.util.List;

/**
 * STOMP-over-WebSocket configuration for real-time executive messaging.
 *
 * <p>Clients connect to {@code /ws} (SockJS fallback enabled) and authenticate
 * by sending their JWT in the STOMP {@code CONNECT} frame's
 * {@code Authorization: Bearer &lt;token&gt;} header. On success the user's
 * email becomes the STOMP session {@link Principal}, which lets the server
 * deliver messages to that user's personal {@code /user/queue/**}
 * destinations.</p>
 *
 * <p>The WebSocket handshake itself is left open in {@code SecurityConfig}
 * (the {@code /ws/**} path); real authentication happens here at the STOMP
 * CONNECT step, mirroring the stateless JWT model used for REST.</p>
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Reuses the same allow-list as the REST CORS policy so the websocket
        // handshake can never fall out of sync with the HTTP origins.
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns(
                        CorsConfig.ALLOWED_ORIGINS.toArray(new String[0]))
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // In-memory broker: /topic for broadcast, /queue for per-user delivery.
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor
                        .getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null
                        && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String email = resolveEmail(accessor);
                    if (email != null) {
                        accessor.setUser(new StompPrincipal(email));
                    }
                }
                return message;
            }
        });
    }

    private String resolveEmail(StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders == null || authHeaders.isEmpty()) {
            return null;
        }
        String header = authHeaders.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }
        String token = header.substring(7);
        try {
            String email = jwtUtil.extractEmail(token);
            return jwtUtil.isTokenValid(token, email) ? email : null;
        } catch (Exception ex) {
            return null;
        }
    }

    /** Minimal Principal carrying the authenticated user's email as its name. */
    private record StompPrincipal(String name) implements Principal {
        @Override
        public String getName() {
            return name;
        }
    }
}
