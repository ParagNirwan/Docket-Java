package com.docket.app.controller;

import com.docket.app.dto.AiSourceChunkDTO;
import com.docket.app.service.ai.AiStreamCancelledException;
import com.docket.app.service.ai.AiStreamListener;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

// Writes an AI answer to the response as server-sent events: "sources" once retrieval is
// done, "delta" for each piece of text, then "done" with the stored result or "error".
//
// It writes on the request's own thread instead of using SseEmitter. The work is one
// blocking call either way, and this keeps it inside the caller's transaction and security
// context with no thread pool to size.
final class SseStream implements AiStreamListener {

    private static final Logger log = LoggerFactory.getLogger(SseStream.class);

    private final HttpServletResponse response;
    private final ObjectMapper objectMapper;
    private boolean started;

    private SseStream(HttpServletResponse response, ObjectMapper objectMapper) {
        this.response = response;
        this.objectMapper = objectMapper;
    }

    // Runs the work and reports how it ended. Nothing is written until the first event, so
    // a failure before that (not found, no access) is still an ordinary HTTP error.
    static void run(HttpServletResponse response, ObjectMapper objectMapper, Function<AiStreamListener, Object> work) {
        SseStream stream = new SseStream(response, objectMapper);
        try {
            Object result = work.apply(stream);
            stream.send("done", result);
        } catch (AiStreamCancelledException e) {
            log.debug("AI answer abandoned: the client disconnected");
        } catch (RuntimeException e) {
            if (!stream.started) {
                throw e;
            }
            String message = e instanceof ResponseStatusException statusException && statusException.getReason() != null
                    ? statusException.getReason()
                    : "Something went wrong while answering. Please try again.";
            if (!(e instanceof ResponseStatusException)) {
                log.error("AI answer failed mid-stream", e);
            }
            try {
                stream.send("error", Map.of("message", message));
            } catch (AiStreamCancelledException ignored) {
                // Nobody left to tell
            }
        }
    }

    @Override
    public void onSources(List<AiSourceChunkDTO> sources) {
        send("sources", sources);
    }

    @Override
    public void onDelta(String text) {
        send("delta", Map.of("text", text));
    }

    private void send(String event, Object data) {
        try {
            if (!started) {
                started = true;
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/event-stream");
                response.setCharacterEncoding("UTF-8");
                response.setHeader("Cache-Control", "no-cache, no-transform");
                // Tells nginx to pass each event on at once instead of collecting them
                response.setHeader("X-Accel-Buffering", "no");
            }
            // JSON never contains a raw line break, so the data always fits on one line
            String frame = "event: " + event + "\ndata: " + objectMapper.writeValueAsString(data) + "\n\n";
            OutputStream out = response.getOutputStream();
            out.write(frame.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } catch (IOException e) {
            throw new AiStreamCancelledException(e);
        }
    }
}
