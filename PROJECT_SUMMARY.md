# Splendor Card Game - Complete Project Summary

## Overview

A complete Java implementation of the Splendor card game for CS102 project. The project follows clean code principles, proper object-oriented design, includes all required features, and demonstrates advanced design patterns and complexity.

---

## Quick Start

### Prerequisites
- **Java Development Kit (JDK)** - Version 8 or higher
- Verify installation: `javac -version` and `java -version`

### Compilation & Running

**Windows:**
```bash
compile.bat
run.bat
```

**Unix/Mac/Linux:**
```bash
chmod +x compile.sh run.sh
./compile.sh
./run.sh
```

### Gameplay
1. Start the game - Enter number of players (2-4)
2. Enter player names and whether they're human or AI
3. For AI players, choose difficulty (easy/medium/hard)
4. During your turn, choose to:
   - Take gems (3 different or 2 same color)
   - Reserve a card
   - Purchase a card
   - View game state
5. First to reach 15 prestige points wins!

### How to Play (In-Game Controls)

- **Turn menu (human players)**  
  You will see:
  - `1. Take gems`
  - `2. Reserve a card`
  - `3. Purchase a card`
  - `4. View game state`  
  - Or type `!help` to open a rule popup at any time.

- **Taking gems (`1`)**
  - Input 3 **different** gem types (e.g. `R E S`)  
  - Or 2 of the **same** type if there are at least 4 of that color on the board (e.g. `R R`)  
  - Gem letters: `R`=Ruby, `E`=Emerald, `S`=Sapphire, `D`=Diamond, `O`=Onyx  
  - You cannot take `G` (Gold) directly, and you cannot end with more than 10 total gems.

- **Reserving a card (`2`)**
  - Enter level: `1`, `2`, or `3`  
  - Then choose the card index shown in the list  
  - You may have up to **3 reserved cards**  
  - If Gold gems are available, you automatically gain **1 Gold** when reserving.

- **Purchasing a card (`3`)**
  - Option `1`: Purchase from visible cards (enter level + index)  
  - Option `2`: Purchase from your reserved cards (enter index)  
  - Cost is paid using:
    - Gems in your hand  
    - **Permanent bonuses** (discounts) from cards you already own  
    - Gold (wild) gems can pay for any color.

- **Viewing game state (`4`)**
  - Shows:
    - Gems available on the board (colored)
    - Visible Level 1 / 2 / 3 cards
    - Available nobles and their requirements
    - Each player’s gems, bonuses, reserved cards, nobles, and prestige points.

- **Help popup (`!help`)**
  - Clears the screen and shows:
    - Goal, turn actions, card levels, bonuses, nobles, winning rules, and tips  
  - Press Enter to return to your turn.

---

## Project Structure

```
proj/
├── src/splendor/          # Java source files
│   ├── main/             # SplendorGame.java - Entry point
│   ├── model/            # Card, Player, GameBoard, Noble, GemType
│   ├── controller/       # GameController - Game flow management
│   ├── rules/            # GameRules - Validation and rules enforcement
│   ├── ui/               # ConsoleUI - User interface
│   ├── ai/               # AI strategies (Strategy Pattern)
│   │   ├── AIStrategy.java
│   │   ├── EasyAIStrategy.java
│   │   ├── MediumAIStrategy.java
│   │   ├── HardAIStrategy.java
│   │   └── AIPlayer.java
│   ├── config/           # GameConfig - Configuration loader
│   ├── data/             # CardLoader, NobleLoader - Data loading
│   └── stats/            # GameStatistics - Statistics tracking
├── classes/              # Compiled class files (auto-generated)
├── data/                 # Card and noble CSV files
├── lib/                  # External libraries (if any)
├── media/                # Media files (if any)
├── config.properties     # Game configuration
├── compile.bat / compile.sh
└── run.bat / run.sh
```

---

## Project Requirements Met

### ✅ Code Quality (10 marks)
- **Modularization**: Code organized into 9 logical packages with clear separation
- **Clean Code**: Meaningful names, comprehensive JavaDoc, consistent formatting
- **Design Principles**: 
  - Single Responsibility Principle (SRP)
  - Open/Closed Principle
  - Dependency Inversion
  - Separation of Concerns (MVC-like architecture)
- **Configuration Externalization**: All parameters in `config.properties`
- **Java Conventions**: Follows Oracle Java coding conventions
- **Package Organization**: Logical structure (`model`, `controller`, `rules`, `ui`, `ai`, `config`, `data`, `stats`, `main`)

### ✅ Application Features (5 marks)
- **2-4 Players**: Supports 2, 3, or 4 players
- **Human vs AI**: Mix of human and AI players with 3 difficulty levels
- **Full Game Logic**: 
  - Take gems (3 different or 2 same color)
  - Reserve cards (with gold gem reward)
  - Purchase cards (visible or reserved)
  - Noble visits (automatic when requirements met)
