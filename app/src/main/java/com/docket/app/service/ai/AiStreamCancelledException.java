package com.docket.app.service.ai;

// The person who asked is no longer listening, so the answer is abandoned
public class AiStreamCancelledException extends RuntimeException {

    public AiStreamCancelledException(Throwable cause) {
        super("The client stopped listening", cause);
    }
}
