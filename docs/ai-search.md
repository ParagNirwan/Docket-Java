# AI search

How a document becomes searchable, how a question gets answered, and what to set up in
Google Cloud. All of this only runs under the `gcp` profile.

## Before deploying OCR

OCR needs three things that code can't do for you. Do them before the deploy: the re-index
job starts about a minute after startup, and each document only gets three attempts.

1. Enable the API:

   ```
   gcloud services enable vision.googleapis.com --project docket-java
   ```

2. Give the backend's service account access. Cloud Vision reads and writes the bucket as
   that account, and the app lists and deletes the OCR output, so it needs
   `roles/storage.objectAdmin` on the uploads bucket. It also needs
   `roles/serviceusage.serviceUsageConsumer` on the project to call the API.

3. Add a lifecycle rule to the bucket that deletes objects under `ocr-output/` after one
   day. The app deletes its OCR output after each document, but output is left behind if a
   pod dies mid-way, and it contains the document's text:

   ```
   gcloud storage buckets update gs://docket-java-uploads --lifecycle-file=lifecycle.json
   ```

   ```json
   {"rule": [{"action": {"type": "Delete"},
              "condition": {"age": 1, "matchesPrefix": ["ocr-output/"]}}]}
   ```

   This replaces any lifecycle rules the bucket already has, so merge them first if there are any.

## Where documents are sent

`vision.googleapis.com`, the default OCR endpoint, doesn't guarantee where data is
processed. To keep OCR in the EU, set `AI_OCR_ENDPOINT=eu-vision.googleapis.com:443`.
The bucket must then be in the EU too.

## Indexing

1. **Extract.** Tika reads the text and keeps PDF page numbers and DOCX headings. Images go
   to Cloud Vision. A PDF averaging under 50 visible characters a page counts as a scan, and
   goes to Cloud Vision too, up to `AI_OCR_MAX_PAGES` (200) pages. A longer scan isn't sent:
   it keeps whatever text it has, or is marked `FAILED` if it has none. Text past 2,000,000
   characters isn't indexed, and a warning is logged.
2. **Chunk.** Text is split into 2,000 character chunks that overlap by 200, and each chunk
   records its pages, nearest heading and position in the text.
3. **Embed.** Each chunk is embedded as `RETRIEVAL_DOCUMENT`, with the file name, folder path
   and heading in front of its text. That context only goes into the embedding. `chunk_text`
   stays unchanged.

Only the first page of a multi-page TIFF is read.

### Optional: AI-written chunk context

`AI_LLM_CHUNK_CONTEXT_ENABLED=true` adds a sentence or two from Gemini to each chunk's
embedding, for documents under 40,000 characters. It's off by default, because it costs one
model call per chunk with the whole document in each.

## Re-indexing

Every document records the `embedding_version` it was indexed with. A scheduled job processes
any active document below `docket.ai.embedding.version`, 10 per pod per minute by default.

**Raise the version whenever a change to extraction, chunking or embedding should reach
existing documents.** Version 1 covers everything in this document.

- Uploads don't wait for the job, which has its own thread.
- A document stays searchable on its old chunks until the new ones are ready, and keeps
  them if re-indexing fails.
- Each document gets 3 attempts, an hour apart. After that the job leaves it alone. Fix the
  cause, then either `POST /api/tenants/{slug}/documents/{id}/reindex` or run:

  ```sql
  UPDATE documents SET reindex_attempts = 0, reindex_claimed_at = NULL
  WHERE embedding_version < 1 AND status = 'ACTIVE';
  ```

- `AI_REINDEX_ENABLED=false` stops the job.

To see how far it has got:

```sql
SELECT embedding_version, indexing_status, reindex_attempts >= 3 AS gave_up, count(*)
FROM documents WHERE status = 'ACTIVE' GROUP BY 1, 2, 3;
```

Renaming or moving a document or folder doesn't update embeddings that mention the old name.

## Document summaries

After a document's chunks are saved, Gemini writes a 3 to 5 sentence summary and up to 7 key
points, stored in `documents.ai_summary` and `ai_key_points`. A document over about 48,000
characters is condensed in groups of 10 chunks first, and only its first 200 chunks are read.
The summary is embedded too (`ai_summary_embedding`), which is what lets a workspace question
match a document as a whole.

- The document is searchable before its summary exists, and stays indexed if summarizing fails.
- Every re-index writes a new summary, including a new version and the re-index job.
  Documents indexed before this feature get one the next time they are re-indexed. Raising
  `docket.ai.embedding.version` does that for all of them, at the cost of re-embedding everything.
- An owner or admin can switch summaries off under Settings, AI
  (`PUT /api/tenants/{slug}/ai-settings`). That also deletes the summaries already written.
- `AI_SUMMARIES_ENABLED=false` switches them off for every organization.
- `GET /api/tenants/{slug}/documents/{id}/summary` returns it to anyone who can read the document.

## Answering a question

