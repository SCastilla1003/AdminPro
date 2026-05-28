package com.adminpro.repository;

import com.adminpro.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    List<ChatMessage> findTop50ByOrderByTimestampAsc();

    @Query("SELECT m FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "((m.sender = :user1 AND m.recipientUsername = :user2) OR " +
           "(m.sender = :user2 AND m.recipientUsername = :user1)) " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateMessagesBetween(
        @Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT m FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "(m.sender = :username OR m.recipientUsername = :username) " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> findPrivateMessagesForUser(@Param("username") String username);
}
