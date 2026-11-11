package com.docket.app.service.ai;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class StreamingCitationsTest {

    private final List<String> deltas = new ArrayList<>();

    private CitationResolver.ResolvedAnswer stream(int sourceCount, String... pieces) {
        StreamingCitations streaming = new StreamingCitations(sourceCount, deltas::add);
        for (String piece : pieces) {
            streaming.accept(piece);
        }
        return streaming.finish();
    }

    @Test
    void whatWasStreamedIsExactlyTheFinalAnswer() {
        CitationResolver.ResolvedAnswer resolved = stream(5,
                "The rent is due monthly [4]. ", "It rises each year [2", ", 4]. ", "Nothing else applies.");

        assertEquals("The rent is due monthly [1]. It rises each year [2][1]. Nothing else applies.",
                resolved.answer());
        assertEquals(resolved.answer(), String.join("", deltas));
        assertEquals(List.of(3, 1), resolved.cited());
    }

    @Test
    void aMarkerSplitAcrossPiecesIsNeverShownHalfWritten() {
        stream(3, "See [", "2", "] for details.");

        assertFalse(deltas.stream().anyMatch(delta -> delta.endsWith("[") || delta.endsWith("[2")));
        assertEquals("See [1] for details.", String.join("", deltas));
    }

    @Test
    void aMarkerForASourceThatDoesNotExistIsDropped() {
        assertEquals("Paid [1] in full.", stream(2, "Paid [2] in full", " [9].").answer().replace(" .", "."));
    }

    @Test
    void anAnswerThatLooksLikeJsonIsStillTreatedAsText() {
        assertEquals("[1] is the lease and [2] the policy.",
                stream(2, "[2] is the lease and [1] the policy.").answer());
    }

    @Test
    void anOpenBracketThatIsNotAMarkerIsReleasedOnceItIsTooLongToBeOne() {
        String listing = "[draft: this bracket is never closed and keeps going for a while";

        stream(2, "Note ", listing);

        assertEquals("Note " + listing, String.join("", deltas));
    }
}
