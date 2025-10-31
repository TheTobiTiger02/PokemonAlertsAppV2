# 🎨 Pokémon Alerts V2 - Visual Design Guide

## Design System Overview

This guide documents the complete visual design system for Pokémon Alerts V2, showcasing the modern, Pokémon-inspired aesthetic.

---

## 🎨 Color System

### Primary Palette - Pokémon Red
```
Light Mode:
  Primary:          #EE1515 (Pokémon Red)
  Primary Light:    #FF6B6B
  On Primary:       #FFFFFF

Dark Mode:
  Primary:          #FF5252 (Bright Red)
  Primary Variant:  #FF8A80
  On Primary:       #FFFFFF
```

### Secondary Palette - Pokémon Blue
```
Light Mode:
  Secondary:        #3B4CCA (Pokémon Blue)
  Secondary Light:  #5B7FFF
  On Secondary:     #FFFFFF

Dark Mode:
  Secondary:        #7B93FF (Bright Blue)
  Secondary Variant:#A5B8FF
  On Secondary:     #FFFFFF
```

### Tertiary Palette - Pokémon Yellow
```
Light Mode:
  Tertiary:         #FFDE00 (Pokémon Yellow)
  Tertiary Light:   #FFED4E
  On Tertiary:      #1A1A1A

Dark Mode:
  Tertiary:         #FFEA00 (Bright Yellow)
  Tertiary Variant: #FFF59D
  On Tertiary:      #1A1A1A
```

### Neutral Colors
```
Light Mode:
  Background:       #FDFDFD
  Surface:          #FFFFFF
  Surface Variant:  #F5F5F5
  On Background:    #1A1A1A
  On Surface:       #1A1A1A
  On Surface Var:   #444444

Dark Mode:
  Background:       #121212
  Surface:          #1E1E1E
  Surface Variant:  #2A2A2A
  On Background:    #E1E1E1
  On Surface:       #E1E1E1
  On Surface Var:   #CACACA
```

### Accent Colors
```
Green (Success):    #4CAF50 (Light) / #81C784 (Dark)
Orange (Warning):   #FF9800 (Light) / #FFB74D (Dark)
Red (Error):        #B00020 (Light) / #CF6679 (Dark)
```

---

## 📝 Typography Scale

### Display (Hero Text)
```
Display Large:    57sp / Bold / -0.25sp tracking
Display Medium:   45sp / Bold / 0sp tracking
Display Small:    36sp / Bold / 0sp tracking
```

### Headlines (Page Titles)
```
Headline Large:   32sp / Bold / 0sp tracking
Headline Medium:  28sp / Bold / 0sp tracking
Headline Small:   24sp / SemiBold / 0sp tracking
```

### Titles (Section Headers)
```
Title Large:      22sp / Bold / 0sp tracking
Title Medium:     18sp / SemiBold / 0.15sp tracking
Title Small:      14sp / SemiBold / 0.1sp tracking
```

### Body (Content Text)
```
Body Large:       16sp / Normal / 0.5sp tracking
Body Medium:      14sp / Normal / 0.25sp tracking
Body Small:       12sp / Normal / 0.4sp tracking
```

### Labels (UI Elements)
```
Label Large:      14sp / SemiBold / 0.1sp tracking
Label Medium:     12sp / SemiBold / 0.5sp tracking
Label Small:      11sp / Medium / 0.5sp tracking
```

---

## 🃏 Component Designs

### Alert Card

**Structure:**
```
┌─────────────────────────────────────┐
│                                     │
│         Hero Image (220dp)          │
│      with Gradient Overlay          │
│                                     │
│  ┌──────────────────┐               │
│  │ Pokémon Name     │ [Distance]    │
│  │ (Title Large)    │               │
│  └──────────────────┘               │
├─────────────────────────────────────┤
│  Description (Body Medium)          │
│  (Max 3 lines)                      │
│                                     │
│  [⏱ Countdown] End: 2024-10-31     │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  🗺️  Open in Maps            │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
```

**Specifications:**
- Corner Radius: 20dp
- Elevation: 4dp default, 8dp pressed
- Image Height: 220dp
- Content Padding: 16dp
- Gradient: Transparent → Black 70%
- Title Color: White (on image)
- Button: Full width, 12dp corners

---

### Distance Chip

**Design:**
```
┌──────────────────┐
│ 📍 1.2 km • 15 min │
└──────────────────┘
```

**Specifications:**
- Background: Primary (90% opacity)
- Text Color: White
- Corner Radius: 20dp (pill shape)
- Padding: 12dp horizontal, 6dp vertical
- Icon Size: 16dp
- Typography: Label Medium Bold
- Shadow: 2dp elevation

---

### Countdown Timer Chip

**Active State:**
```
┌─────────────┐
│ ⏱ 2h 34m   │
└─────────────┘
```

**Expired State:**
```
┌────────────┐
│ Expired    │
└────────────┘
```

**Specifications:**
- Background: Secondary Container (active) / Error (expired)
- Text Color: On Secondary Container / On Error
- Corner Radius: 12dp
- Padding: 12dp horizontal, 8dp vertical
- Icon Size: 16dp
- Typography: Label Large Bold
- Shadow: 1dp elevation

---

### Loading State

**Design:**
```
        ⭕ (Spinner)
        
  Loading Pokémon alerts...
```

**Specifications:**
- Spinner Color: Primary
- Stroke Width: 4dp
- Text Typography: Body Large
- Spacing: 16dp between elements
- Alignment: Center

---

### Empty State

**Design:**
```
        🖼️
        
    No Active Alerts
    
  Currently there are no active
  Pokémon alerts in your area.
  
    ┌──────────────┐
    │ 🔄 Refresh   │
    └──────────────┘
```

