package com.docket.app.service;

import com.docket.app.dto.AuditEventDTO;
import com.docket.app.entity.AuditEvent;
import com.docket.app.entity.Tenant;
import com.docket.app.entity.User;
import com.docket.app.enums.AuditAction;
import com.docket.app.enums.AuditActorType;
import com.docket.app.enums.Permission;
import com.docket.app.repository.AuditEventRepository;
import com.docket.app.repository.TenantRepository;
import com.docket.app.repository.UserRepository;
import com.docket.app.security.ClientRequestInfo;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.Writer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

// Writes and reads the audit log. Called from inside a transaction, an event joins it:
// if the action rolls back the event does too, so the log never claims something
// happened that didn't.
@Service
@RequiredArgsConstructor
public class AuditService {
    private static final int MAX_PAGE_SIZE = 100;
    private static final int EXPORT_BATCH_SIZE = 1000;
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final AuditEventRepository auditEventRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final ClientRequestInfo clientRequestInfo;

    // Records an action taken by the logged-in user inside an organization.
    @Transactional
    public void record(
            Authentication authentication,
            UUID tenantId,
            AuditAction action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        UUID actorId = userRepository.findByEmailIgnoreCase(authentication.getName())
                .map(User::getId)
                .orElse(null);
        record(tenantId, actorId, action, targetType, targetId, metadata);
    }

    // Records something a scheduled job did on its own, with no person behind it.
    @Transactional
    public void recordSystem(UUID tenantId, AuditAction action, String targetType, UUID targetId, Map<String, Object> metadata) {
        save(tenantId, null, AuditActorType.SYSTEM, action, targetType, targetId, metadata);
    }

    // Records something that happened to an account rather than inside one organization,
    // such as a login. actorId is null if we don't know who it was.
    @Transactional
    public void recordAccountEvent(
            UUID actorId,
            AuditAction action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        record((UUID) null, actorId, action, targetType, targetId, metadata);
    }

    // Records an action inside an organization where the actor is already known.
    @Transactional
    public void record(
            UUID tenantId,
            UUID actorId,
            AuditAction action,
            String targetType,
            UUID targetId,
            Map<String, Object> metadata
    ) {
        save(tenantId, actorId, AuditActorType.USER, action, targetType, targetId, metadata);
    }

    private void save(UUID tenantId, UUID actorId, AuditActorType actorType, AuditAction action,
                      String targetType, UUID targetId, Map<String, Object> metadata) {
        AuditEvent event = new AuditEvent();
        event.setTenantId(tenantId);
        event.setActorUserId(actorId);
        event.setActorType(actorType);
        event.setAction(action);
        event.setTargetType(targetType);
        event.setTargetId(targetId);
        event.setMetadata(metadata);
        event.setIp(clientRequestInfo.ip());
        event.setUserAgent(clientRequestInfo.userAgent());
        event.setCreatedAt(Instant.now());
        auditEventRepository.save(event);
    }

    public Page<AuditEventDTO> search(String slug, Filter filter, int page, int size, Authentication authentication) {
        Tenant tenant = requireAuditAccess(slug, authentication);
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE), NEWEST_FIRST);
        Page<AuditEvent> events = auditEventRepository.findAll(matching(tenant.getId(), filter), pageable);
        Map<UUID, User> actors = loadActors(events.getContent());
        return events.map(event -> toDTO(event, actors));
    }

    public void exportCsv(String slug, Filter filter, Authentication authentication, Writer writer) throws IOException {
        Tenant tenant = requireAuditAccess(slug, authentication);
        record(authentication, tenant.getId(), AuditAction.AUDIT_LOG_EXPORTED, null, null, null);

        // Fixing the end time keeps pages stable: new events can't push rows between
        // pages and make them appear twice.
        Instant now = Instant.now();
        Instant to = filter.to() == null || filter.to().isAfter(now) ? now : filter.to();
        Specification<AuditEvent> matching = matching(tenant.getId(), new Filter(filter.actor(), filter.action(), filter.from(), to));

        writer.write("created_at,actor_email,actor_type,action,target_type,target_id,ip,user_agent,metadata\r\n");
        Page<AuditEvent> page;
        int pageNumber = 0;
        do {
            page = auditEventRepository.findAll(matching, PageRequest.of(pageNumber++, EXPORT_BATCH_SIZE, NEWEST_FIRST));
            Map<UUID, User> actors = loadActors(page.getContent());
            for (AuditEvent event : page.getContent()) {
                User actor = actors.get(event.getActorUserId());
                writer.write(String.join(",",
                        csv(event.getCreatedAt()),
                        csv(actor == null ? null : actor.getEmail()),
                        csv(event.getActorType()),
                        csv(event.getAction()),
                        csv(event.getTargetType()),
                        csv(event.getTargetId()),
                        csv(event.getIp()),
                        csv(event.getUserAgent()),
                        csv(event.getMetadata())));
                writer.write("\r\n");
            }
        } while (page.hasNext());
        writer.flush();
    }

    private Tenant requireAuditAccess(String slug, Authentication authentication) {
        Tenant tenant = tenantRepository.findBySlug(slug)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Tenant not found"));
        tenantAuthorizationService.requirePermission(authentication, tenant.getId(), Permission.AUDIT_READ);
        return tenant;
    }

    private Specification<AuditEvent> matching(UUID tenantId, Filter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("tenantId"), tenantId));
            if (filter.actor() != null && !filter.actor().isBlank()) {
                // An unknown actor must match nothing, not everything.
                UUID actorId = userRepository.findByEmailIgnoreCase(filter.actor().trim())
                        .map(User::getId)
                        .orElse(new UUID(0, 0));
                predicates.add(cb.equal(root.get("actorUserId"), actorId));
            }
            if (filter.action() != null) {
                predicates.add(cb.equal(root.get("action"), filter.action()));
            }
            if (filter.from() != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.<Instant>get("createdAt"), filter.from()));
            }
            if (filter.to() != null) {
                predicates.add(cb.lessThanOrEqualTo(root.<Instant>get("createdAt"), filter.to()));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private Map<UUID, User> loadActors(List<AuditEvent> events) {
        Set<UUID> actorIds = events.stream()
                .map(AuditEvent::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return actorIds.isEmpty() ? Map.of()
                : userRepository.findAllById(actorIds).stream().collect(Collectors.toMap(User::getId, user -> user));
    }

    private AuditEventDTO toDTO(AuditEvent event, Map<UUID, User> actors) {
        User actor = actors.get(event.getActorUserId());
        return new AuditEventDTO(
                event.getId(),
                event.getActorUserId(),
                actor == null ? null : actor.getName(),
                actor == null ? null : actor.getEmail(),
                event.getActorType(),
                event.getAction(),
                event.getTargetType(),
                event.getTargetId(),
                event.getMetadata(),
                event.getIp(),
                event.getUserAgent(),
                event.getCreatedAt());
    }

    // Filenames and user agents are attacker-controlled, and spreadsheets run cells
    // starting with these characters as formulas, so those get a leading apostrophe.
    static String csv(Object value) {
        if (value == null) {
            return "";
        }
        String text = value.toString();
        if (!text.isEmpty() && "=+-@\t\r".indexOf(text.charAt(0)) >= 0) {
            text = "'" + text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    public record Filter(String actor, AuditAction action, Instant from, Instant to) {
    }
}
