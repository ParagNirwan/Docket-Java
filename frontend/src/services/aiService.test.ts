import { describe, expect, it, vi } from "vitest";
import { readAiStream } from "./aiService";

// Sends each string as its own network chunk
function streamOf(...chunks: string[]): Response {
  const encoder = new TextEncoder();
  const body = new ReadableStream<Uint8Array>({
    start(controller) {
      chunks.forEach((chunk) => controller.enqueue(encoder.encode(chunk)));
      controller.close();
    },
  });
  return new Response(body, { status: 200, headers: { "Content-Type": "text/event-stream" } });
}

const frame = (event: string, data: unknown) => `event: ${event}\ndata: ${JSON.stringify(data)}\n\n`;

describe("readAiStream", () => {
  it("passes on sources and text, and resolves with the stored answer", async () => {
    const onSources = vi.fn();
    const onDelta = vi.fn();
    const done = { answer: "Pay in 30 days [1].", sources: [] };

    const result = await readAiStream(
      streamOf(
        frame("sources", [{ documentId: "d1" }]),
        frame("delta", { text: "Pay in " }),
        frame("delta", { text: "30 days [1]." }),
        frame("done", done),
      ),
      { onSources, onDelta },
    );

    expect(onSources).toHaveBeenCalledWith([{ documentId: "d1" }]);
    expect(onDelta.mock.calls.map(([text]) => text).join("")).toBe("Pay in 30 days [1].");
    expect(result).toEqual(done);
  });

  it("handles an event that arrives split across network chunks", async () => {
    const onDelta = vi.fn();
    const whole = frame("delta", { text: "line one\nline two" }) + frame("done", { answer: "x", sources: [] });

    await readAiStream(streamOf(whole.slice(0, 20), whole.slice(20, 47), whole.slice(47)), { onDelta });

    expect(onDelta).toHaveBeenCalledExactlyOnceWith("line one\nline two");
  });

  it("rejects with the server's message on an error event", async () => {
    await expect(
      readAiStream(streamOf(frame("delta", { text: "Pay" }), frame("error", { message: "AI is unavailable" })), {
        onDelta: vi.fn(),
      }),
    ).rejects.toThrow("AI is unavailable");
  });

  it("rejects when the stream ends without finishing", async () => {
    await expect(
      readAiStream(streamOf(frame("delta", { text: "Pay" })), { onDelta: vi.fn() }),
    ).rejects.toThrow(/cut off/);
  });

  it("reports an ordinary HTTP error that happened before streaming began", async () => {
    const response = new Response(JSON.stringify({ message: "Folder not found" }), { status: 404 });

    await expect(readAiStream(response, { onDelta: vi.fn() })).rejects.toThrow("Folder not found");
  });
});
