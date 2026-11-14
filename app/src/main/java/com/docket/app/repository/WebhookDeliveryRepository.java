package com.docket.app.repository;

import com.docket.app.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    List<WebhookDelivery> findByStatusOrderByCreatedAtAsc(String status);

    List<WebhookDelivery> findByStatusAndNextAttemptAtBeforeOrderByCreatedAtAsc(String status, Instant before);
}
