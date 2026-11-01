import { z } from "zod";

// Rules for new passwords. Keep in step with ValidPassword on the backend. The limit is
// in bytes because bcrypt can't take more than 72, and some characters take several.
export const passwordSchema = z
  .string()
  .min(8, "Password must be at least 8 characters")
  .refine((value) => new TextEncoder().encode(value).length <= 72, "Password is too long")
  .refine((value) => /\p{L}/u.test(value), "Password must contain a letter")
  .refine((value) => /\p{Nd}/u.test(value), "Password must contain a number");

export const resetPasswordSchema = z.object({
  password: passwordSchema,
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

export const registerSchema = z.object({
  fullName: z.string().min(2, "Full name must be at least 2 characters"),
  email: z.string().email("Invalid email address"),
  password: passwordSchema,
  confirmPassword: z.string(),
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords don't match",
  path: ["confirmPassword"],
});

