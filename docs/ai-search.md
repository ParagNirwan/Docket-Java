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

## Answering a question

1. **Rewrite.** In a conversation, the last 6 messages are used to turn a follow-up into a
   question that stands alone. The result is stored in `conversation_messages.rewritten_query`.
   If rewriting fails, the original question is used.
2. **Retrieve.** Vector and keyword search each return up to 30 chunks, merged with Reciprocal
   Rank Fusion. Both apply the same tenant, status and tray visibility filters.
   If the question contains identifiers such as `INV-2024-0042`, the keyword search looks for
   those alone. `AI_RETRIEVAL_HYBRID=false` turns keyword search off.
3. **Filter.** Chunks further than `AI_RETRIEVAL_MAX_DISTANCE` (0.45) are dropped, unless they
   contain every searched word. If nothing is left, the closest chunk within
   `AI_RETRIEVAL_FALLBACK_MAX_DISTANCE` (0.6) is kept. If there's still nothing, Gemini isn't
   called and the user is told nothing relevant was found.
4. **Answer.** Gemini returns JSON with inline `[n]` markers and a list of citations. Only
   cited sources are returned and stored. Markers are renumbered to match them, and any that
   point to a source that doesn't exist are removed.

## Tuning the threshold

0.45 is a starting point, not a measured value. Set `logging.level.com.docket.app.service.ai.AiQueryService=DEBUG`
to log the distances for each question. They're also recorded in the
`docket.ai.retrieval.distance` metric. If good answers go missing, raise the threshold. If
unrelated sources get through, lower it. No code change or re-index is needed.
