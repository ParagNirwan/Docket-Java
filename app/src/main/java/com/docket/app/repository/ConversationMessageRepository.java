package com.docket.app.repository;

import com.docket.app.entity.ConversationMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationMessageRepository extends JpaRepository<ConversationMessage, UUID> {

    @Query("SELECT m FROM ConversationMessage m JOIN m.conversation c WHERE c.tenantId = :tenantId AND m.pinnedAt IS NOT NULL ORDER BY m.pinnedAt DESC")
    List<ConversationMessage> findPinnedByTenantId(@Param("tenantId") UUID tenantId);

    Optional<ConversationMessage> findByIdAndConversationId(UUID id, UUID conversationId);
}
