# Splendor Card Game (CS102 Project)

## Overview

Java console implementation of the **Splendor** board game with:
- 2–4 players (any mix of human and AI)
- Multiple AI difficulty levels (easy / medium / hard)
- Full core rules: gems, development cards (L1–L3), nobles, winning & tie-breakers
- Optional tutorial and in-game `!help` popup

### Demo Highlights
- Real-time lobby + in-game updates (ready status, turns, action feed)
- Room join by game code with player-name persistence
- AFK-to-AI host control with automatic player control recovery on return
- Web UI quality-of-life features: recommended steps, rulebook modal, visible costs/counts

## How to Run

### Prerequisites
- Java JDK 8 or higher
- Terminal that supports ANSI colors (WSL / most Linux terminals / modern Windows terminals)

### 1) Compile

#### Linux / WSL / Mac
```bash
chmod +x compile.sh run.sh run_web.sh   # first time only
./compile.sh
```

#### Windows
```bat
compile.bat
```

### 2) Run Command-Line Version

#### Linux / WSL / Mac
```bash
./run.sh
```

#### Windows
```bat
run.bat
```

### 3) Run Web Version

#### Linux / WSL / Mac
```bash
./run_web.sh
```

#### Windows
```bat
run_web.bat
```

Then open:

```text
http://localhost:8080
```

## Web Lobby / Rooms

- Enter a room name (default `Room A`) on the start screen.
- **Start Game** creates/resets that room.
- **Join Room** opens a dialog to enter the host’s game code.
- Multiple clients can join the same room code to share one game state.

## Deploy (optional static frontend + Java backend)

Because the game server is stateful Java (rooms/sessions), deploy backend and frontend separately:

1. Deploy Java backend (Render/Railway/Fly/etc.) and get a URL, for example:
   - `https://your-splendor-api.onrender.com`
2. In `web/config.js`, set:
   - `window.__SPLENDOR_API_BASE__ = "https://your-splendor-api.onrender.com";`
3. Host the `web/` folder on any static host (configure SPA-style routing to `index.html` if your host requires it).

For local dev, keep `window.__SPLENDOR_API_BASE__ = ""` and run `run_web.bat` / `run_web.sh`.

## Technical Architecture (Short)

- **Backend**: Java HTTP server in `src/splendor/web/WebServer.java` provides REST endpoints (`/api/state`, `/api/action`, room/lobby endpoints) and owns authoritative game state.
- **Core engine**: Rules and transitions are enforced server-side (`GameController`, `GameRules`), so both web and CLI use the same game logic.
- **Frontend**: Vanilla JS app in `web/app.js` renders state snapshots, sends player actions, and performs periodic live sync.
- **AI**: Strategy-pattern AI (`EasyAIStrategy`, `MediumAIStrategy`, `HardAIStrategy`) used by both console and web paths.

## Known Limitations

- UI live updates use short-interval polling (not websocket push), so updates are near-real-time rather than instant.
- Browser-based end-to-end UI tests are manual.
- Long backend idle/restart windows can require one auto-rejoin cycle before full lobby/game state appears stable.

## Notes

- If you changed Java files, compile before running.
- If port `8080` is busy, stop the existing process first.
- If the browser shows old UI, do a hard refresh (`Ctrl+F5`).

### (Optional) Automated AI Test
```bash
# only if test script exists in your copy
./test.sh
```

## How to Play (Controls)

On your turn (human player), you see:
- `1. Take gems`
- `2. Reserve a card`
- `3. Purchase a card`
- `4. View game state`
- Or type `!help` for a rule popup.

### 1) Take Gems
- Enter 3 different types, e.g. `R E S`  
  - `R`=Ruby, `E`=Emerald, `S`=Sapphire, `D`=Diamond, `O`=Onyx
- Or 2 of the same type, e.g. `R R` (only if there are **4+** of that color on the board)
- You **cannot** take Gold (`G`) directly.
- You cannot exceed **10 total gems** after taking.

### 2) Reserve a Card
- Enter level: `1`, `2`, or `3`  
- Choose the card index from the list.
- Max **3 reserved cards** per player.
- If Gold gems are available, you gain **1 Gold** when reserving.

### 3) Purchase a Card
- Choose:
  - `1` – From visible cards (enter level + index)
  - `2` – From your reserved cards (enter index)
- Cost is paid using:
  - Gems in your hand
  - **Permanent bonuses** (color discounts) from previously purchased cards
  - Gold gems can substitute any color.