**Specifications:**
- Icon Size: 80dp
- Icon Tint: Primary (60% opacity)
- Title Typography: Headline Small Bold
- Message Typography: Body Medium
- Text Alignment: Center
- Container Padding: 32dp
- Spacing: 16dp between elements
- Button: Tonal style, 12dp corners

---

### Top App Bar

**Design:**
```
┌─────────────────────────────────────┐
│ Pokémon Alerts        🔄    🗺️    │
└─────────────────────────────────────┘
```

**Specifications:**
- Background: Primary color
- Title Typography: Title Large Bold
- Title Color: On Primary (White)
- Icon Color: On Primary (White)
- Icon Size: 24dp
- Height: 56dp

---

### Alert Detail Dialog

**Design:**
```
┌─────────────────────────────────────┐
│  Pokémon Name                       │
├─────────────────────────────────────┤
│                                     │
│      Alert Image (200dp)            │
│                                     │
├─────────────────────────────────────┤
│  Detailed description text goes     │
│  here with proper spacing and       │
│  typography...                      │
│                                     │
│  ┌─────────────┐                   │
│  │ Type: Raid  │                   │
│  └─────────────┘                   │
│                                     │
│  [⏱ 1h 23m] End: 2024-10-31       │
│                                     │
│  ┌───────────────────────────────┐  │
│  │  🗺️  Open in Maps            │  │
│  └───────────────────────────────┘  │
│                                     │
│              [Close]                 │
└─────────────────────────────────────┘
```

**Specifications:**
- Corner Radius: 24dp
- Container: Surface color
- Title Typography: Title Large Bold
- Content Padding: 24dp
- Spacing: 16dp between sections
- Image Border Radius: 16dp
- Type Badge: Tertiary Container, 8dp corners
- Button: Full width, 12dp corners

---

### Map Info Window

**Design:**
```
┌─────────────────────────────┐
│                             │
│  Thumbnail (120dp × full)   │
│                             │
├─────────────────────────────┤
│  Pokémon Name               │
│  (Title Medium Bold)        │
│                             │
│  Description or end time    │
│  (Body Small, 3 lines max)  │
│                             │
│  ┌──────────────────────┐   │
│  │ Tap for details      │   │
│  └──────────────────────┘   │
└─────────────────────────────┘
```

**Specifications:**
- Card Elevation: 8dp
- Corner Radius: 16dp
- Max Width: 240dp
- Padding: 12dp
- Image Border Radius: 12dp
- Thumbnail Height: 120dp
- Spacing: 8dp, 4dp for elements
- Badge: Primary Container, 8dp corners
- Badge Padding: 12dp horizontal, 6dp vertical

---

## 📐 Spacing System

**Standard Spacing Scale:**
```
4dp   - Tight spacing (icon-text)
6dp   - Very close elements
8dp   - Close elements (chip padding)
12dp  - Related items
16dp  - Section spacing
20dp  - Large gaps
24dp  - Major sections
32dp  - Page margins (empty states)
```

**Corner Radius Scale:**
```
8dp   - Small badges
12dp  - Buttons, small cards
16dp  - Medium cards, images
20dp  - Large cards, chips
24dp  - Dialogs
```

---

## 🎯 Design Tokens

### Elevation
```
Level 0: 0dp   (Flat)
Level 1: 1dp   (Slight lift)
Level 2: 2dp   (Subtle elevation)
Level 3: 4dp   (Card default)
Level 4: 8dp   (Raised cards)
```

### Animation Durations
```
Fast:    150ms  (Small transitions)
Medium:  300ms  (Standard animations)
Slow:    500ms  (Complex transitions)
```

### Icon Sizes
```
Small:   16dp (In chips/badges)
Medium:  24dp (App bar, buttons)
Large:   32dp (Empty states)
XLarge:  80dp (Hero icons)
```

---

## ✨ Special Effects

### Gradient Overlays
```
Image Cards:
  Start: Transparent
  End: Black 70% alpha
  Direction: Top to Bottom
  Height: 120dp from bottom

Status Bar Scrim:
  Start: Black 50% alpha
  End: Transparent
  Direction: Top to Bottom
  Height: Status bar + 80dp
```

### Shadows
```
Cards:       4dp elevation (8dp on press)
Chips:       1-2dp elevation
Dialogs:     8dp elevation
Info Window: 8dp elevation
```

---

## 🎨 Usage Guidelines

### When to Use Each Color

**Primary (Red):**
- App bars and headers
- Primary actions
- Distance chips
- Accent elements

**Secondary (Blue):**
- Active timers
- Secondary actions
- Status indicators

**Tertiary (Yellow):**
- Type badges
- Warning states
- Highlights

**Error (Red):**
- Expired alerts
- Error messages
- Critical warnings

---

## 📱 Component States

### Buttons
```
Default:  Tonal style, theme colors
Hover:    Slight opacity change
Pressed:  Darker shade
Disabled: 38% opacity
```

### Cards
```
Default:  4dp elevation
Hover:    6dp elevation
Pressed:  8dp elevation
Selected: Primary color border
```

### Chips
```
Active:   Full opacity, shadow
Inactive: 60% opacity
Disabled: 38% opacity
```

---

## 🎨 Accessibility

### Contrast Ratios
- Text on Surface: 4.5:1 minimum
- Large Text: 3:1 minimum
- Icons: 3:1 minimum
- UI Components: 3:1 minimum

### Touch Targets
- Minimum: 48dp × 48dp
- Comfortable: 56dp × 56dp

---

This design system ensures consistency, accessibility, and a cohesive visual experience throughout the Pokémon Alerts V2 app.
