package com.adminpro.repository;

import com.adminpro.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findTop50ByTypeNotOrderByTimestampAsc(String typeExcluded);

    List<ChatMessage> findTop50ByTypeInOrderByTimestampAsc(List<String> types);

    @Query("SELECT m FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "((m.sender = :user1 AND m.recipientUsername = :user2) OR " +
           "(m.sender = :user2 AND m.recipientUsername = :user1)) AND m.isDeleted = false " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateMessagesBetween(
        @Param("user1") String user1, @Param("user2") String user2);

    @Query("SELECT m FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "((m.sender = :username AND m.recipientUsername = :partner) OR " +
           "(m.sender = :partner AND m.recipientUsername = :username)) AND m.isDeleted = false " +
           "ORDER BY m.timestamp DESC LIMIT 1")
    Optional<ChatMessage> findLastPrivateMessage(
        @Param("username") String username, @Param("partner") String partner);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "m.sender = :partner AND m.recipientUsername = :username AND m.isRead = false AND m.isDeleted = false")
    long countUnreadFrom(@Param("username") String username, @Param("partner") String partner);

    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE m.type = 'PRIVATE' AND " +
           "m.recipientUsername = :username AND m.isRead = false AND m.isDeleted = false")
    long countTotalUnread(@Param("username") String username);

    @Modifying
    @Transactional
    @Query("UPDATE ChatMessage m SET m.isRead = true WHERE m.type = 'PRIVATE' AND " +
           "m.sender = :partner AND m.recipientUsername = :username AND m.isRead = false")
    void markPrivateMessagesAsRead(@Param("username") String username, @Param("partner") String partner);

    @Query("SELECT m FROM ChatMessage m WHERE m.type = 'CHAT' AND m.isDeleted = false ORDER BY m.timestamp DESC")
    List<ChatMessage> findRecentPublicMessages();

    @Query("SELECT m FROM ChatMessage m WHERE m.isStarred = true AND " +
           "(m.sender = :username OR m.recipientUsername = :username) AND m.isDeleted = false " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> findStarredMessages(@Param("username") String username);

    @Query("SELECT m FROM ChatMessage m WHERE m.isDeleted = false AND " +
           "(m.content LIKE %:query% OR m.senderFullName LIKE %:query%) AND " +
           "((m.recipientUsername = :username AND m.type = 'PRIVATE') OR " +
           "(m.sender = :username AND m.type = 'PRIVATE') OR m.type = 'CHAT') " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> searchMessages(@Param("username") String username, @Param("query") String query);

    @Query("SELECT DISTINCT m.sender FROM ChatMessage m WHERE m.type = 'PRIVATE' AND m.recipientUsername = :username " +
           "UNION SELECT DISTINCT m.recipientUsername FROM ChatMessage m WHERE m.type = 'PRIVATE' AND m.sender = :username")
    List<String> findConversationPartners(@Param("username") String username);
}
