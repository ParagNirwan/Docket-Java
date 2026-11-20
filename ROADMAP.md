# Roadmap

Completed items are removed from this file once their issues are closed; see the git
history and closed GitHub issues for what has shipped.

## Open

| Priority | Issue | Area |
|----------|-------|------|
| **P1** | #113 Enforce AI query limits across backend replicas | ai-search, platform |
| **P2** | #46 AI search: answer feedback (👍/👎) and retrieval telemetry (Epic #91) | ai-search, observability |

---

## Notes

- **Epic issues** are tracking umbrellas; work happens in their child issues. Epic #91 stays open until #46 is done.
- **Security fixes applied:** refresh token grace window ↓, SSO `azp` check, HKDF webhook keys, access token out of localStorage.
