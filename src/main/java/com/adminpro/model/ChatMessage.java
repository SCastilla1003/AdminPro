package com.adminpro.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 5000)
    private String content;

    @Column(nullable = false)
    private String sender;

    private String senderFullName;

    private String senderProfilePhoto;

    @Column(nullable = false)
    private LocalDateTime timestamp = LocalDateTime.now();

    private String type; // CHAT, JOIN, PRIVATE

    private String recipientUsername;

    private boolean isRead = false;

    private boolean isEdited = false;

    private boolean isDeleted = false;

    private boolean isStarred = false;

    private Long replyToId;

    private String replyToContent;

    private String replyToSenderName;

    private String reaction;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private ChatGroup group;
}
