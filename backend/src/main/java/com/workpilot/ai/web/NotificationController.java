package com.workpilot.ai.web;

import com.workpilot.ai.dto.NotificationResponse;
import com.workpilot.ai.dto.UnreadCountResponse;
import com.workpilot.ai.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    @Operation(summary = "List the signed-in user's notifications")
    public List<NotificationResponse> list() {
        return notificationService.listForCurrentUser();
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Count unread notifications for the signed-in user")
    public UnreadCountResponse unreadCount() {
        return notificationService.unreadCount();
    }

    @PostMapping("/{notificationId}/read")
    @Operation(summary = "Mark one notification as read")
    public NotificationResponse markRead(@PathVariable UUID notificationId) {
        return notificationService.markRead(notificationId);
    }

    @PostMapping("/read-all")
    @Operation(summary = "Mark all of the signed-in user's notifications as read")
    public UnreadCountResponse markAllRead() {
        return notificationService.markAllRead();
    }
}
