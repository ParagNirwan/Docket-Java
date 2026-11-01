import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { MemoryRouter } from "react-router-dom";
import { OrganizationSettings } from "./OrganizationSettings";

vi.mock("./MembersPanel", () => ({ MembersPanel: () => <div>members panel</div> }));
vi.mock("./RetentionPanel", () => ({ RetentionPanel: () => <div>retention panel</div> }));
vi.mock("./LegalHoldsPanel", () => ({ LegalHoldsPanel: () => <div>holds panel</div> }));
vi.mock("./DangerZone", () => ({ DangerZone: () => <div>danger panel</div> }));

function show(permissions: string[], initialTab?: string) {
  render(
    <MemoryRouter>
      <OrganizationSettings
        slug="acme"
        token="t"
        tenantName="Acme"
        permissions={new Set(permissions)}
        initialTab={initialTab}
        onOwnershipTransferred={() => {}}
      />
    </MemoryRouter>,
  );
}

describe("OrganizationSettings", () => {
  afterEach(cleanup);

  it("hides retention and legal holds without retention.read", () => {
    show(["members.read"]);

    expect(screen.queryByRole("tab", { name: "Retention" })).toBeNull();
    expect(screen.queryByRole("tab", { name: "Legal holds" })).toBeNull();
  });

  it("shows them with retention.read", () => {
    show(["retention.read"]);

    expect(screen.getByRole("tab", { name: "Retention" })).toBeTruthy();
    expect(screen.getByRole("tab", { name: "Legal holds" })).toBeTruthy();
  });

  it("opens on the tab a link asks for", () => {
    show(["members.read", "retention.read"], "legal-holds");

    expect(screen.getByText("holds panel")).toBeTruthy();
  });

  it("ignores a linked tab the person can't see", () => {
    show(["members.read"], "retention");

    expect(screen.getByText("members panel")).toBeTruthy();
    expect(screen.queryByText("retention panel")).toBeNull();
  });
});