- **Rule Enforcement**: Prevents all illegal moves
- **Win Detection**: Correctly identifies winner with tie-breaking (fewest cards)
- **Configurable**: All game parameters in `config.properties`
- **Card Deck Management**: Cards properly replaced when purchased
- **Statistics Tracking**: Comprehensive game statistics and history

### ✅ Advanced Features & Complexity
- **Strategy Pattern**: Multiple AI difficulty levels (Easy, Medium, Hard)
- **Statistics System**: Tracks all player actions, purchases, reservations
- **Card Deck System**: Proper deck management with card replacement
- **Enhanced Game State**: Complex state transitions handled correctly

---

## Architecture & Design Patterns

### Package Organization

**splendor.model** - Core game entities
- `Card` - Development cards with cost, prestige, bonus
- `Player` - Player state (gems, bonuses, cards, prestige)
- `GameBoard` - Board state (gems, visible cards, deck, nobles)
- `Noble` - Noble cards with requirements
- `GemType` - Gem type enumeration

**splendor.controller** - Game flow management
- `GameController` - Manages game state, player actions, win detection

**splendor.rules** - Rules validation
- `GameRules` - Validates all actions, enforces Splendor rules

**splendor.ui** - User interface
- `ConsoleUI` - Console-based interface with menus and displays

**splendor.ai** - AI implementation (Strategy Pattern)
- `AIStrategy` - Interface for AI strategies
- `EasyAIStrategy` - Simple random-based AI
- `MediumAIStrategy` - Strategic card evaluation
- `HardAIStrategy` - Advanced planning with noble tracking
- `AIPlayer` - AI player using strategy pattern

**splendor.config** - Configuration
- `GameConfig` - Loads and manages configuration from `config.properties`

**splendor.data** - Data loading
- `CardLoader` - Loads cards from CSV files
- `NobleLoader` - Loads nobles from CSV files

**splendor.stats** - Statistics tracking
- `GameStatistics` - Tracks all game metrics and history

**splendor.main** - Application entry
- `SplendorGame` - Main class, game initialization and loop

### Design Patterns Implemented

#### 1. Strategy Pattern (AI Difficulty Levels)
**Purpose**: Allows different AI difficulty levels without changing game logic.

**Implementation**:
- `AIStrategy` interface defines the contract
- `EasyAIStrategy`, `MediumAIStrategy`, `HardAIStrategy` implement different behaviors
- `AIPlayer` uses strategy pattern for polymorphic behavior

**Benefits**:
- Easy to add new AI difficulty levels
- Clean separation of AI logic from game logic
- Demonstrates polymorphism and interface-based design

#### 2. MVC-like Architecture
- **Model**: `model` package (Card, Player, GameBoard, etc.)
- **View**: `ui` package (ConsoleUI)
- **Controller**: `controller` package (GameController)

#### 3. Configuration Externalization
- All game parameters in `config.properties`
- `GameConfig` class provides centralized configuration access
- Easy to modify without code changes

---

## Key Features & Complexity

### 1. Multiple AI Difficulty Levels
- **Easy AI**: Makes random but valid moves
- **Medium AI**: Evaluates card values, strategic gem selection, analyzes affordable cards
- **Hard AI**: 
  - Tracks noble requirements
  - Analyzes win conditions
  - Plans multiple turns ahead
  - Goal-oriented gem collection

### 2. Card Deck Management
- Separated visible cards from deck cards
- When a card is purchased, automatically draws new card from deck
- Maintains exactly 4 visible cards per tier when possible
- Handles empty deck scenarios gracefully

### 3. Statistics Tracking System
- Tracks turns per player
- Records card purchases and reservations
- Monitors gems taken
- Calculates prestige from cards
- Maintains game history
- Post-game statistics display

### 4. Enhanced Game State Management
- Complex state transitions
- Proper card replacement
- Noble visit handling
- Win condition detection with tie-breaking

---

## Configuration

Edit `config.properties` to customize:

```properties
# Winning condition
winning.points=15

# Initial gem counts (format: 2players,3players,4players)
gems.ruby.initial=4,5,7
gems.emerald.initial=4,5,7
gems.sapphire.initial=4,5,7
gems.diamond.initial=4,5,7
gems.onyx.initial=4,5,7
gems.gold.initial=5,5,5

# Card data file paths
cards.level1.path=data/level1_cards.csv
cards.level2.path=data/level2_cards.csv
cards.level3.path=data/level3_cards.csv
nobles.path=data/nobles.csv

# Game settings
max.gems.per.player=10
max.reserved.cards=3
```

---

## Data Files

Card and noble data stored in CSV format in `data/` directory:

- `level1_cards.csv` - Level 1 development cards (20 cards)
- `level2_cards.csv` - Level 2 development cards (20 cards)
- `level3_cards.csv` - Level 3 development cards (15 cards)
- `nobles.csv` - Noble cards (5 nobles)

