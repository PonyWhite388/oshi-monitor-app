package org.aponywhite.oshimonitorapp.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SystemWebSocketHandler systemWebSocketHandler;

    @Autowired
    public WebSocketConfig(SystemWebSocketHandler systemWebSocketHandler) {
        this.systemWebSocketHandler = systemWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(systemWebSocketHandler, "/ws/system").setAllowedOrigins("*");
    }
}
