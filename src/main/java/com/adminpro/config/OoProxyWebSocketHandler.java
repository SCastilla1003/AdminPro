package com.adminpro.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.PingMessage;
import org.springframework.web.socket.PongMessage;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletionStage;

@Component
public class OoProxyWebSocketHandler implements WebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(OoProxyWebSocketHandler.class);

    private final Environment environment;
    private final HttpClient httpClient;

    public OoProxyWebSocketHandler(Environment environment) {
        this.environment = environment;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession clientSession) {
        String internalUrl = resolveInternalUrl();
        String wsBaseUrl = internalUrl
                .replaceFirst("^http://", "ws://")
                .replaceFirst("^https://", "wss://");

        if (wsBaseUrl.endsWith("/")) {
            wsBaseUrl = wsBaseUrl.substring(0, wsBaseUrl.length() - 1);
        }

        URI clientUri = clientSession.getUri();
        String rawPath = clientUri.getRawPath();
        int idx = rawPath.indexOf("/oo-proxy");
        String remainingPath = idx >= 0 ? rawPath.substring(idx + "/oo-proxy".length()) : rawPath;
        if (remainingPath.isEmpty()) {
            remainingPath = "/";
        }

        String query = clientUri.getRawQuery();
        String targetPath = remainingPath + (query != null ? "?" + query : "");

        URI targetUri = URI.create(wsBaseUrl + targetPath);

        log.info("WS proxy connect: {} -> {}", clientUri, targetUri);

        httpClient.newWebSocketBuilder()
                .buildAsync(targetUri, new UpstreamListener(clientSession))
                .thenAccept(upstream -> {
                    clientSession.getAttributes().put("upstream", upstream);
                    log.debug("Upstream connected for session {}", clientSession.getId());
                })
                .exceptionally(ex -> {
                    log.error("Failed to connect upstream {} : {}", targetUri, ex.getMessage());
                    try {
                        clientSession.close(CloseStatus.SERVER_ERROR);
                    } catch (IOException e) {
                        // ignore
                    }
                    return null;
                });
    }

    @Override
    public void handleMessage(WebSocketSession clientSession, WebSocketMessage<?> message) {
        WebSocket upstream = (WebSocket) clientSession.getAttributes().get("upstream");
        if (upstream == null) {
            log.warn("Upstream not ready for session {}; dropping message", clientSession.getId());
            return;
        }

        if (message instanceof TextMessage text) {
            upstream.sendText(text.getPayload(), text.isLast());
        } else if (message instanceof BinaryMessage binary) {
            ByteBuffer buf = binary.getPayload();
            upstream.sendBinary(buf, binary.isLast());
        } else if (message instanceof PingMessage) {
            upstream.sendPing(ByteBuffer.wrap(new byte[0]));
        } else if (message instanceof PongMessage) {
            // ignore unsolicited pong
        }
    }

    @Override
    public void handleTransportError(WebSocketSession clientSession, Throwable exception) {
        log.error("Transport error for session {}: {}", clientSession.getId(), exception.getMessage());
        WebSocket upstream = (WebSocket) clientSession.getAttributes().get("upstream");
        if (upstream != null) {
            upstream.abort();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession clientSession, CloseStatus closeStatus) {
        log.info("Client disconnected: session={}, status={}", clientSession.getId(), closeStatus);
        WebSocket upstream = (WebSocket) clientSession.getAttributes().get("upstream");
        if (upstream != null) {
            upstream.sendClose(closeStatus.getCode(), closeStatus.getReason());
        }
    }

    @Override
    public boolean supportsPartialMessages() {
        return false;
    }

    private String resolveInternalUrl() {
        String url = environment.getProperty("onlyoffice.internal-url");
        if (url == null || url.isBlank()) {
            url = environment.getProperty("onlyoffice.document-server-url", "http://localhost:8080");
        }
        if (url == null || url.isBlank()) {
            url = "http://localhost:8080";
        }
        return url;
    }

    private class UpstreamListener implements WebSocket.Listener {

        private final WebSocketSession clientSession;

        UpstreamListener(WebSocketSession clientSession) {
            this.clientSession = clientSession;
        }

        @Override
        public CompletionStage<?> onText(WebSocket upstream, CharSequence data, boolean last) {
            try {
                clientSession.sendMessage(new TextMessage(data.toString(), last));
            } catch (IOException e) {
                log.error("Error forwarding text to client: {}", e.getMessage());
                upstream.abort();
            }
            return WebSocket.Listener.super.onText(upstream, data, last);
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket upstream, ByteBuffer data, boolean last) {
            try {
                byte[] bytes = new byte[data.remaining()];
                data.get(bytes);
                clientSession.sendMessage(new BinaryMessage(bytes, last));
            } catch (IOException e) {
                log.error("Error forwarding binary to client: {}", e.getMessage());
                upstream.abort();
            }
            return WebSocket.Listener.super.onBinary(upstream, data, last);
        }

        @Override
        public CompletionStage<?> onPing(WebSocket upstream, ByteBuffer message) {
            try {
                clientSession.sendMessage(new PingMessage(message));
            } catch (IOException e) {
                log.error("Error forwarding ping to client: {}", e.getMessage());
            }
            return WebSocket.Listener.super.onPing(upstream, message);
        }

        @Override
        public CompletionStage<?> onPong(WebSocket upstream, ByteBuffer message) {
            try {
                clientSession.sendMessage(new PongMessage(message));
            } catch (IOException e) {
                log.error("Error forwarding pong to client: {}", e.getMessage());
            }
            return WebSocket.Listener.super.onPong(upstream, message);
        }

        @Override
        public CompletionStage<?> onClose(WebSocket upstream, int statusCode, String reason) {
            try {
                clientSession.close(new CloseStatus(statusCode, reason != null ? reason : ""));
            } catch (IOException e) {
                log.error("Error closing client session: {}", e.getMessage());
            }
            return WebSocket.Listener.super.onClose(upstream, statusCode, reason);
        }

        @Override
        public void onError(WebSocket upstream, Throwable error) {
            log.error("Upstream error: {}", error.getMessage());
            try {
                clientSession.close(CloseStatus.SERVER_ERROR);
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
