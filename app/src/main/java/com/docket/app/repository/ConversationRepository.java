package com.docket.app.repository;

import com.docket.app.entity.Conversation;
import com.docket.app.enums.ConversationVisibility;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {

    List<Conversation> findByTenantIdAndUserIdOrderByUpdatedAtDesc(UUID tenantId, UUID userId);

    List<Conversation> findByTenantIdAndVisibilityOrderByUpdatedAtDesc(UUID tenantId, ConversationVisibility visibility);

    Optional<Conversation> findByIdAndTenantIdAndUserId(UUID id, UUID tenantId, UUID userId);

    Optional<Conversation> findByIdAndTenantId(UUID id, UUID tenantId);
}