### 4) View Game State
Shows:
- Board gems (colored)
- Visible Level 1 / 2 / 3 cards
- Nobles and their requirements
- Each player’s gems, bonuses, reserved cards, nobles, prestige

### `!help` – Rule Popup
- Type `!help` instead of 1–4 to open a popup that explains:
  - Goal and win condition
  - Turn actions
  - Card levels (L1/L2/L3) and what they’re for
  - Bonuses (discounts) and nobles
  - Tie-breaker rules and tips

## Game Concepts (Short)

- **Goal**: First to reach at least **15 prestige points** (configurable in `config.properties`).
- **Development Cards (L1/L2/L3)**:
  - L1: Cheap, usually 0 points, give discounts (engine building).
  - L2: Medium cost, 1–2 points.
  - L3: Expensive, many points (finishers).
- **Bonuses**: Every card you buy has a color; it gives +1 permanent bonus (discount) of that color.
- **Nobles**: Give free prestige if your **bonuses** meet their color requirements (no gems paid).
- **Tie-breaker**: If multiple players reach the goal, highest prestige wins; tie goes to the player with **fewer purchased cards**.

## How the AI Works

The AI uses the **Strategy** pattern: `AIStrategy` defines `makeMove(GameController, Player)`, and `AIPlayer` delegates to a concrete strategy (`EasyAIStrategy`, `MediumAIStrategy`, or `HardAIStrategy`). Shared helpers live on the interface:

- **`evaluateCard`** — Scores a card: prestige × 3, a small bonus if the AI already has that bonus color, minus a penalty for total chip cost.
- **`canAffordCard`** — Uses `Card.canAfford` with the AI’s gems and permanent bonuses.

After a human acts (console or web), the controller advances turns; each AI seat calls **`ai.makeMove(controller)`** once per turn, using the same purchase / reserve / take-gems paths as players.

### Easy (`EasyAIStrategy`)

1. Collect every **visible** card (levels 1–3) and **reserved** card the AI can afford.
2. If any, **pick one at random** and purchase it.
3. Otherwise try to take gems: if at least three non-gold colors are on the board and the AI has fewer than 10 gems, take **one of each of the first three** available types (order follows how gem types are collected).
4. If that fails, the turn effectively passes.

### Medium (`MediumAIStrategy`)

1. Among all affordable visible and reserved cards, sort by **`evaluateCard`** (highest first) and **buy the best**.
2. If no buy: if fewer than three reserved cards, **reserve** a visible card with **prestige > 0**, preferring higher level and higher prestige.
3. If no reserve: **take gems** toward colors that visible (unaffordable) cards still need—aggregate “missing” gems vs bonuses, then take up to three of the most-needed colors if the move is legal.
4. Fallback: take any three different available colors; otherwise pass.

### Hard (`HardAIStrategy`)

Priority order:

1. **Win now** — If an affordable card’s prestige is enough to reach the **winning score** from `config`, buy it.
2. **Noble focus** — Pick the noble with the **smallest total “missing” bonuses** (sum of shortfalls per color). If an affordable card’s **bonus color** helps that gap most, buy the best by `evaluateCard`.
3. **Best strategic buy** — Among affordable cards, rank by `evaluateCard` plus extra weight on prestige minus cost.
4. **Reserve** — Prefer visible cards with **prestige ≥ 3**, highest first.
5. **Gems** — Prefer colors needed for the target noble, then colors needed for cards; only calls `takeGems` when exactly three gems are chosen (same pattern as Medium’s strategic take); otherwise may pass.

### Caveats

- Easy’s gem line uses a **fixed** order of gem types, not a fully random triple.
- Medium and Hard often require **exactly three** gems for a take-gems action; if fewer than three types are chosen, the AI may pass even when a smaller legal take exists.
- Hard’s “closest noble” is **myopic** (missing bonuses only); it does not model opponents’ plans.

## Files of Interest

- `src/splendor/main/SplendorGame.java` – Main entry point, tutorial, game loop
- `src/splendor/ui/ConsoleUI.java` – Colored console UI, menus, `!help` popup
- `src/splendor/controller/GameController.java` – Game flow and state
- `src/splendor/rules/GameRules.java` – Rule validation (legal moves)
- `src/splendor/ai/*` – AI strategies (easy / medium / hard)
- `src/splendor/stats/GameStatistics.java` – Game stats
- `config.properties` – Winning points, gem counts, CSV paths
- `data/*.csv` – Card and noble data

For a full architectural description, see `PROJECT_SUMMARY.md`.

## Quick QA Before Submission

Use `DEMO_CHECKLIST.md` for final regression + demo walkthrough.
