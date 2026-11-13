# DESIGN.md — Slack

## Overview
Slack's design system balances professionalism with personality. The aubergine sidebar is instantly recognizable, while the messaging canvas stays clean and white. A four-color brand palette (blue, green, yellow, red) supports rich notification and status semantics.

## Colors

### Primary Palette
| Token | Hex | Usage |
|-------|-----|-------|
| `color-aubergine` | `#4A154B` | Sidebar, brand anchor |
| `color-blue` | `#36C5F0` | Links, informational |
| `color-green` | `#2EB67D` | Online status, success |
| `color-yellow` | `#ECB22E` | Warnings, stars |
| `color-red` | `#E01E5A` | Notifications, errors |

### Neutral Palette
| Token | Hex | Usage |
|-------|-----|-------|
| `color-canvas` | `#FFFFFF` | Message area background |
| `color-hover` | `#F8F8F8` | Message hover state |
| `color-border` | `#DDDDDD` | Dividers |
| `color-text-secondary` | `#616061` | Timestamps, metadata |
| `color-sidebar-text` | `#D1D2D3` | Muted sidebar items |

### Semantic Colors
| Token | Hex | Usage |
|-------|-----|-------|
| `color-online` | `#2EB67D` | Active/online indicator |
| `color-mention` | `#E01E5A` | @mention notification |
| `color-unread` | `#ECB22E` | Unread message indicator |

## Typography

| Role | Family | Size | Weight | Line Height |
|------|--------|------|--------|-------------|
| Display | Lato | 34px | 900 | 1.2 |
| Heading | Lato | 22px | 700 | 1.3 |
| Body | Lato | 15px | 400 | 1.5 |
| Message | Lato | 15px | 400 | 1.46 |
| Timestamp | Lato | 12px | 400 | 1.4 |

## Spacing

| Token | Value | Usage |
|-------|-------|-------|
| `space-1` | 4px | Inline gaps |
| `space-2` | 8px | Message action gaps |
| `space-3` | 12px | Avatar spacing |
| `space-4` | 16px | Message row padding |
| `space-5` | 20px | Message vertical rhythm |

## Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `radius-sm` | 4px | Reaction pills |
| `radius-md` | 8px | Buttons, modals |
| `radius-lg` | 12px | Sidebar items hover |

## Elevation

| Level | Value | Usage |
|-------|-------|-------|
| `shadow-sm` | `0 1px 2px rgba(0,0,0,0.1)` | Toolbar actions |
| `shadow-md` | `0 4px 12px rgba(0,0,0,0.15)` | Dropdowns |
| `shadow-lg` | `0 8px 30px rgba(0,0,0,0.2)` | Modals |

## Components

### Message Bubble
- Left: 36px avatar, right: username (bold) + timestamp
- Message body below, reactions row at bottom
- Hover reveals action toolbar (emoji, thread, bookmark)

### Channel Sidebar
- Aubergine background, white text
- Sections: Channels, DMs, Apps
- Unread bold white, muted channels in faded text

### Thread Panel
- Right side panel, 400px width
- Original message at top, replies below
- Reply input pinned to bottom

## Do's and Don'ts

### Do
- Use aubergine for the sidebar — it is Slack's most recognizable element
- Use the four brand colors for status semantics only
- Keep message density comfortable with 20px vertical rhythm

### Don't
- Don't use aubergine outside the sidebar context
- Don't render message timestamps larger than 12px
- Don't auto-collapse threads — let users control their view