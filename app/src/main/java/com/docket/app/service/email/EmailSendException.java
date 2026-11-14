package com.docket.app.service.email;

import lombok.Getter;

import java.io.Serial;

// permanent means retrying can't help, for example a rejected address.
@Getter
public class EmailSendException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;
    private final boolean permanent;

    public EmailSendException(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }
}