1. **Rewrite.** In a conversation, the last 6 messages are used to turn a follow-up into a
   question that stands alone. The result is stored in `conversation_messages.rewritten_query`.
   If rewriting fails, the original question is used.
2. **Retrieve.** Vector and keyword search each return up to 30 chunks, merged with Reciprocal
   Rank Fusion. Both apply the same tenant, status and tray visibility filters, and the scope
   if there is one.
   If the question contains identifiers such as `INV-2024-0042`, the keyword search looks for
   those alone. `AI_RETRIEVAL_HYBRID=false` turns keyword search off.
3. **Re-rank.** When switched on, the best 30 candidates go to the Vertex AI Ranking API,
   which scores each one against the question. See below.
4. **Filter.** Re-ranked chunks scoring under `AI_RERANK_MIN_SCORE` (0.3) are dropped. Without
   re-ranking, chunks further than `AI_RETRIEVAL_MAX_DISTANCE` (0.45) are dropped. Either way a
   chunk containing every searched word stays. If nothing is left, the closest chunk within
   `AI_RETRIEVAL_FALLBACK_MAX_DISTANCE` (0.6) is kept. If there's still nothing, Gemini isn't
   called and the user is told nothing relevant was found.
5. **Add summaries.** A workspace search also looks for documents whose summary is within
   `AI_RETRIEVAL_MAX_DISTANCE` of the question, and adds up to 3 that no chunk already covers.
   They are cited like chunks, under the heading "AI summary".
6. **Answer.** Gemini returns JSON with inline `[n]` markers and a list of citations. Only
   cited sources are returned and stored. Markers are renumbered to match them, and any that
   point to a source that doesn't exist are removed.

## Scope

A workspace question can be narrowed with a `scope` object on `/ai/query`, on a new
conversation and on each message:

```json
{"question": "When does it end?",
 "scope": {"trayId": "...", "folderId": "...", "documentIds": ["..."],
           "contentTypes": ["application/pdf"], "tagIds": ["..."],
           "uploadedAfter": "2026-01-01T00:00:00Z", "uploadedBefore": "2026-07-01T00:00:00Z"}}
```

Every part is optional and they combine with AND. `folderId` includes all folders below it.
A scope only ever adds conditions: tenant, status and tray visibility still apply, and a tray
or folder the person can't open answers 404, the same as one that doesn't exist. The scope
belongs to the message, not the conversation, and is ignored when `documentId` is set.

The search page offers tray, file type, upload date and tag. `folderId` and `documentIds` are
API only for now.

## Streaming

Each answering endpoint has a twin that sends the answer as server-sent events while it is
written: `/ai/query/stream`, `/ai/conversations/stream` and
`/ai/conversations/{id}/messages/stream`. The request body is the same.

| Event | Data |
| --- | --- |
| `sources` | Once, after retrieval: everything the model is about to read |
| `delta` | `{"text": "..."}`, a piece of the answer |
| `done` | The stored result, the same JSON the non-streaming endpoint returns |
| `error` | `{"message": "..."}`, if something fails after the stream has started |

A failure before the first event (not found, rate limit) is an ordinary HTTP error. The text
in the `delta` events adds up to the answer in `done`: citation markers are renumbered as
they pass. Gemini's JSON mode can't be shown half-written, so a streamed answer is requested
as plain text. For a long summary only the final step streams.

Closing the connection stops the answer. A stopped answer isn't stored, so a conversation
never holds half a reply, and a question that was stopped isn't in the history either.

Three things can hold a stream back, and all are configured in this repository:
nginx buffering (`X-Accel-Buffering: no` on the response), the nginx read timeout
(`frontend/nginx.conf`) and the load balancer's 30 second backend timeout
(`k8s/base/backend-policy.yaml` raises it to 300).

## Re-ranking

Off by default, because it needs an API that isn't enabled yet:

```
gcloud services enable discoveryengine.googleapis.com --project docket-java
```

The backend's service account needs `roles/discoveryengine.viewer`. Then set
`AI_RERANK_ENABLED=true`.

- It has 500 ms. If the call fails or takes longer, the answer goes ahead in retrieval order
  and `docket.ai.rerank.fallback` counts it. Enabling it before the API is ready costs every
  question up to half a second and nothing else.
- `AI_RERANK_LOCATION` is `global` by default. Set `eu` or `us` to keep requests in that region.
- 0.3 for `AI_RERANK_MIN_SCORE` is a starting point, like the distance threshold. The
  `docket.ai.rerank.score` metric shows the scores real questions get.
- Metrics: `docket.ai.rerank.latency`, and `docket.ai.rerank.records`, the number of records
  ranked, which is what the API bills for.

## Tuning the threshold

0.45 is a starting point, not a measured value. Set `logging.level.com.docket.app.service.ai.AiQueryService=DEBUG`
to log the distances for each question. They're also recorded in the
`docket.ai.retrieval.distance` metric. If good answers go missing, raise the threshold. If
unrelated sources get through, lower it. No code change or re-index is needed.
