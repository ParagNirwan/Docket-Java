package com.docket.app.service;

import com.docket.app.dto.*;
import com.docket.app.entity.AiFeedback;
import com.docket.app.entity.Conversation;
import com.docket.app.entity.ConversationMessage;
import com.docket.app.entity.Document;
import com.docket.app.enums.AiQueryMode;
import com.docket.app.enums.ConversationVisibility;
import com.docket.app.enums.FileStatus;
import com.docket.app.enums.Roles;
import com.docket.app.repository.AiFeedbackRepository;
import com.docket.app.repository.ConversationMessageRepository;
import com.docket.app.repository.ConversationRepository;
import com.docket.app.repository.DocumentRepository;
import com.docket.app.service.ai.AiQueryService;
import com.docket.app.service.ai.AiSearchScope;
import com.docket.app.service.ai.AiStreamListener;
import com.docket.app.service.ai.ConversationTurn;
import com.docket.app.service.ai.QueryRewriteService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);
    private static final int HISTORY_MESSAGES = 6;
    private static final int MAX_HISTORY_MESSAGE_LENGTH = 1000;
    private static final Pattern CITATION_MARKER = Pattern.compile("\\s*\\[\\d+]");

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final AiFeedbackRepository aiFeedbackRepository;
    private final AiQueryService aiQueryService;
    private final QueryRewriteService queryRewriteService;
    private final FolderAccessService folderAccessService;
    private final DocumentRepository documentRepository;
    private final TenantAuthorizationService tenantAuthorizationService;
    private final ObjectMapper objectMapper;

    public List<ConversationDTO> listConversations(UUID tenantId, UUID userId) {
        return conversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId)
                .stream()
                .map(c -> toDTO(c, false, null, true, new HashMap<>()))
                .toList();
    }

    public List<ConversationDTO> listSharedConversations(Authentication authentication, UUID tenantId) {
        return conversationRepository.findByTenantIdAndVisibilityOrderByUpdatedAtDesc(
                        tenantId, ConversationVisibility.TENANT)
                .stream()
                .map(c -> toDTO(c, false, authentication, false, new HashMap<>()))
                .toList();
    }

    public ConversationDTO getConversation(UUID conversationId, UUID tenantId, UUID userId) {
        return getConversation(null, conversationId, tenantId, userId);
    }

    public ConversationDTO getConversation(Authentication authentication, UUID conversationId, UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        boolean isAuthor = userId != null && userId.equals(conversation.getUserId());
        boolean isShared = conversation.getVisibility() == ConversationVisibility.TENANT;

        if (!isAuthor && !isShared) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        return toDTO(conversation, true, authentication, isAuthor, new HashMap<>());
    }

    @Transactional
    public ConversationDTO updateVisibility(UUID conversationId, UUID tenantId, UUID userId,
                                           ConversationVisibility visibility) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        conversation.setVisibility(visibility != null ? visibility : ConversationVisibility.PRIVATE);
        conversationRepository.save(conversation);

        return toDTO(conversation, false, null, true, new HashMap<>());
    }

    @Transactional
    public ConversationDTO createConversation(Authentication authentication, UUID tenantId, UUID userId, String question) {
        return createConversation(authentication, tenantId, userId, question, AiQueryMode.ASK);
    }

    @Transactional
    public ConversationDTO createConversation(Authentication authentication, UUID tenantId, UUID userId,
                                             String question, AiQueryMode mode) {
        return createConversation(authentication, tenantId, userId, question, mode, AiSearchScope.NONE, null);
    }

    @Transactional
    public ConversationDTO createConversation(Authentication authentication, UUID tenantId, UUID userId,
                                             String question, AiQueryMode mode, AiSearchScope scope,
                                             AiStreamListener listener) {
        return createConversation(authentication, tenantId, userId, question, mode, scope,
                ConversationVisibility.PRIVATE, listener);
    }

    // With a listener, the answer is also handed over piece by piece while it is written.
    // Nothing is stored unless the answer completes, so a cancelled question leaves no trace.
    @Transactional
    public ConversationDTO createConversation(Authentication authentication, UUID tenantId, UUID userId,
                                             String question, AiQueryMode mode, AiSearchScope scope,
                                             ConversationVisibility visibility,
                                             AiStreamListener listener) {
        String title = question.length() > 80 ? question.substring(0, 80) + "..." : question;

        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setVisibility(visibility != null ? visibility : ConversationVisibility.PRIVATE);

        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setConversation(conversation);
        userMsg.setRole("user");
        userMsg.setContent(question);
        conversation.getMessages().add(userMsg);

        AiQueryResponseDTO aiResponse = ask(
                authentication, tenantId, question, question, List.of(), mode, scope, listener);

        ConversationMessage aiMsg = new ConversationMessage();
        aiMsg.setConversation(conversation);
        aiMsg.setRole("ai");
        aiMsg.setContent(aiResponse.getAnswer());
        aiMsg.setSourcesJson(serializeSources(aiResponse.getSources()));
        conversation.getMessages().add(aiMsg);

        conversationRepository.save(conversation);

        return toDTO(conversation, true, authentication, true, new HashMap<>());
    }

    @Transactional
    public ConversationMessageDTO sendMessage(Authentication authentication, UUID conversationId, UUID tenantId, UUID userId, String question) {
        return sendMessage(authentication, conversationId, tenantId, userId, question, AiQueryMode.ASK);
    }

    @Transactional
    public ConversationMessageDTO sendMessage(Authentication authentication, UUID conversationId, UUID tenantId,
                                              UUID userId, String question, AiQueryMode mode) {
        return sendMessage(authentication, conversationId, tenantId, userId, question, mode, AiSearchScope.NONE, null);
    }

    @Transactional
    public ConversationMessageDTO sendMessage(Authentication authentication, UUID conversationId, UUID tenantId,
                                              UUID userId, String question, AiQueryMode mode, AiSearchScope scope,
                                              AiStreamListener listener) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        boolean isAuthor = userId != null && userId.equals(conversation.getUserId());
        boolean isShared = conversation.getVisibility() == ConversationVisibility.TENANT;
        if (!isAuthor && !isShared) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        List<ConversationTurn> history = recentHistory(conversation);
        String searchQuery = queryRewriteService.rewrite(history, question);

        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setConversation(conversation);
        userMsg.setRole("user");
        userMsg.setContent(question);
        userMsg.setRewrittenQuery(searchQuery);
        conversation.getMessages().add(userMsg);

        AiQueryResponseDTO aiResponse = ask(
                authentication, tenantId, question, searchQuery, history, mode, scope, listener);

        ConversationMessage aiMsg = new ConversationMessage();
        aiMsg.setConversation(conversation);
        aiMsg.setRole("ai");
        aiMsg.setContent(aiResponse.getAnswer());
        aiMsg.setSourcesJson(serializeSources(aiResponse.getSources()));
        conversation.getMessages().add(aiMsg);

        conversationRepository.save(conversation);

        return toMessageDTO(aiMsg, authentication, isAuthor, new HashMap<>());
    }

    @Transactional
    public ConversationMessageDTO pinMessage(Authentication authentication, UUID conversationId, UUID messageId,
                                            UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        boolean isAuthor = userId != null && userId.equals(conversation.getUserId());
        boolean isShared = conversation.getVisibility() == ConversationVisibility.TENANT;
        if (!isAuthor && !isShared) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        ConversationMessage message = conversationMessageRepository
                .findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        message.setPinnedAt(Instant.now());
        message.setPinnedBy(userId);
        conversationMessageRepository.save(message);

        return toMessageDTO(message, authentication, isAuthor, new HashMap<>());
    }

    @Transactional
    public ConversationMessageDTO unpinMessage(Authentication authentication, UUID conversationId, UUID messageId,
                                              UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        boolean isAuthor = userId != null && userId.equals(conversation.getUserId());
        boolean isShared = conversation.getVisibility() == ConversationVisibility.TENANT;
        if (!isAuthor && !isShared) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found");
        }

        ConversationMessage message = conversationMessageRepository
                .findByIdAndConversationId(messageId, conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Message not found"));

        message.setPinnedAt(null);
        message.setPinnedBy(null);
        conversationMessageRepository.save(message);

        return toMessageDTO(message, authentication, isAuthor, new HashMap<>());
    }

    public List<SavedAnswerDTO> listSavedAnswers(Authentication authentication, UUID tenantId, UUID userId) {
        List<ConversationMessage> pinned = conversationMessageRepository.findPinnedByTenantId(tenantId);
        Map<UUID, Boolean> docAccessCache = new HashMap<>();

        return pinned.stream()
                .filter(msg -> {
                    Conversation c = msg.getConversation();
                    return c.getVisibility() == ConversationVisibility.TENANT
                            || (userId != null && userId.equals(c.getUserId()));
                })
                .map(msg -> {
                    Conversation c = msg.getConversation();
                    boolean isAuthor = userId != null && userId.equals(c.getUserId());
                    List<AiSourceChunkDTO> sources = deserializeSources(msg.getSourcesJson());
                    if (!isAuthor && authentication != null) {
                        sources = filterSourcesForViewer(sources, authentication, tenantId, docAccessCache);
                    }

                    String question = findPrecedingQuestion(c, msg);
                    return new SavedAnswerDTO(
                            c.getId(),
                            c.getTitle(),
                            msg.getId(),
                            question,
                            msg.getContent(),
                            sources,
                            msg.getPinnedAt(),
                            msg.getPinnedBy()
                    );
                })
                .toList();
    }

    private String findPrecedingQuestion(Conversation conversation, ConversationMessage message) {
        if (conversation.getMessages() == null) return conversation.getTitle();
        int idx = conversation.getMessages().indexOf(message);
        if (idx > 0) {
            ConversationMessage prev = conversation.getMessages().get(idx - 1);
            if ("user".equalsIgnoreCase(prev.getRole())) {
                return prev.getContent();
            }
        }
        return conversation.getTitle();
    }

    private AiQueryResponseDTO ask(Authentication authentication, UUID tenantId, String question, String searchQuery,
                                   List<ConversationTurn> history, AiQueryMode mode, AiSearchScope scope,
                                   AiStreamListener listener) {
        return listener == null
                ? aiQueryService.query(authentication, tenantId, question, null, searchQuery, history, mode, scope)
                : aiQueryService.stream(
                        authentication, tenantId, question, null, searchQuery, history, mode, scope, listener);
    }

    @Transactional
    public void addFeedback(UUID conversationId, UUID messageId, UUID tenantId, UUID userId,
                            short rating, String reason, String comment) {
        if (rating != -1 && rating != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating must be -1 or 1");
        }
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        boolean isAiMessage = conversation.getMessages().stream()
                .anyMatch(message -> message.getId().equals(messageId) && "ai".equals(message.getRole()));
        if (!isAiMessage) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AI message not found");
        }

        AiFeedback feedback = new AiFeedback();
        feedback.setTenantId(tenantId);
        feedback.setUserId(userId);
        feedback.setMessageId(messageId);
        feedback.setRating(rating);
        feedback.setReason(reason);
        feedback.setComment(comment);
        aiFeedbackRepository.save(feedback);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID tenantId, UUID userId) {
        deleteConversation(null, conversationId, tenantId, userId);
    }

    @Transactional
    public void deleteConversation(Authentication authentication, UUID conversationId, UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantId(conversationId, tenantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        boolean isAuthor = userId != null && userId.equals(conversation.getUserId());
        boolean isAdminOrOwner = false;
        if (authentication != null) {
            Roles role = tenantAuthorizationService.getMembershipRole(authentication, tenantId);
            isAdminOrOwner = (role == Roles.OWNER || role == Roles.ADMIN);
        }
        if (!isAuthor && !isAdminOrOwner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the author or an administrator can delete this conversation");
        }
        conversationRepository.delete(conversation);
    }

    // Old citation markers are removed, because their numbers would clash with the new sources.
    private List<ConversationTurn> recentHistory(Conversation conversation) {
        List<ConversationMessage> messages = conversation.getMessages();
        return messages.subList(Math.max(0, messages.size() - HISTORY_MESSAGES), messages.size()).stream()
                .map(msg -> {
                    String content = CITATION_MARKER.matcher(msg.getContent()).replaceAll("");
                    return new ConversationTurn(msg.getRole(), content.length() > MAX_HISTORY_MESSAGE_LENGTH
                            ? content.substring(0, MAX_HISTORY_MESSAGE_LENGTH) + "..."
                            : content);
                })
                .toList();
    }

    private ConversationDTO toDTO(Conversation c, boolean includeMessages, Authentication authentication,
                                  boolean isAuthor, Map<UUID, Boolean> docAccessCache) {
        List<ConversationMessageDTO> messageDTOs = includeMessages
                ? c.getMessages().stream().map(msg -> toMessageDTO(msg, authentication, isAuthor, docAccessCache)).toList()
                : null;
        return new ConversationDTO(
                c.getId(),
                c.getTitle(),
                c.getVisibility(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getMessages().size(),
                messageDTOs
        );
    }

    private ConversationMessageDTO toMessageDTO(ConversationMessage msg, Authentication authentication,
                                                boolean isAuthor, Map<UUID, Boolean> docAccessCache) {
        List<AiSourceChunkDTO> sources = deserializeSources(msg.getSourcesJson());
        if (!isAuthor && authentication != null) {
            sources = filterSourcesForViewer(sources, authentication, msg.getConversation().getTenantId(), docAccessCache);
        }
        return new ConversationMessageDTO(
                msg.getId(),
                msg.getRole(),
                msg.getContent(),
                sources,
                msg.getCreatedAt(),
                msg.getPinnedAt(),
                msg.getPinnedBy()
        );
    }

    private List<AiSourceChunkDTO> filterSourcesForViewer(List<AiSourceChunkDTO> sources,
                                                          Authentication authentication,
                                                          UUID tenantId,
                                                          Map<UUID, Boolean> docAccessCache) {
        if (sources == null || sources.isEmpty()) {
            return Collections.emptyList();
        }
        return sources.stream()
                .filter(s -> canViewerAccessDocument(authentication, tenantId, s.getDocumentId(), docAccessCache))
                .toList();
    }

    private boolean canViewerAccessDocument(Authentication authentication,
                                            UUID tenantId,
                                            UUID documentId,
                                            Map<UUID, Boolean> docAccessCache) {
        if (documentId == null) {
            return false;
        }
        if (docAccessCache.containsKey(documentId)) {
            return docAccessCache.get(documentId);
        }
        boolean allowed = documentRepository.findById(documentId)
                .filter(doc -> tenantId.equals(doc.getTenantId()))
                .filter(doc -> doc.getFileStatus() == FileStatus.ACTIVE && doc.getDeletedAt() == null)
                .map(doc -> folderAccessService.canAccessFolder(authentication, tenantId, doc.getFolderId()))
                .orElse(false);
        docAccessCache.put(documentId, allowed);
        return allowed;
    }

    private String serializeSources(List<AiSourceChunkDTO> sources) {
        if (sources == null || sources.isEmpty()) return null;
        try {
            return objectMapper.writeValueAsString(sources);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize sources: {}", e.getMessage());
            return null;
        }
    }

    private List<AiSourceChunkDTO> deserializeSources(String json) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            log.warn("Failed to deserialize sources: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
