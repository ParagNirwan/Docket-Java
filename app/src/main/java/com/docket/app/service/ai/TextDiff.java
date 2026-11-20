package com.docket.app.service.ai;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// Line-level diff (longest common subsequence). Pure and bounded: documents longer than
// MAX_LINES are compared on their first MAX_LINES lines and flagged as truncated, so a huge
// file can't take the server's memory (the LCS table is lines × lines).
public final class TextDiff {
    public static final int MAX_LINES = 2_000;

    public enum Kind { EQUAL, ADDED, REMOVED }

    public record Op(Kind kind, String text) {
    }

    public record Result(List<Op> ops, int added, int removed, boolean truncated) {
    }

    private TextDiff() {
    }

    public static Result diff(String left, String right) {
        List<String> a = lines(left);
        List<String> b = lines(right);
        boolean truncated = a.size() > MAX_LINES || b.size() > MAX_LINES;
        a = a.subList(0, Math.min(a.size(), MAX_LINES));
        b = b.subList(0, Math.min(b.size(), MAX_LINES));

        // Trim the shared start and end first: real edits are usually small.
        int prefix = 0;
        while (prefix < a.size() && prefix < b.size() && a.get(prefix).equals(b.get(prefix))) prefix++;
        int suffix = 0;
        while (suffix < a.size() - prefix && suffix < b.size() - prefix
                && a.get(a.size() - 1 - suffix).equals(b.get(b.size() - 1 - suffix))) suffix++;

        List<String> midA = a.subList(prefix, a.size() - suffix);
        List<String> midB = b.subList(prefix, b.size() - suffix);

        List<Op> ops = new ArrayList<>();
        for (int i = 0; i < prefix; i++) add(ops, Kind.EQUAL, a.get(i));
        lcs(midA, midB, ops);
        for (int i = a.size() - suffix; i < a.size(); i++) add(ops, Kind.EQUAL, a.get(i));

        int added = 0;
        int removed = 0;
        for (Op op : ops) {
            int count = op.text().split("\n", -1).length;
            if (op.kind() == Kind.ADDED) added += count;
            if (op.kind() == Kind.REMOVED) removed += count;
        }
        return new Result(ops, added, removed, truncated);
    }

    private static void lcs(List<String> a, List<String> b, List<Op> ops) {
        int n = a.size();
        int m = b.size();
        int[][] table = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                table[i][j] = a.get(i).equals(b.get(j)) ? table[i + 1][j + 1] + 1 : Math.max(table[i + 1][j], table[i][j + 1]);
            }
        }
        int i = 0;
        int j = 0;
        while (i < n && j < m) {
            if (a.get(i).equals(b.get(j))) {
                add(ops, Kind.EQUAL, a.get(i));
                i++;
                j++;
            } else if (table[i + 1][j] >= table[i][j + 1]) {
                add(ops, Kind.REMOVED, a.get(i++));
            } else {
                add(ops, Kind.ADDED, b.get(j++));
            }
        }
        while (i < n) add(ops, Kind.REMOVED, a.get(i++));
        while (j < m) add(ops, Kind.ADDED, b.get(j++));
    }

    // Consecutive lines of the same kind become one op, to keep responses small.
    private static void add(List<Op> ops, Kind kind, String line) {
        if (!ops.isEmpty() && ops.get(ops.size() - 1).kind() == kind) {
            Op last = ops.remove(ops.size() - 1);
            ops.add(new Op(kind, last.text() + "\n" + line));
        } else {
            ops.add(new Op(kind, line));
        }
    }

    private static List<String> lines(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(text.replace("\r\n", "\n").split("\n"))
                .map(String::stripTrailing)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
