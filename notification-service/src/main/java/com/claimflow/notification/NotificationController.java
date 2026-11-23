package com.claimflow.notification;

import com.claimflow.notification.NotificationService.NotificationView;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The caller's notifications. Stub authentication: this service has no user table and trusts the
 * X-User-Id header as long as it is a UUID. A real deployment validates a signed token here instead.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications")
class NotificationController {

    static final String USER_HEADER = "X-User-Id";

    private final NotificationService notificationService;

    NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/mine")
    @Operation(summary = "My notifications, newest first")
    public List<NotificationView> mine(@RequestHeader(name = USER_HEADER, required = false) String userId) {
        return notificationService.mine(caller(userId));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark one of my notifications as read")
    public NotificationView markRead(@RequestHeader(name = USER_HEADER, required = false) String userId,
                                     @PathVariable UUID id) {
        return notificationService.markRead(caller(userId), id);
    }

    private static UUID caller(String header) {
        if (header == null || header.isBlank()) {
            throw new UnauthenticatedException("Missing " + USER_HEADER + " header");
        }
        try {
            return UUID.fromString(header.trim());
        } catch (IllegalArgumentException e) {
            throw new UnauthenticatedException(USER_HEADER + " is not a valid UUID");
        }
    }
}
