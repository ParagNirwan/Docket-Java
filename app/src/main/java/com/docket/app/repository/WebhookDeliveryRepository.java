package com.docket.app.repository;

import com.docket.app.entity.WebhookDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, UUID> {
    java.util.List<WebhookDelivery> findByStatusOrderByCreatedAtAsc(String status);
}
