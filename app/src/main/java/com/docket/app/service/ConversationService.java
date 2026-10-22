package com.docket.app.service;

import com.docket.app.dto.*;
import com.docket.app.entity.Conversation;
import com.docket.app.entity.ConversationMessage;
import com.docket.app.repository.ConversationRepository;
import com.docket.app.service.ai.AiQueryService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("gcp")
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ConversationRepository conversationRepository;
    private final AiQueryService aiQueryService;
    private final ObjectMapper objectMapper;

    public List<ConversationDTO> listConversations(UUID tenantId, UUID userId) {
        return conversationRepository.findByTenantIdAndUserIdOrderByUpdatedAtDesc(tenantId, userId)
                .stream()
                .map(c -> toDTO(c, false))
                .toList();
    }

    public ConversationDTO getConversation(UUID conversationId, UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        return toDTO(conversation, true);
    }

    @Transactional
    public ConversationDTO createConversation(UUID tenantId, UUID userId, String question) {
        String title = question.length() > 80 ? question.substring(0, 80) + "..." : question;

        Conversation conversation = new Conversation();
        conversation.setTenantId(tenantId);
        conversation.setUserId(userId);
        conversation.setTitle(title);

        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setConversation(conversation);
        userMsg.setRole("user");
        userMsg.setContent(question);
        conversation.getMessages().add(userMsg);

        AiQueryResponseDTO aiResponse = aiQueryService.query(tenantId, question, null);

        ConversationMessage aiMsg = new ConversationMessage();
        aiMsg.setConversation(conversation);
        aiMsg.setRole("ai");
        aiMsg.setContent(aiResponse.getAnswer());
        aiMsg.setSourcesJson(serializeSources(aiResponse.getSources()));
        conversation.getMessages().add(aiMsg);

        conversationRepository.save(conversation);

        return toDTO(conversation, true);
    }

    @Transactional
    public ConversationMessageDTO sendMessage(UUID conversationId, UUID tenantId, UUID userId, String question) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));

        ConversationMessage userMsg = new ConversationMessage();
        userMsg.setConversation(conversation);
        userMsg.setRole("user");
        userMsg.setContent(question);
        conversation.getMessages().add(userMsg);

        AiQueryResponseDTO aiResponse = aiQueryService.query(tenantId, question, null);

        ConversationMessage aiMsg = new ConversationMessage();
        aiMsg.setConversation(conversation);
        aiMsg.setRole("ai");
        aiMsg.setContent(aiResponse.getAnswer());
        aiMsg.setSourcesJson(serializeSources(aiResponse.getSources()));
        conversation.getMessages().add(aiMsg);

        conversationRepository.save(conversation);

        return toMessageDTO(aiMsg);
    }

    @Transactional
    public void deleteConversation(UUID conversationId, UUID tenantId, UUID userId) {
        Conversation conversation = conversationRepository
                .findByIdAndTenantIdAndUserId(conversationId, tenantId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
        conversationRepository.delete(conversation);
    }

    private ConversationDTO toDTO(Conversation c, boolean includeMessages) {
        List<ConversationMessageDTO> messageDTOs = includeMessages
                ? c.getMessages().stream().map(this::toMessageDTO).toList()
                : null;
        return new ConversationDTO(
                c.getId(),
                c.getTitle(),
                c.getCreatedAt(),
                c.getUpdatedAt(),
                c.getMessages().size(),
                messageDTOs
        );
    }

    private ConversationMessageDTO toMessageDTO(ConversationMessage msg) {
        return new ConversationMessageDTO(
                msg.getId(),
                msg.getRole(),
                msg.getContent(),
                deserializeSources(msg.getSourcesJson()),
                msg.getCreatedAt()
        );
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
