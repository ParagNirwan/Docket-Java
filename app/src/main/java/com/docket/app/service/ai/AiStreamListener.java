package com.docket.app.service.ai;

import com.docket.app.dto.AiSourceChunkDTO;

import java.util.List;

// Receives an answer while it is being produced. Either method may throw
// AiStreamCancelledException to stop the work, which is how a closed browser tab ends it.
public interface AiStreamListener {

    // Called once, when retrieval is done, with everything the model is about to read
    void onSources(List<AiSourceChunkDTO> sources);

    void onDelta(String text);
}
