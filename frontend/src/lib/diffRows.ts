// Diff rows for the side-by-side compare view.
export type Op = { kind: "EQUAL" | "ADDED" | "REMOVED"; text: string };
export type Comparison = {
  left: { version: number | null; fileName: string };
  right: { version: number | null; fileName: string };
  ops: Op[];
  added: number;
  removed: number;
  truncated: boolean;
  summary: string | null;
  changes: { kind: "added" | "removed" | "changed"; description: string }[];
};

export type Row = { left: string | null; right: string | null; kind: "same" | "removed" | "added" | "changed" };

// Pairs removed/added runs line by line so a change shows side by side.
export function toRows(ops: Op[]): Row[] {
  const rows: Row[] = [];
  for (let i = 0; i < ops.length; i++) {
    const op = ops[i];
    const lines = op.text.split("\n");
    if (op.kind === "EQUAL") {
      lines.forEach((line) => rows.push({ left: line, right: line, kind: "same" }));
    } else if (op.kind === "REMOVED" && ops[i + 1]?.kind === "ADDED") {
      const added = ops[i + 1].text.split("\n");
      for (let j = 0; j < Math.max(lines.length, added.length); j++) {
        rows.push({ left: lines[j] ?? null, right: added[j] ?? null, kind: "changed" });
      }
      i++;
    } else if (op.kind === "REMOVED") {
      lines.forEach((line) => rows.push({ left: line, right: null, kind: "removed" }));
    } else {
      lines.forEach((line) => rows.push({ left: null, right: line, kind: "added" }));
    }
  }
  return rows;
}

