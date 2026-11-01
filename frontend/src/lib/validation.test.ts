import { describe, expect, it } from "vitest";
import { passwordSchema, resetPasswordSchema } from "./validation";

// These cases mirror ValidPasswordTest on the backend.
describe("passwordSchema", () => {
  const valid = (password: string) => passwordSchema.safeParse(password).success;

  it("accepts a password meeting the rules", () => {
    expect(valid("correct1horse")).toBe(true);
  });

  it("rejects too short, or missing a letter or a number", () => {
    expect(valid("abc1234")).toBe(false);
    expect(valid("12345678")).toBe(false);
    expect(valid("abcdefgh")).toBe(false);
  });

  it("counts the limit in bytes", () => {
    expect(valid("a1" + "x".repeat(70))).toBe(true);
    expect(valid("a1" + "x".repeat(71))).toBe(false);
    expect(valid("a1" + "\u6f22".repeat(28))).toBe(false);
  });
});

describe("resetPasswordSchema", () => {
  it("requires both passwords to match", () => {
    expect(resetPasswordSchema.safeParse({ password: "correct1horse", confirmPassword: "other" }).success).toBe(false);
    expect(resetPasswordSchema.safeParse({ password: "correct1horse", confirmPassword: "correct1horse" }).success).toBe(true);
  });
});
