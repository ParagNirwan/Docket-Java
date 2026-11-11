package com.docket.app.service;

import com.docket.app.dto.AiQueryResponseDTO;
import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.dto.ConversationMessageDTO;
import com.docket.app.entity.Conversation;
import com.docket.app.entity.ConversationMessage;
import com.docket.app.enums.AiQueryMode;
import com.docket.app.repository.AiFeedbackRepository;
import com.docket.app.repository.ConversationRepository;
import com.docket.app.service.ai.AiQueryService;
import com.docket.app.service.ai.AiSearchScope;
import com.docket.app.service.ai.AiStreamListener;
import com.docket.app.service.ai.ConversationTurn;
import com.docket.app.service.ai.QueryRewriteService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    private static final String FOLLOW_UP = "When does it expire?";
    private static final String REWRITTEN = "When does the lease expire?";

    private final Authentication authentication =
            new UsernamePasswordAuthenticationToken("ada@example.com", "credentials", List.of());
    private final UUID conversationId = UUID.randomUUID();
    private final UUID tenantId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();

    @Mock private ConversationRepository conversationRepository;
    @Mock private AiFeedbackRepository aiFeedbackRepository;
    @Mock private AiQueryService aiQueryService;
    @Mock private QueryRewriteService queryRewriteService;

    private ConversationService service;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        service = new ConversationService(
                conversationRepository, aiFeedbackRepository, aiQueryService, queryRewriteService, new ObjectMapper());
        conversation = new Conversation();
    }

    private void addMessage(String role, String content) {
        ConversationMessage message = new ConversationMessage();
        message.setId(UUID.randomUUID());
        message.setConversation(conversation);
        message.setRole(role);
        message.setContent(content);
        conversation.getMessages().add(message);
    }

    private void conversationExists() {
        when(conversationRepository.findByIdAndTenantIdAndUserId(conversationId, tenantId, userId))
                .thenReturn(Optional.of(conversation));
    }

    private AiSourceChunkDTO source() {
        return new AiSourceChunkDTO(UUID.randomUUID(), "lease.pdf", "text", 2, 0.8, 4, 4, "Term");
    }

    @Test
    void sendMessage_rewritesFollowUpAndStoresTheRewrite() {
        conversationExists();
        addMessage("user", "Who is the landlord in the lease?");
        addMessage("ai", "Acme Ltd [1].");
        List<ConversationTurn> history = List.of(
                new ConversationTurn("user", "Who is the landlord in the lease?"),
                new ConversationTurn("ai", "Acme Ltd."));
        when(queryRewriteService.rewrite(history, FOLLOW_UP)).thenReturn(REWRITTEN);
        when(aiQueryService.query(authentication, tenantId, FOLLOW_UP, null, REWRITTEN, history, AiQueryMode.ASK,
                AiSearchScope.NONE))
                .thenReturn(new AiQueryResponseDTO("In May [1].", List.of(source())));

        service.sendMessage(authentication, conversationId, tenantId, userId, FOLLOW_UP);

        ConversationMessage userMessage = conversation.getMessages().get(2);
        assertEquals(FOLLOW_UP, userMessage.getContent());
        assertEquals(REWRITTEN, userMessage.getRewrittenQuery());
        assertNull(conversation.getMessages().get(3).getRewrittenQuery());
    }

    @Test
    void sendMessage_onlySendsTheLastSixMessages() {
        conversationExists();
        for (int i = 1; i <= 8; i++) {
            addMessage("user", "message " + i);
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ConversationTurn>> history = ArgumentCaptor.forClass(List.class);
        when(queryRewriteService.rewrite(history.capture(), eq(FOLLOW_UP))).thenReturn(FOLLOW_UP);
        when(aiQueryService.query(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AiQueryResponseDTO("Answer", List.of()));

        service.sendMessage(authentication, conversationId, tenantId, userId, FOLLOW_UP);

        assertEquals(6, history.getValue().size());
        assertEquals("message 3", history.getValue().get(0).content());
    }

    @Test
    void sendMessage_sourcesSurviveBeingStoredAndReadBack() {
        conversationExists();
        AiSourceChunkDTO source = source();
        when(queryRewriteService.rewrite(List.of(), FOLLOW_UP)).thenReturn(FOLLOW_UP);
        when(aiQueryService.query(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(new AiQueryResponseDTO("Answer [1].", List.of(source)));

        ConversationMessageDTO result = service.sendMessage(
                authentication, conversationId, tenantId, userId, FOLLOW_UP);

        assertEquals(List.of(source), result.getSources());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void sendMessage_streaming_storesTheStreamedAnswerWithItsScope() {
        conversationExists();
        AiSearchScope scope = new AiSearchScope(UUID.randomUUID(), null, null, null, null, null, null);
        AiStreamListener listener = org.mockito.Mockito.mock(AiStreamListener.class);
        when(queryRewriteService.rewrite(List.of(), FOLLOW_UP)).thenReturn(FOLLOW_UP);
        when(aiQueryService.stream(authentication, tenantId, FOLLOW_UP, null, FOLLOW_UP, List.of(),
                AiQueryMode.ASK, scope, listener)).thenReturn(new AiQueryResponseDTO("Streamed [1].", List.of(source())));

        ConversationMessageDTO result = service.sendMessage(
                authentication, conversationId, tenantId, userId, FOLLOW_UP, AiQueryMode.ASK, scope, listener);

        assertEquals("Streamed [1].", result.getContent());
        assertEquals("Streamed [1].", conversation.getMessages().get(1).getContent());
        verify(conversationRepository).save(conversation);
    }

    @Test
    void sendMessage_streamingCancelled_storesNothing() {
        conversationExists();
        AiStreamListener listener = org.mockito.Mockito.mock(AiStreamListener.class);
        when(queryRewriteService.rewrite(List.of(), FOLLOW_UP)).thenReturn(FOLLOW_UP);
        when(aiQueryService.stream(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenThrow(new com.docket.app.service.ai.AiStreamCancelledException(new java.io.IOException("gone")));

        org.junit.jupiter.api.Assertions.assertThrows(com.docket.app.service.ai.AiStreamCancelledException.class,
                () -> service.sendMessage(authentication, conversationId, tenantId, userId, FOLLOW_UP,
                        AiQueryMode.ASK, AiSearchScope.NONE, listener));

        verify(conversationRepository, org.mockito.Mockito.never()).save(any());
    }

    @Test
    void addFeedback_savesFeedbackForAnAiMessage() {
        conversationExists();
        addMessage("ai", "Answer");
        UUID messageId = conversation.getMessages().get(0).getId();

        service.addFeedback(conversationId, messageId, tenantId, userId, (short) -1, "wrong source", null);

        org.mockito.ArgumentCaptor<com.docket.app.entity.AiFeedback> feedback =
                org.mockito.ArgumentCaptor.forClass(com.docket.app.entity.AiFeedback.class);
        verify(aiFeedbackRepository).save(feedback.capture());
        assertEquals(tenantId, feedback.getValue().getTenantId());
        assertEquals(userId, feedback.getValue().getUserId());
        assertEquals(messageId, feedback.getValue().getMessageId());
        assertEquals(-1, feedback.getValue().getRating());
    }

    @Test
    void getConversation_readsSourcesStoredBeforeTheNewFieldsExisted() {
        conversationExists();
        addMessage("ai", "Old answer");
        UUID documentId = UUID.randomUUID();
        conversation.getMessages().get(0).setSourcesJson("""
                [{"documentId":"%s","documentName":"a.pdf","chunkText":"text","chunkIndex":1}]
                """.formatted(documentId));

        AiSourceChunkDTO source = service.getConversation(conversationId, tenantId, userId)
                .getMessages().get(0).getSources().get(0);

        assertEquals(documentId, source.getDocumentId());
        assertNull(source.getPageStart());
    }
}
