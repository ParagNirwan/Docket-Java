package com.docket.app.service.ai;

import com.docket.app.repository.ChunkSearchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Vector search understands meaning but is weak on exact strings such as invoice numbers,
// and keyword search is the opposite. This runs both and merges the two rankings with
// Reciprocal Rank Fusion, which only compares ranks, because the scores aren't comparable.
@Service
@RequiredArgsConstructor
@Profile("gcp")
public class HybridRetriever {

    private static final int CANDIDATES_PER_SEARCH = 30;
    private static final int RRF_K = 60;
    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_/.][\\p{L}\\p{N}]+)*");
    private static final int MIN_IDENTIFIER_LENGTH = 3;
    private static final int MIN_DIGITS_ONLY_IDENTIFIER_LENGTH = 5; // so that a year isn't an identifier

    private final ChunkSearchRepository chunkSearchRepository;

    @Value("${docket.ai.retrieval.hybrid}")
    private boolean hybrid = true;

    // Best match first. documentId limits the search to one document when it isn't null,
    // and the scope is then ignored.
    public List<RetrievedChunk> search(UUID tenantId, UUID documentId, String query, float[] queryEmbedding,
                                       boolean includeRestricted, AiSearchScope scope) {
        String embedding = toVectorString(queryEmbedding);
        AiSearchScope effectiveScope = documentId == null ? scope : AiSearchScope.ofDocument(documentId);

        List<RetrievedChunk> vectorResults = chunkSearchRepository.findNearest(
                tenantId, embedding, includeRestricted, effectiveScope, CANDIDATES_PER_SEARCH);

        if (!hybrid) {
            return vectorResults;
        }

        List<RetrievedChunk> keywordResults = chunkSearchRepository.findByKeywords(
                tenantId, keywordQuery(query), embedding, includeRestricted, effectiveScope, CANDIDATES_PER_SEARCH);

        return fuse(vectorResults, keywordResults);
    }

    public List<RetrievedChunk> firstChunks(UUID tenantId, UUID documentId, boolean includeRestricted, int limit) {
        return chunkSearchRepository.findInDocumentOrder(tenantId, documentId, includeRestricted, limit);
    }

    // Documents whose AI summary matches the question, closest first
    public List<RetrievedChunk> summaries(UUID tenantId, float[] queryEmbedding, boolean includeRestricted,
                                          AiSearchScope scope, double maxDistance, int limit) {
        return chunkSearchRepository.findNearestSummaries(
                tenantId, toVectorString(queryEmbedding), includeRestricted, scope, maxDistance, limit);
    }

    // In "what is the total on INV-2024-0042" only the invoice number tells chunks apart.
    // Keyword ranking doesn't know a rare word from a common one, so identifiers are searched alone.
    static String keywordQuery(String query) {
        List<String> identifiers = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(query);
        while (matcher.find()) {
            if (isIdentifier(matcher.group())) {
                identifiers.add(matcher.group());
            }
        }
        return identifiers.isEmpty() ? query : String.join(" ", identifiers);
    }

    private static boolean isIdentifier(String token) {
        boolean hasDigit = token.chars().anyMatch(Character::isDigit);
        boolean digitsOnly = token.chars().allMatch(Character::isDigit);
        return hasDigit && token.length() >= (digitsOnly ? MIN_DIGITS_ONLY_IDENTIFIER_LENGTH : MIN_IDENTIFIER_LENGTH);
    }

    static List<RetrievedChunk> fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults) {
        Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();

        // Keyword results go first: they carry exact_match, and win when two chunks tie.
        addRanked(keywordResults, chunks, scores);
        addRanked(vectorResults, chunks, scores);

        Comparator<RetrievedChunk> byScore = Comparator.comparingDouble(chunk -> -scores.get(key(chunk)));
        return chunks.values().stream()
                .sorted(byScore.thenComparing(RetrievedChunk::exactMatch, Comparator.reverseOrder()))
                .toList();
    }

    private static void addRanked(List<RetrievedChunk> results, Map<String, RetrievedChunk> chunks,
                                  Map<String, Double> scores) {
        for (int i = 0; i < results.size(); i++) {
            RetrievedChunk chunk = results.get(i);
            chunks.putIfAbsent(key(chunk), chunk);
            scores.merge(key(chunk), 1.0 / (RRF_K + i + 1), Double::sum);
        }
    }

    private static String key(RetrievedChunk chunk) {
        return chunk.documentId() + ":" + chunk.chunkIndex();
    }

    static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.append("]").toString();
    }
}
