import { describe, expect, it } from "vitest";
import { EMPTY_SCOPE_SELECTION, toAiScope } from "./aiScope";

describe("toAiScope", () => {
  it("sends nothing when nothing is picked", () => {
    expect(toAiScope(EMPTY_SCOPE_SELECTION)).toEqual({});
  });

  it("turns each pick into the filter the API expects", () => {
    const now = new Date("2026-11-12T00:00:00Z");

    const scope = toAiScope({ trayId: "tray-1", fileType: "pdf", period: "week", tagId: "tag-1" }, now);

    expect(scope).toEqual({
      trayId: "tray-1",
      contentTypes: ["application/pdf"],
      uploadedAfter: "2026-11-05T00:00:00.000Z",
      tagIds: ["tag-1"],
    });
  });

  it("ignores a file type or period it doesn't know", () => {
    expect(toAiScope({ ...EMPTY_SCOPE_SELECTION, fileType: "nope", period: "nope" })).toEqual({});
  });
});
