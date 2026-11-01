package com.docket.app.service.ai;

// Documents and questions are embedded differently, which improves how well they match.
public enum EmbeddingTaskType {
    RETRIEVAL_DOCUMENT,
    RETRIEVAL_QUERY
}
