package com.adminpro.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketProxyConfig implements WebSocketConfigurer {

    private final OoProxyWebSocketHandler ooProxyWebSocketHandler;

    public WebSocketProxyConfig(OoProxyWebSocketHandler ooProxyWebSocketHandler) {
        this.ooProxyWebSocketHandler = ooProxyWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(ooProxyWebSocketHandler, "/oo-proxy/**")
                .setAllowedOrigins("*");
    }
}
