import { describe, it, expect, vi, beforeEach } from "vitest";
import { notify, toast } from "./notifications";

vi.mock("sonner", () => ({
  toast: {
    success: vi.fn(),
    error: vi.fn(),
    warning: vi.fn(),
    info: vi.fn(),
    dismiss: vi.fn(),
  },
}));

describe("Global Notifications System (notify)", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("triggers success notification with message and description", () => {
    notify.success("Operation successful", "Data was updated");
    expect(toast.success).toHaveBeenCalledWith("Operation successful", {
      description: "Data was updated",
      duration: undefined,
      action: undefined,
    });
  });

  it("triggers error notification with default duration", () => {
    notify.error("Network error", "Unable to reach server");
    expect(toast.error).toHaveBeenCalledWith("Network error", {
      description: "Unable to reach server",
      duration: 5000,
      action: undefined,
    });
  });

  it("triggers warning notification", () => {
    notify.warning("Storage warning", "You have used 90% of your quota");
    expect(toast.warning).toHaveBeenCalledWith("Storage warning", {
      description: "You have used 90% of your quota",
      duration: 4500,
      action: undefined,
    });
  });

  it("triggers info notification", () => {
    notify.info("Downloading file", "Preparing document");
    expect(toast.info).toHaveBeenCalledWith("Downloading file", {
      description: "Preparing document",
      duration: undefined,
      action: undefined,
    });
  });

  it("triggers uploadComplete notification with folder name", () => {
    notify.uploadComplete("Report.pdf", "Legal Contracts");
    expect(toast.success).toHaveBeenCalledWith("Upload complete", {
      description: '"Report.pdf" saved to Legal Contracts',
      duration: 4000,
    });
  });

  it("triggers uploadFailed notification with error details", () => {
    notify.uploadFailed("LargeFile.zip", "File exceeds 50 MB limit");
    expect(toast.error).toHaveBeenCalledWith("Upload failed", {
      description: '"LargeFile.zip": File exceeds 50 MB limit',
      duration: 6000,
    });
  });

  it("dismisses notifications", () => {
    notify.dismiss("toast-123");
    expect(toast.dismiss).toHaveBeenCalledWith("toast-123");
  });
});

