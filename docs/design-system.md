# Docket design system

Docket uses a calm, native-feeling interface inspired by the clarity and restraint
of Apple's Human Interface Guidelines. Keep Docket's own identity and workflows;
do not reproduce Apple's branding, logos, product screens, or proprietary assets.

## Principles

- **Content first:** prioritize the user's documents and tasks; use whitespace and
  hierarchy instead of decoration, gradients, or heavy shadows.
- **Clear hierarchy:** use concise titles, readable body copy, restrained
  secondary text, and consistent alignment.
- **Familiar interaction:** make controls predictable, visibly interactive, and
  keyboard accessible. Use blue for primary actions and links, not decoration.
- **Purposeful feedback:** communicate loading, success, warnings, and errors with
  semantic colors, labels, and accessible status information.
- **Adaptive by default:** layouts must work on narrow screens, support zoom, and
  respect reduced-motion preferences.

## Visual language

- **Typography:** use the platform system font stack (`-apple-system`,
  `BlinkMacSystemFont`, `Segoe UI`, sans-serif). SF Pro is supplied by Apple
  platforms; never download, bundle, or imitate its letterforms on other systems.
- **Primary blue:** `#0071e3` for actions and selected states; use darker blue on
  hover and lighter tints for quiet selected surfaces. Preserve white text on
  primary buttons.
- **Neutrals:** use charcoal text (`#1d1d1f`), secondary gray (`#6e6e73`), subtle
  cool-gray separators (`#d2d2d7`), and a soft off-white canvas (`#f5f5f7`).
  Prefer white or near-black surfaces with clear contrast.
- **Semantic colors:** retain distinct, accessible green, amber, and red meanings
  for success, caution, and destructive/error states.
- **Shape and depth:** prefer restrained corner radii, thin separators, and
  minimal shadows. Reserve prominent surfaces for related content and dialogs.
- **Motion:** use short, purposeful transitions and disable nonessential motion
  when `prefers-reduced-motion` is enabled.

## Implementation

Shared tokens and base styles live in `frontend/src/index.css`; the document
theme color and font loading belong in `frontend/index.html`. Reuse these tokens
and Tailwind theme colors instead of introducing page-specific palettes. Keep
focus indicators visible, maintain WCAG AA text contrast, and ensure status is
never communicated by color alone.
