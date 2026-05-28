package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String content;

    @Column(nullable = false)
    private String sender; // Username

    private String senderFullName;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    private String type; // "CHAT", "JOIN", o "PRIVATE"

    private String recipientUsername; // Para mensajes privados (null = público)
}
