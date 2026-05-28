package com.adminpro.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class PreviewTokenService {

    private final ConcurrentHashMap<String, TokenData> tokens = new ConcurrentHashMap<>();

    public static record TokenData(Long documentId, long expiryTime) {}

    public String generateToken(Long documentId) {
        cleanExpiredTokens();
        String token = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + 60000; // 60 seconds
        tokens.put(token, new TokenData(documentId, expiryTime));
        return token;
    }

    public Long validateToken(String token) {
        cleanExpiredTokens();
        if (token == null) {
            return null;
        }
        TokenData data = tokens.get(token);
        if (data == null) {
            return null;
        }
        if (System.currentTimeMillis() > data.expiryTime()) {
            tokens.remove(token);
            return null;
        }
        return data.documentId();
    }

    private void cleanExpiredTokens() {
        long now = System.currentTimeMillis();
        tokens.entrySet().removeIf(entry -> now > entry.getValue().expiryTime());
    }
}
