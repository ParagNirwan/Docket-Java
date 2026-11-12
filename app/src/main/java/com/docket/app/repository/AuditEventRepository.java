package com.docket.app.repository;

import com.docket.app.entity.AuditEvent;
import com.docket.app.enums.AuditAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID>, JpaSpecificationExecutor<AuditEvent> {
    List<AuditEvent> findTop20ByTenantIdAndActionInOrderByCreatedAtDesc(UUID tenantId, Collection<AuditAction> actions);
}
