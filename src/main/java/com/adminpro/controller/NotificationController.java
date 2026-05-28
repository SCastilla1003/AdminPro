package com.adminpro.controller;

import com.adminpro.model.Notification;
import com.adminpro.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Controller
@RequestMapping("/notificaciones")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    @ResponseBody
    public Map<String, Object> getNotifications() {
        return Map.of(
            "unreadCount", notificationService.getUnreadCountForCurrentUser(),
            "notifications", notificationService.getRecentForCurrentUser(20)
        );
    }

    @GetMapping("/unread-count")
    @ResponseBody
    public Map<String, Long> getUnreadCount() {
        return Map.of("count", notificationService.getUnreadCountForCurrentUser());
    }

    @PostMapping("/{id}/leer")
    @ResponseBody
    public ResponseEntity<Void> markAsRead(@PathVariable Long id) {
        notificationService.markAsRead(id);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/leer-todas")
    @ResponseBody
    public ResponseEntity<Void> markAllAsRead() {
        notificationService.markAllAsRead();
        return ResponseEntity.ok().build();
    }
}
