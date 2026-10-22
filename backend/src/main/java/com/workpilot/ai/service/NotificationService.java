package com.workpilot.ai.service;

import com.workpilot.ai.domain.AppNotification;
import com.workpilot.ai.domain.Organization;
import com.workpilot.ai.dto.NotificationResponse;
import com.workpilot.ai.dto.UnreadCountResponse;
import com.workpilot.ai.exception.ResourceNotFoundException;
import com.workpilot.ai.repository.NotificationRepository;
import com.workpilot.ai.repository.OrganizationRepository;
import com.workpilot.ai.security.SecurityUtils;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {

    private static final int LIST_LIMIT = 50;

    private final NotificationRepository notificationRepository;
    private final OrganizationRepository organizationRepository;

    public NotificationService(NotificationRepository notificationRepository, OrganizationRepository organizationRepository) {
        this.notificationRepository = notificationRepository;
        this.organizationRepository = organizationRepository;
    }

    @Transactional
    public void notifyUser(UUID organizationId, UUID userId, UUID executionId, String title, String body) {
        if (executionId != null && notificationRepository.existsByExecutionIdAndUserId(executionId, userId)) {
            return;
        }
        AppNotification notification = new AppNotification();
        notification.setOrganizationId(organizationId);
        notification.setUserId(userId);
        notification.setExecutionId(executionId);
        notification.setTitle(limit(title, 160));
        notification.setBody(limit(body, 1000));
        notification.setCreatedAt(Instant.now());
        try {
            notificationRepository.saveAndFlush(notification);
        } catch (DataIntegrityViolationException exception) {
            // A retry can reach this after the notification was already stored.
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationResponse> listForCurrentUser() {
        UUID userId = SecurityUtils.currentUserId();
        List<AppNotification> notifications = notificationRepository.findRecentByUserId(userId, PageRequest.of(0, LIST_LIMIT));
        Map<UUID, String> names = new HashMap<>();
        return notifications.stream()
                .map(notification -> NotificationResponse.fromEntity(notification, organizationName(names, notification.getOrganizationId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount() {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(SecurityUtils.currentUserId()));
    }

    @Transactional
    public NotificationResponse markRead(UUID notificationId) {
        AppNotification notification = notificationRepository
                .findByIdAndUserId(notificationId, SecurityUtils.currentUserId())
                .orElseThrow(() -> new ResourceNotFoundException("Notification not found"));
        if (notification.getReadAt() == null) {
            notification.setReadAt(Instant.now());
        }
        String name = organizationRepository.findById(notification.getOrganizationId())
                .map(Organization::getName)
                .orElse("Organization");
        return NotificationResponse.fromEntity(notification, name);
    }

    @Transactional
    public UnreadCountResponse markAllRead() {
        notificationRepository.markAllRead(SecurityUtils.currentUserId(), Instant.now());
        return new UnreadCountResponse(0);
    }

    private String organizationName(Map<UUID, String> names, UUID organizationId) {
        return names.computeIfAbsent(organizationId, id -> organizationRepository.findById(id)
                .map(Organization::getName)
                .orElse("Organization"));
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
