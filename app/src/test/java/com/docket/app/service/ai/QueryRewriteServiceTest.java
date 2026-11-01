package com.docket.app.service.ai;

import com.google.cloud.vertexai.api.GenerationConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QueryRewriteServiceTest {

    private static final List<ConversationTurn> HISTORY = List.of(
            new ConversationTurn("user", "Who is the landlord in the lease?"),
            new ConversationTurn("ai", "Acme Ltd."));

    @Mock
    private GeminiClient geminiClient;

    @InjectMocks
    private QueryRewriteService queryRewriteService;

    @Test
    void rewrite_noHistory_doesNotCallTheModel() {
        assertEquals("Who is the landlord?", queryRewriteService.rewrite(List.of(), "Who is the landlord?"));
        verifyNoInteractions(geminiClient);
    }

    @Test
    void rewrite_sendsHistoryAndReturnsStandaloneQuestion() throws IOException {
        ArgumentCaptor<String> prompt = ArgumentCaptor.forClass(String.class);
        when(geminiClient.generate(any(), prompt.capture(), any(GenerationConfig.class)))
                .thenReturn("  When does the lease expire?\n");

        assertEquals("When does the lease expire?", queryRewriteService.rewrite(HISTORY, "When does it expire?"));

        assertTrue(prompt.getValue().contains("Who is the landlord in the lease?"));
        assertTrue(prompt.getValue().contains("When does it expire?"));
    }

    @Test
    void rewrite_modelFails_usesOriginalQuestion() throws IOException {
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenThrow(new IOException("down"));

        assertEquals("When does it expire?", queryRewriteService.rewrite(HISTORY, "When does it expire?"));
    }

    @Test
    void rewrite_unusableResult_usesOriginalQuestion() throws IOException {
        when(geminiClient.generate(any(), any(), any(GenerationConfig.class))).thenReturn("  ");

        assertEquals("When does it expire?", queryRewriteService.rewrite(HISTORY, "When does it expire?"));
    }
}
