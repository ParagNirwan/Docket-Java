import { afterEach, describe, expect, it } from "vitest";
import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { AiMessage } from "./AiMessage";
import type { AiSourceChunk } from "../services/aiService";

function source(overrides: Partial<AiSourceChunk>): AiSourceChunk {
  return {
    documentId: "doc-1",
    documentName: "lease.pdf",
    chunkText: "Pay within 30 days.",
    chunkIndex: 7,
    ...overrides,
  };
}

function renderSources(sources: AiSourceChunk[]) {
  render(<AiMessage role="ai" content="Pay within 30 days [1]." sources={sources} />);
  fireEvent.click(screen.getByRole("button", { name: /source/ }));
}

describe("AiMessage sources", () => {
  afterEach(cleanup);

  it("numbers sources to match the markers in the answer", () => {
    renderSources([source({ chunkIndex: 7 }), source({ chunkIndex: 2 })]);

    expect(screen.getByText("1")).toBeTruthy();
    expect(screen.getByText("2")).toBeTruthy();
    expect(screen.queryByText("8")).toBeNull();
  });

  it("shows the page and heading", () => {
    renderSources([source({ pageStart: 4, pageEnd: 4, heading: "Payment Terms" })]);

    expect(screen.getByText("p. 4 · Payment Terms")).toBeTruthy();
  });

  it("shows a page range when a source spans pages", () => {
    renderSources([source({ pageStart: 4, pageEnd: 6 })]);

    expect(screen.getByText("pp. 4–6")).toBeTruthy();
  });

  it("shows only the heading for documents without pages", () => {
    renderSources([source({ pageStart: null, pageEnd: null, heading: "Scope" })]);

    expect(screen.getByText("Scope")).toBeTruthy();
  });

  it("still shows sources saved before locations existed", () => {
    renderSources([source({})]);

    expect(screen.getByText("lease.pdf")).toBeTruthy();
  });
});
