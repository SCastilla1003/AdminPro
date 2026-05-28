package com.adminpro.service;

import org.springframework.stereotype.Service;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

@Service
public class PreviewTokenService {

    private final ConcurrentHashMap<String, TokenData> tokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, String> docToToken = new ConcurrentHashMap<>();

    public static record TokenData(Long documentId, long expiryTime) {}

    public String generateToken(Long documentId) {
        cleanExpiredTokens();
        
        // Si ya existe un token válido para este documento, reutilízalo
        String existingToken = docToToken.get(documentId);
        if (existingToken != null) {
            TokenData data = tokens.get(existingToken);
            if (data != null && System.currentTimeMillis() < data.expiryTime()) {
                return existingToken;
            }
        }

        String token = UUID.randomUUID().toString();
        long expiryTime = System.currentTimeMillis() + 300000; // 5 minutes
        tokens.put(token, new TokenData(documentId, expiryTime));
        docToToken.put(documentId, token);
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
        tokens.entrySet().removeIf(entry -> {
            boolean expired = now > entry.getValue().expiryTime();
            if (expired) {
                docToToken.remove(entry.getValue().documentId());
            }
            return expired;
        });
    }
}
