import { render, screen, fireEvent, cleanup } from "@testing-library/react";
import { describe, it, expect, vi, afterEach } from "vitest";
import { TraysOverview } from "./TraysOverview";
import type { Tray } from "../services/trayService";

const mockTrays: Tray[] = [
  {
    id: "general",
    name: "General",
    description: "General documents and records",
    isTray: true,
    isDefault: true,
    visibility: "ALL_MEMBERS",
    sortOrder: 1,
    itemCount: 4,
  },
  {
    id: "legal",
    name: "Corporate Legal",
    description: "Confidential legal filings",
    isTray: true,
    isDefault: false,
    visibility: "ADMIN_ONLY",
    sortOrder: 2,
    itemCount: 12,
  },
];

describe("TraysOverview - View Mode Switcher (List, Thumbnail, Cards)", () => {
  afterEach(() => {
    cleanup();
    if (typeof localStorage?.clear === "function") {
      localStorage.clear();
    }
  });

  it("renders with view mode switcher buttons", () => {
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={vi.fn()}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    expect(screen.getByRole("button", { name: /list view/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /thumbnail view/i })).toBeTruthy();
    expect(screen.getByRole("button", { name: /cards view/i })).toBeTruthy();
  });

  it("renders Cards view with tray metadata and click to open", () => {
    const onSelect = vi.fn();
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={onSelect}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    const cardsBtn = screen.getByRole("button", { name: /cards view/i });
    fireEvent.click(cardsBtn);

    expect(screen.getByText("General")).toBeTruthy();
    expect(screen.getByText("Corporate Legal")).toBeTruthy();
    expect(screen.getByText("Confidential legal filings")).toBeTruthy();
    expect(screen.getByText("12 items")).toBeTruthy();

    fireEvent.click(screen.getByText("Corporate Legal"));
    expect(onSelect).toHaveBeenCalledWith("legal");
  });

  it("switches to List view with table columns", () => {
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={vi.fn()}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    const listBtn = screen.getByRole("button", { name: /list view/i });
    fireEvent.click(listBtn);

    expect(screen.getByText("Tray Name")).toBeTruthy();
    expect(screen.getByText("Description")).toBeTruthy();
    expect(screen.getByText("Items")).toBeTruthy();
    expect(screen.getByText("Type")).toBeTruthy();
    expect(screen.getByText("Visibility")).toBeTruthy();
    expect(screen.getByText("Actions")).toBeTruthy();

    // Verify row data
    expect(screen.getByText("General")).toBeTruthy();
    expect(screen.getByText("Corporate Legal")).toBeTruthy();
    expect(screen.getByText("Default")).toBeTruthy();
    expect(screen.getByText("Custom")).toBeTruthy();
  });

  it("switches to Thumbnail view with thumbnail elements", () => {
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={vi.fn()}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    const thumbBtn = screen.getByRole("button", { name: /thumbnail view/i });
    fireEvent.click(thumbBtn);

    expect(screen.getByText("General")).toBeTruthy();
    expect(screen.getByText("Corporate Legal")).toBeTruthy();
  });

  it("filters trays using the search input", () => {
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={vi.fn()}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    const searchInput = screen.getByPlaceholderText(/search trays/i);
    fireEvent.change(searchInput, { target: { value: "Legal" } });

    expect(screen.getByText("Corporate Legal")).toBeTruthy();
    expect(screen.queryByText("General")).toBeNull();
  });

  it("does not render the files pagination footer on TraysOverview", () => {
    render(
      <TraysOverview
        trays={mockTrays}
        onSelectTray={vi.fn()}
        canCreateTray={true}
        canDeleteTray={true}
        onCreateTrayClick={vi.fn()}
      />
    );

    expect(screen.queryByText(/files to load:/i)).toBeNull();
    expect(screen.queryByRole("combobox", { name: /how many files to load/i })).toBeNull();
  });
});
