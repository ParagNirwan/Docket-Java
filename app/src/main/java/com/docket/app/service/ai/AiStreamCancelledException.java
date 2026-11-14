package com.docket.app.service.ai;

import java.io.Serial;

// The person who asked is no longer listening, so the answer is abandoned
public class AiStreamCancelledException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    public AiStreamCancelledException(Throwable cause) {
        super("The client stopped listening", cause);
    }
}
