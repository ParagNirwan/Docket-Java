package com.docket.app.repository;

import com.docket.app.entity.AiFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AiFeedbackRepository extends JpaRepository<AiFeedback, UUID> {
    List<AiFeedback> findByTenantIdOrderByCreatedAtDesc(UUID tenantId);
}
