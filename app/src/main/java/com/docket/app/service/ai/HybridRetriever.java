package com.docket.app.service.ai;

import com.docket.app.config.DocketAiProperties;
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

    private static final Pattern TOKEN = Pattern.compile("[\\p{L}\\p{N}]+(?:[-_/.][\\p{L}\\p{N}]+)*");

    private final ChunkSearchRepository chunkSearchRepository;
    private final DocketAiProperties aiProperties;

    public HybridRetriever(ChunkSearchRepository chunkSearchRepository) {
        this(chunkSearchRepository, new DocketAiProperties());
    }

    @Value("${docket.ai.retrieval.hybrid}")
    private boolean hybrid = true;

    // Best match first. documentId limits the search to one document when it isn't null,
    // and the scope is then ignored.
    public List<RetrievedChunk> search(UUID tenantId, UUID documentId, String query, float[] queryEmbedding,
                                       boolean includeRestricted, AiSearchScope scope) {
        String embedding = toVectorString(queryEmbedding);
        AiSearchScope effectiveScope = documentId == null ? scope : AiSearchScope.ofDocument(documentId);
        int candidatesLimit = aiProperties.getHybrid().getCandidatesPerSearch();

        List<RetrievedChunk> vectorResults = chunkSearchRepository.findNearest(
                tenantId, embedding, includeRestricted, effectiveScope, candidatesLimit);

        if (!hybrid) {
            return vectorResults;
        }

        String kw = keywordQuery(query, aiProperties.getHybrid().getMinIdentifierLength(),
                aiProperties.getHybrid().getMinDigitsOnlyIdentifierLength());
        List<RetrievedChunk> keywordResults = chunkSearchRepository.findByKeywords(
                tenantId, kw, embedding, includeRestricted, effectiveScope, candidatesLimit);

        return fuse(vectorResults, keywordResults, aiProperties.getHybrid().getRrfK());
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
        return keywordQuery(query, DocketAiProperties.Hybrid.DEFAULT_MIN_IDENTIFIER_LENGTH,
                DocketAiProperties.Hybrid.DEFAULT_MIN_DIGITS_ONLY_IDENTIFIER_LENGTH);
    }

    static String keywordQuery(String query, int minIdentifierLength, int minDigitsOnlyIdentifierLength) {
        List<String> identifiers = new ArrayList<>();
        Matcher matcher = TOKEN.matcher(query);
        while (matcher.find()) {
            if (isIdentifier(matcher.group(), minIdentifierLength, minDigitsOnlyIdentifierLength)) {
                identifiers.add(matcher.group());
            }
        }
        return identifiers.isEmpty() ? query : String.join(" ", identifiers);
    }

    private static boolean isIdentifier(String token, int minIdentifierLength, int minDigitsOnlyIdentifierLength) {
        boolean hasDigit = token.chars().anyMatch(Character::isDigit);
        boolean digitsOnly = token.chars().allMatch(Character::isDigit);
        return hasDigit && token.length() >= (digitsOnly ? minDigitsOnlyIdentifierLength : minIdentifierLength);
    }

    static List<RetrievedChunk> fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults) {
        return fuse(vectorResults, keywordResults, DocketAiProperties.Hybrid.DEFAULT_RRF_K);
    }

    static List<RetrievedChunk> fuse(List<RetrievedChunk> vectorResults, List<RetrievedChunk> keywordResults, int rrfK) {
        Map<String, RetrievedChunk> chunks = new LinkedHashMap<>();
        Map<String, Double> scores = new LinkedHashMap<>();

        // Keyword results go first: they carry exact_match, and win when two chunks tie.
        addRanked(keywordResults, chunks, scores, rrfK);
        addRanked(vectorResults, chunks, scores, rrfK);

        Comparator<RetrievedChunk> byScore = Comparator.comparingDouble(chunk -> -scores.get(key(chunk)));
        return chunks.values().stream()
                .sorted(byScore.thenComparing(RetrievedChunk::exactMatch, Comparator.reverseOrder()))
                .toList();
    }

    private static void addRanked(List<RetrievedChunk> results, Map<String, RetrievedChunk> chunks,
                                  Map<String, Double> scores, int rrfK) {
        for (int i = 0; i < results.size(); i++) {
            RetrievedChunk chunk = results.get(i);
            chunks.putIfAbsent(key(chunk), chunk);
            scores.merge(key(chunk), 1.0 / (rrfK + i + 1), Double::sum);
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
