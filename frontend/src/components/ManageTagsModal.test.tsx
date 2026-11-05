import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ManageTagsModal } from "./ManageTagsModal";
import * as tagService from "../services/tagService";
import type { DocumentDTO } from "../services/documentService";

vi.mock("../services/tagService", () => ({
  getTags: vi.fn(),
  createTag: vi.fn(),
  deleteTag: vi.fn(),
  setDocumentTags: vi.fn(),
  updateDocumentMetadata: vi.fn(),
}));

const mockDoc: DocumentDTO = {
  id: "doc-1",
  originalFileName: "contract.pdf",
  fileSize: 1024,
  contentType: "application/pdf",
  uploadedBy: "user@example.com",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  tags: [{ id: "tag-1", name: "Finance", color: "#3b82f6" }],
};

describe("ManageTagsModal", () => {
  afterEach(cleanup);

  it("renders tags list and allows selecting tags", async () => {
    vi.mocked(tagService.getTags).mockResolvedValue([
      { id: "tag-1", name: "Finance", color: "#3b82f6" },
      { id: "tag-2", name: "Legal", color: "#ef4444" },
    ]);

    render(
      <ManageTagsModal
        slug="test-org"
        document={mockDoc}
        token="token-123"
        onClose={() => {}}
        onTagsUpdated={() => {}}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("Document Tags")).toBeTruthy();
      expect(screen.getByText("Finance")).toBeTruthy();
      expect(screen.getByText("Legal")).toBeTruthy();
    });
  });
});
