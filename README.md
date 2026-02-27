# Splendor Card Game (CS102 Project)

## Overview

Java console implementation of the **Splendor** board game with:
- 2–4 players (any mix of human and AI)
- Multiple AI difficulty levels (easy / medium / hard)
- Full core rules: gems, development cards (L1–L3), nobles, winning & tie-breakers
- Optional tutorial and in-game `!help` popup

## How to Run

### Prerequisites
- Java JDK 8 or higher
- Terminal that supports ANSI colors (WSL / most Linux terminals / modern Windows terminals)

### Linux / WSL / Mac
```bash
chmod +x compile.sh run.sh test.sh   # first time only
./compile.sh                         # compile main game
./run.sh                             # run main game
```

### Windows
```bat
compile.bat
run.bat
```

### Automated AI Test (no user input)
```bash
./test.sh        # runs a full AI vs AI game
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
