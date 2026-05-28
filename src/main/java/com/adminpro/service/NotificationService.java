package com.adminpro.service;

import com.adminpro.model.Notification;
import com.adminpro.model.User;
import com.adminpro.repository.NotificationRepository;
import com.adminpro.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public void createNotification(String username, String message, String type, String link) {
        userRepository.findByUsername(username).ifPresent(user -> {
            Notification notification = Notification.builder()
                .user(user)
                .message(message)
                .type(type)
                .link(link)
                .build();
            notificationRepository.save(notification);

            long unreadCount = notificationRepository.countByUserAndIsReadFalse(user);
            messagingTemplate.convertAndSendToUser(username, "/queue/notifications",
                Map.of(
                    "count", unreadCount,
                    "notification", Map.of(
                        "id", notification.getId(),
                        "message", notification.getMessage(),
                        "type", notification.getType(),
                        "link", notification.getLink() != null ? notification.getLink() : "",
                        "isRead", notification.isRead(),
                        "createdAt", notification.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    )
                ));
        });
    }

    public void createNotificationForAll(String message, String type, String link) {
        List<User> users = userRepository.findAll();
        for (User user : users) {
            if (user.isEnabled()) {
                Notification notification = Notification.builder()
                    .user(user)
                    .message(message)
                    .type(type)
                    .link(link)
                    .build();
                notificationRepository.save(notification);
            }
        }
    }

    public List<Notification> getUnreadForCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
            .map(notificationRepository::findByUserAndIsReadFalseOrderByCreatedAtDesc)
            .orElse(List.of());
    }

    public long getUnreadCountForCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
            .map(notificationRepository::countByUserAndIsReadFalse)
            .orElse(0L);
    }

    public List<Notification> getRecentForCurrentUser(int limit) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
            .map(user -> {
                List<Notification> all = notificationRepository.findByUserOrderByCreatedAtDesc(user);
                return all.size() > limit ? all.subList(0, limit) : all;
            })
            .orElse(List.of());
    }

    public void markAsRead(Long notificationId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            n.setRead(true);
            notificationRepository.save(n);
        });
    }

    public void markAllAsRead() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        userRepository.findByUsername(username).ifPresent(user -> {
            List<Notification> unread = notificationRepository.findByUserAndIsReadFalseOrderByCreatedAtDesc(user);
            unread.forEach(n -> n.setRead(true));
            notificationRepository.saveAll(unread);
        });
    }
}
