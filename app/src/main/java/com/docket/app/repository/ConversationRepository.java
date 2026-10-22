package com.docket.app.repository;

import com.docket.app.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByTenantIdAndUserIdOrderByUpdatedAtDesc(UUID tenantId, UUID userId);

    Optional<Conversation> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);
}
