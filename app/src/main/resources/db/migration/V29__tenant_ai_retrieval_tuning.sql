-- Per-organization AI search tuning. NULL = use the server default (docket.ai.retrieval.*).
ALTER TABLE tenants
    ADD COLUMN ai_max_distance          DOUBLE PRECISION CHECK (ai_max_distance > 0 AND ai_max_distance <= 2),
    ADD COLUMN ai_fallback_max_distance DOUBLE PRECISION CHECK (ai_fallback_max_distance > 0 AND ai_fallback_max_distance <= 2),
    ADD COLUMN ai_rerank_min_score      DOUBLE PRECISION CHECK (ai_rerank_min_score >= 0 AND ai_rerank_min_score <= 1);