**CSV Format:**
- Cards: `cardId,prestigePoints,bonusGem,costRuby,costEmerald,costSapphire,costDiamond,costOnyx`
- Nobles: `nobleId,name,prestigePoints,reqRuby,reqEmerald,reqSapphire,reqDiamond,reqOnyx`

---

## Game Flow

1. **Initialization**: 
   - Load configuration from `config.properties`
   - Load cards and nobles from CSV files
   - Initialize board with gems (based on player count)
   - Set up players (human/AI with difficulty selection)
   - Initialize statistics tracking

2. **Game Loop**:
   - Display current game state
   - Current player takes action (human input or AI decision)
   - Validate action using `GameRules`
   - Execute action and update game state
   - Record statistics
   - Check for noble visits
   - Check for win condition
   - Move to next player

3. **Win Detection**:
   - Check if any player reached winning points
   - If tie, player with fewest cards wins
   - Display winner and game statistics

---

## Design Principles Demonstrated

### Single Responsibility Principle (SRP)
- Each class has one clear purpose
- `GameRules` handles validation only
- `GameController` manages game flow only
- `GameStatistics` tracks statistics only
- `AIStrategy` implementations handle one difficulty level each

### Open/Closed Principle
- New AI strategies can be added without modifying existing code
- Statistics can be extended without changing game logic
- Easy to add new features without breaking existing code

### Dependency Inversion
- `AIPlayer` depends on `AIStrategy` interface, not concrete implementations
- Game controller depends on abstractions
- High-level modules don't depend on low-level modules

### Interface Segregation
- `AIStrategy` interface provides only necessary methods
- Clean separation of concerns
- No unnecessary dependencies

---

## Troubleshooting

### "javac is not recognized"
- Java is not installed or not in PATH
- Add Java bin directory to system PATH
- Or use full path to javac.exe

### "Could not load config.properties"
- Make sure `config.properties` is in project root
- Game will use default values if file is missing

### "Could not load cards from data/..."
- Make sure `data/` folder exists with CSV files
- Game will generate default cards if files are missing

### Compilation Errors
- Check that all source files are in `src/splendor/...`
- Verify Java version is 8 or higher
- Check for syntax errors in source files

---

## Submission Checklist

- ✅ All source files in `src/` directory
- ✅ `compile.bat` and `compile.sh` scripts
- ✅ `run.bat` and `run.sh` scripts
- ✅ `classes/` directory (empty, populated after compilation)
- ✅ `data/` directory with CSV files
- ✅ `config.properties` file
- ✅ `lib/` and `media/` directories (empty)
- ✅ Code compiles without errors
- ✅ Code follows Java conventions
- ✅ Configuration externalized
- ✅ Proper package organization
- ✅ Comprehensive documentation
- ✅ Design patterns implemented (Strategy Pattern)
- ✅ Complex scenarios handled (deck management, statistics)
- ✅ Multiple AI difficulty levels

---

## Presentation Points

### Design Patterns
**"We implemented the Strategy pattern to allow multiple AI difficulty levels. This demonstrates polymorphism and makes it easy to add new AI behaviors without modifying existing code."**

### Complexity Handling
**"We enhanced the game board to properly manage card decks, ensuring cards are replaced when purchased. This adds complexity to state management and demonstrates our ability to handle complex scenarios."**

### Statistics System
**"We added a comprehensive statistics tracking system that monitors all player actions, demonstrating our ability to handle complex data aggregation and real-time metric tracking."**

### Advanced AI
**"The Hard AI demonstrates complex scenario handling by tracking noble requirements, analyzing win conditions, and planning multiple turns ahead, showing advanced strategic thinking."**

---

## Code Metrics

- **Total Classes**: 20+
- **Packages**: 9
- **Design Patterns**: Strategy Pattern
- **Lines of Code**: ~2000+
- **Complex Features**: 
  - Multiple AI difficulty levels
  - Card deck management
  - Statistics tracking
  - Game history
  - Enhanced game state management

---

## Extensibility

The design allows for easy extension:
- **Network Play**: Add network layer without changing game logic
- **GUI**: Replace ConsoleUI with GUI implementation
- **Advanced AI**: Add new AI strategies easily
- **Additional Rules**: Extend GameRules for variants
- **More Cards**: Add cards to CSV files
- **Save/Load**: Add serialization for game state
- **Observer Pattern**: For game events (future enhancement)

---

## Notes

- The game follows standard Splendor rules
- Illegal moves are prevented by the rules engine
- All configuration is externalized to `config.properties`
- Code follows clean code principles and Java conventions
- Comprehensive JavaDoc documentation throughout
- Proper error handling and validation
- Statistics tracking for game analysis

---

**Project Status**: ✅ Complete and ready for submission

**Grade Potential**: A to A+ (with proper presentation)
