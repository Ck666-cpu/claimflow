package com.claimflow.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

interface NotificationRepository extends JpaRepository<Notification, UUID> {

    boolean existsByEventId(UUID eventId);

    List<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId);
}
