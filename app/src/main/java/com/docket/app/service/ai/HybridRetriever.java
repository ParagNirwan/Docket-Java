package com.docket.app.service.ai;

import com.docket.app.repository.DocumentChunkRepository;
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

    private final DocumentChunkRepository documentChunkRepository;

    @Value("${docket.ai.retrieval.hybrid}")
    private boolean hybrid = true;

    // Best match first. documentId limits the search to one document when it isn't null.
    public List<RetrievedChunk> search(UUID tenantId, UUID documentId, String query, float[] queryEmbedding,
                                       boolean includeRestricted) {
        String embedding = toVectorString(queryEmbedding);

        List<Object[]> vectorRows = documentId == null
                ? documentChunkRepository.findNearestByTenant(tenantId, embedding, includeRestricted, CANDIDATES_PER_SEARCH)
                : documentChunkRepository.findNearestByTenantAndDocument(
                        tenantId, documentId, embedding, includeRestricted, CANDIDATES_PER_SEARCH);
        List<RetrievedChunk> vectorResults = vectorRows.stream().map(HybridRetriever::toChunk).toList();

        if (!hybrid) {
            return vectorResults;
        }

        String keywordQuery = keywordQuery(query);
        List<Object[]> keywordRows = documentId == null
                ? documentChunkRepository.findByKeywordsInTenant(
                        tenantId, keywordQuery, embedding, includeRestricted, CANDIDATES_PER_SEARCH)
                : documentChunkRepository.findByKeywordsInDocument(
                        tenantId, documentId, keywordQuery, embedding, includeRestricted, CANDIDATES_PER_SEARCH);

        return fuse(vectorResults, keywordRows.stream().map(HybridRetriever::toChunk).toList());
    }

    public List<RetrievedChunk> firstChunks(UUID tenantId, UUID documentId, boolean includeRestricted, int limit) {
        return documentChunkRepository.findByDocumentOrdered(tenantId, documentId, includeRestricted, limit)
                .stream()
                .map(HybridRetriever::toChunk)
                .toList();
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

    private static RetrievedChunk toChunk(Object[] row) {
        return new RetrievedChunk(
                (UUID) row[0],
                (String) row[1],
                ((Number) row[2]).intValue(),
                (String) row[3],
                row[4] == null ? null : ((Number) row[4]).intValue(),
                row[5] == null ? null : ((Number) row[5]).intValue(),
                (String) row[6],
                row[7] == null ? null : ((Number) row[7]).doubleValue(),
                Boolean.TRUE.equals(row[8]));
    }

    private static String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        return sb.append("]").toString();
    }
}
