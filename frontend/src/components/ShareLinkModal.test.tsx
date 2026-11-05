import { cleanup, render, screen, waitFor } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { ShareLinkModal } from "./ShareLinkModal";
import * as shareLinkService from "../services/shareLinkService";
import type { DocumentDTO } from "../services/documentService";

vi.mock("../services/shareLinkService", () => ({
  listShareLinks: vi.fn(),
  createShareLink: vi.fn(),
  revokeShareLink: vi.fn(),
}));

const mockDoc: DocumentDTO = {
  id: "doc-1",
  originalFileName: "contract.pdf",
  fileSize: 1024,
  contentType: "application/pdf",
  uploadedBy: "user@example.com",
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
  version: 1,
};

describe("ShareLinkModal", () => {
  afterEach(cleanup);

  it("renders share link modal and fetches existing links", async () => {
    vi.mocked(shareLinkService.listShareLinks).mockResolvedValue([
      {
        id: "link-1",
        documentId: "doc-1",
        token: "tok-abc-123",
        hasPassword: false,
        expiresAt: null,
        maxDownloads: 5,
        downloadCount: 1,
        revoked: false,
        createdAt: "2026-01-01T00:00:00Z",
      },
    ]);

    render(
      <ShareLinkModal
        slug="test-org"
        document={mockDoc}
        token="token-123"
        onClose={() => {}}
      />
    );

    await waitFor(() => {
      expect(screen.getByText("External Share Links")).toBeTruthy();
      expect(screen.getByText("Generate Share Link")).toBeTruthy();
      expect(screen.getByText(/tok-abc-123/)).toBeTruthy();
    });
  });
});
