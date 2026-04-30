package splendor.controller; // Orchestrates Splendor: turns, actions, endgame, stats.

import java.util.ArrayList; // Mutable list for players and card pools.
import java.util.Collections; // shuffle() for turn order and decks.
import java.util.HashMap; // Discard maps and payment fragments.
import java.util.List; // Typed lists returned/copied.
import java.util.Map; // Gem maps for take/pay/discard.

import splendor.config.GameConfig; // Properties: paths, limits, bank sizes.
import splendor.data.CardLoader; // CSV → Card objects.
import splendor.data.NobleLoader; // CSV → Noble objects.
import splendor.model.Card; // Development cards.
import splendor.model.GameBoard; // Bank, rows, decks, nobles on table.
import splendor.model.GemType; // Colors + gold enum.
import splendor.model.Noble; // Visit targets.
import splendor.model.Player; // Per-seat hand, bonuses, reserves.
import splendor.rules.GameRules; // Validation and win logic.
import splendor.stats.GameStatistics; // Turn history and per-player tallies.

/**
 * Controls the game flow and manages game state.
 */
public class GameController { // Central façade used by UI, web, and AI.
	private final GameConfig config; // Tunables and file paths (immutable reference).
	private final GameRules rules; // Validates every action against Splendor rules.
	private final GameBoard board; // Single source of table state.
	private final List<Player> players; // Seating order after shuffle; same list for whole game.
	private final List<Card> allCards; // Master list of every card loaded (audit / debug).
	private final List<Noble> allNobles; // All nobles loaded before subset placed on board.
	private int currentPlayerIndex; // Index into players for whose turn it is.
	private Player winner; // Set when endgame round completes; null while playing.
	private GameStatistics statistics; // Logs turns and aggregates per player.
	/** When someone reaches winning prestige, everyone else gets one more turn (Splendor rules). */
	private boolean endgamePending; // True during final equal-turn round.
	private int endgameTurnsRemaining; // Counts down remaining turns in that round.

	/**
	 * Constructor for GameController.
	 *
	 * @param numPlayers the number of players
	 * @param playerNames the names of the players
	 * @param playerTypes whether each player is human (true) or AI (false)
	 */
	public GameController(int numPlayers, List<String> playerNames, List<Boolean> playerTypes) { // Builds a fresh match.
		this.config = new GameConfig(); // Load config.properties or defaults.
		this.rules = new GameRules(config); // Rules may read win points / limits from config.
		this.board = new GameBoard(numPlayers); // Empty board structure sized for player count.
		this.players = new ArrayList<>(); // Will hold Player instances.
		this.allCards = new ArrayList<>(); // Populated in loadGameData.
		this.allNobles = new ArrayList<>(); // Populated in loadGameData.
		this.currentPlayerIndex = 0; // First player in shuffled list starts (index updated only by nextTurn).
		this.winner = null; // No winner at start.
		this.endgamePending = false; // Normal play until someone hits win points.
		this.endgameTurnsRemaining = 0; // Filled when endgame starts.
		
		// Initialize players
		for (int i = 0; i < numPlayers; i++) { // One Player per seat in registration order (before shuffle).
			boolean isHuman = (i < playerTypes.size()) ? playerTypes.get(i) : false; // Default AI if types list short.
			String name = (i < playerNames.size()) ? playerNames.get(i) : "Player " + (i + 1); // Fallback display name.
			players.add(new Player(name, isHuman)); // Construct seat; human flag drives UI vs AI.
		}

		// Randomize turn order
		Collections.shuffle(players); // Splendor random start player / order.
		
		// Load game data
		loadGameData(); // CSV cards/nobles → board layout.
		
		// Initialize board
		initializeBoard(); // Place gem chips on bank from config.
		
		// Initialize statistics
		statistics = new GameStatistics(players); // One PlayerStats entry per Player.
	}

	/**
	 * Loads card and noble data from files.
	 */
	private void loadGameData() { // Fills allCards, allNobles, and visible/deck/noble areas on board.
		// Load cards for each level
		for (int level = 1; level <= 3; level++) { // Three development tiers.
			String path = config.getCardDataPath(level); // CSV path for this level from properties.
			List<Card> cards = CardLoader.loadCards(path, level); // Parse rows into Card objects.
			allCards.addAll(cards); // Remember full set for this session.
			
			// Shuffle cards
			Collections.shuffle(cards); // Random order for deck + initial row.
			
			// Add first 4 cards as visible, rest to deck
			int visibleCount = Math.min(4, cards.size()); // Up to 4 face-up per level.
			for (int i = 0; i < visibleCount; i++) { // Deal face-up.
				board.addCard(level, cards.get(i)); // Slot on display row.
			}
			for (int i = visibleCount; i < cards.size(); i++) { // Remainder goes face-down.
				board.addCardToDeck(level, cards.get(i)); // Bottom/top handled inside GameBoard.
			}
		}
		
		// Load nobles
		String noblesPath = config.getNoblesDataPath(); // CSV for nobles.
		allNobles.addAll(NobleLoader.loadNobles(noblesPath)); // Full noble pool.
		
		// Shuffle and select nobles (number of players + 1)
		Collections.shuffle(allNobles); // Random which nobles appear this game.
		int noblesToSelect = Math.min(players.size() + 1, allNobles.size()); // Splendor: N+1 nobles in play.
		for (int i = 0; i < noblesToSelect; i++) { // Place subset on board.
			board.addNoble(allNobles.get(i)); // Available for visits.
		}
	}

	/**
	 * Initializes the game board with gems.
	 */
	private void initializeBoard() { // Sets bank counts from config for current player count.
		int numPlayers = players.size(); // 2–4.
		
		// Initialize regular gems
		for (GemType type : GemType.values()) { // Ruby, emerald, …
			if (type != GemType.GOLD) { // Gold handled separately below.
				String gemName = type.getName().toLowerCase(); // Config keys use lowercase names.
				int count = config.getInitialGemCount(gemName, numPlayers); // Pick column 2/3/4 players.
				board.setGemCount(type, count); // Fill bank pile for that color.
			}
		}
		
		// Initialize gold gems
		int goldCount = config.getInitialGemCount("gold", numPlayers); // Wild tokens in bank.
		board.setGemCount(GemType.GOLD, goldCount); // Usually 5 for 2 players in default config.
	}

	/**
	 * Gets the current player.
	 *
	 * @return the current player
	 */
	public Player getCurrentPlayer() { // Who must act right now.
		return players.get(currentPlayerIndex); // No copy—live reference.
	}

	/**
	 * Gets all players.
	 *
	 * @return the list of players
	 */
	public List<Player> getPlayers() { // Defensive copy so callers cannot reorder internal list.
		return new ArrayList<>(players); // New list, same Player references.
	}

	/**
	 * Gets the game board.
	 *
	 * @return the game board
	 */
	public GameBoard getBoard() { // Table state for rendering and rules.
		return board; // Shared mutable board (single instance).
	}

	/**
	 * Gets the game rules.
	 *
	 * @return the game rules
	 */
	public GameRules getRules() { // AI and web use for validation helpers (e.g. legal gem takes).
		return rules;
	}

	/**
	 * Gets the game configuration.
	 *
	 * @return the game configuration
	 */
	public GameConfig getConfig() { // Win points, paths, limits.
		return config;
	}

	/**
	 * Gets the winner of the game.
	 *
	 * @return the winner, or null if no winner yet
	 */
	public Player getWinner() { // Null until endgame resolution sets it.
		return winner;
	}

	/**
	 * Gets the game statistics.
	 *
	 * @return the game statistics
	 */
	public GameStatistics getStatistics() { // For end-screen and debugging.
		return statistics;
	}

	/**
	 * Checks if the game is over.
	 *
	 * @return true if the game is over
	 */
	public boolean isGameOver() { // Simple flag check.
		return winner != null; // Set in afterSuccessfulAction when countdown hits zero.
	}

	/**
	 * True after someone has reached winning prestige until the last round finishes.
	 */
	public boolean isEndgamePending() { // Exposed for UI if needed.
		return endgamePending && winner == null; // In final round but not yet resolved.
	}

	/**
	 * After a successful action by {@code actor}, either start the endgame countdown
	 * (first time someone reaches winning prestige) or count down and resolve the winner.
	 */
	private void afterSuccessfulAction(Player actor) { // Called after every completed turn action.
		if (winner != null) { // Already decided—idempotent guard.
			return; // Nothing more to do.
		}
		if (!endgamePending) { // Normal midgame: detect first crossing of win threshold.
			if (rules.hasWon(actor)) { // Actor’s prestige ≥ winning points.
				endgamePending = true; // Enter final equal-turn phase.
				// Splendor rule: once someone reaches winning prestige, finish the
				// current round so everyone has the same number of turns.
				// Rounds are cycles through player indices starting at index 0.
				// If the winner is at index i, only players i+1..(N-1) still have
				// turns remaining in this round.
				endgameTurnsRemaining = Math.max(0, (players.size() - 1) - currentPlayerIndex); // Turns left in round.
				if (endgameTurnsRemaining <= 0) { // Winner was last in round order—no one else acts.
					winner = rules.determineWinner(players); // Tie-break: fewer cards, etc.
					if (winner == null) { // Fallback if rules need prestige-only tie break.
						winner = rules.determineWinnerByPrestige(players);
					}
				}
			}
		} else { // Already in endgame: each successful action ticks countdown.
			endgameTurnsRemaining--; // One fewer turn allowed in the closing round.
			if (endgameTurnsRemaining <= 0) { // Everyone had their last turn.
				winner = rules.determineWinner(players); // Final adjudication.
				if (winner == null) {
					winner = rules.determineWinnerByPrestige(players);
				}
			}
		}
	}

	/**
	 * Executes a take gems action.
	 *
	 * @param gemsToTake the gems to take
	 * @return true if the action was successful
	 */
	public boolean takeGems(Map<GemType, Integer> gemsToTake) { // Simple take when hand already ≤10 after take.
		Player player = getCurrentPlayer(); // Must be current player’s turn (caller ensures).
		
		// Validate action
		GameRules.ValidationResult result = rules.validateTakeGems( // Bank supply + pattern + 10-chip limit.
			gemsToTake, board.getAvailableGems(), player.getGems());
		
		if (!result.isValid()) { // Illegal take.
			return false; // No state change.
		}
		
		// Execute action
		board.removeGems(gemsToTake); // Subtract from bank.
		player.addGems(gemsToTake); // Add to hand map.
		
		// Record statistics
		int gemCount = gemsToTake.values().stream().mapToInt(Integer::intValue).sum(); // Total chips moved.
		statistics.recordGemsTaken(player, gemCount); // Cumulative gems taken stat.
		statistics.recordTurn(player, "Took gems"); // History line.

		// Check for noble visits
		checkNobleVisits(player); // Auto-visit if bonuses qualify.

		afterSuccessfulAction(player); // Maybe start/end endgame countdown.

		return true; // Success.
	}

	/**
	 * Executes take-gems with a resilient discard flow.
	 * If discard selection is missing/partial/invalid, it auto-adjusts discard
	 * so the action can still complete and the player ends at max gems.
	 */
	public GameRules.ValidationResult takeGemsWithDiscard( // Web/console flow when take would exceed 10.
		Map<GemType, Integer> gemsToTake, // Intended take pattern (2 same or 3 diff).
		Map<GemType, Integer> gemsToDiscard // Player-chosen return; may be incomplete—auto-filled below.
	) {
		Player player = getCurrentPlayer(); // Acting player.
		Map<GemType, Integer> requestedDiscard =
			gemsToDiscard == null ? new HashMap<>() : new HashMap<>(gemsToDiscard); // Avoid mutating caller map.
		int requestedDiscardCount = requestedDiscard.values().stream().mapToInt(Integer::intValue).sum(); // How many to return.

		// Validate take pattern/supply with discard count considered for max-gem check.
		Map<GemType, Integer> validationPlayerGems = player.getGems(); // Shallow view—will simulate discard for validation.
		if (requestedDiscardCount > 0) { // Pretend discards happen before take for rule math.
			int target = Math.max(0, player.getTotalGemCount() - requestedDiscardCount); // Hand size after discards.
			int running = validationPlayerGems.values().stream().mapToInt(Integer::intValue).sum(); // Copy total—need mutable simulation.
			if (running > target) { // Need to reduce a scratch copy of gems for validation only.
				for (GemType t : GemType.values()) { // Greedy strip from colors until at target.
					if (running <= target) break;
					int have = validationPlayerGems.getOrDefault(t, 0);
					if (have <= 0) continue;
					int cut = Math.min(have, running - target); // Remove up to deficit.
					validationPlayerGems.put(t, have - cut); // Update scratch map.
					running -= cut;
				}
			}
		}
		GameRules.ValidationResult vr = rules.validateTakeGems( // Now validate take against simulated hand.
			gemsToTake, board.getAvailableGems(), validationPlayerGems
		);
		if (!vr.isValid()) { // Still illegal (bad pattern or bank).
			return vr; // Propagate failure message to UI.
		}

		// Apply taking gems first.
		board.removeGems(gemsToTake); // Bank out.
		player.addGems(gemsToTake); // Hand in—may exceed 10 momentarily.

		int maxGems = config.getMaxGemsPerPlayer(); // Usually 10.
		int needDiscard = Math.max(0, player.getTotalGemCount() - maxGems); // How many must go back.
		Map<GemType, Integer> discardToApply = new HashMap<>(); // Actual return map built below.

		// Keep only valid requested discard that the player currently has.
		if (needDiscard > 0) { // Over limit after take.
			Map<GemType, Integer> afterTake = player.getGems(); // Current hand after take.
			for (Map.Entry<GemType, Integer> e : requestedDiscard.entrySet()) { // Respect user preference where possible.
				int qty = e.getValue() == null ? 0 : e.getValue();
				if (qty <= 0) continue;
				int have = afterTake.getOrDefault(e.getKey(), 0);
				if (have <= 0) continue;
				discardToApply.put(e.getKey(), Math.min(qty, have)); // Cap by owned count.
			}
			int currentDiscard = discardToApply.values().stream().mapToInt(Integer::intValue).sum(); // How many lined up so far.

			// Prefer discarding colors just taken, then any remaining colors.
			if (currentDiscard < needDiscard) { // Top up with gems from colors in the take.
				for (GemType t : gemsToTake.keySet()) {
					if (currentDiscard >= needDiscard) break;
					int have = afterTake.getOrDefault(t, 0);
					int already = discardToApply.getOrDefault(t, 0);
					int canAdd = Math.max(0, have - already);
					if (canAdd <= 0) continue;
					int add = Math.min(canAdd, needDiscard - currentDiscard);
					discardToApply.put(t, already + add);
					currentDiscard += add;
				}
			}
			if (currentDiscard < needDiscard) { // Still short—take from any colors.
				for (GemType t : GemType.values()) {
					if (currentDiscard >= needDiscard) break;
					int have = afterTake.getOrDefault(t, 0);
					int already = discardToApply.getOrDefault(t, 0);
					int canAdd = Math.max(0, have - already);
					if (canAdd <= 0) continue;
					int add = Math.min(canAdd, needDiscard - currentDiscard);
					discardToApply.put(t, already + add);
					currentDiscard += add;
				}
			}
		}

		if (needDiscard > 0) { // Apply returns to bank.
			player.removeGems(discardToApply); // Hand down.
			board.addGems(discardToApply); // Bank up.
		}

		int gemCount = gemsToTake.values().stream().mapToInt(Integer::intValue).sum(); // Net take count for stats (not net of discard).
		statistics.recordGemsTaken(player, gemCount);
		statistics.recordTurn(player, "Took gems");
		checkNobleVisits(player);
		afterSuccessfulAction(player);
		return new GameRules.ValidationResult(true, "Gems taken."); // Success envelope for API.
	}

	/**
	 * After a successful reserve, take 1 gold from the bank if any remains, then
	 * discard down to {@link GameConfig#getMaxGemsPerPlayer()} (same auto-fill idea
	 * as {@link #takeGemsWithDiscard}). Splendor rules: you always receive gold
	 * when reserving if gold is available; over 10 gems you must return tokens.
	 */
	private void giveGoldThenApplyDiscard(Player player, Map<GemType, Integer> requestedDiscard) { // Shared by reserve methods.
		if (board.getGemCount(GemType.GOLD) > 0) { // Gold available in bank.
			Map<GemType, Integer> oneGold = new HashMap<>();
			oneGold.put(GemType.GOLD, 1); // Reserve reward: one wild chip.
			board.removeGems(oneGold); // Bank pays.
			player.addGems(oneGold); // Hand receives—may trigger discard.
		}
		int maxGems = config.getMaxGemsPerPlayer(); // Hand cap.
		int needDiscard = Math.max(0, player.getTotalGemCount() - maxGems); // Overflow amount.
		if (needDiscard <= 0) { // Still legal hand size.
			return; // Done.
		}
		Map<GemType, Integer> req = requestedDiscard == null ? new HashMap<>() : new HashMap<>(requestedDiscard); // Optional UI hints.
		Map<GemType, Integer> discardToApply = new HashMap<>(); // Built discard.
		Map<GemType, Integer> afterGold = player.getGems(); // Post-gold hand snapshot.
		for (Map.Entry<GemType, Integer> e : req.entrySet()) { // Start from requested colors.
			int qty = e.getValue() == null ? 0 : e.getValue();
			if (qty <= 0) {
				continue;
			}
			int have = afterGold.getOrDefault(e.getKey(), 0);
			if (have <= 0) {
				continue;
			}
			discardToApply.put(e.getKey(), Math.min(qty, have)); // Cannot discard more than owned.
		}
		int currentDiscard = discardToApply.values().stream().mapToInt(Integer::intValue).sum();
		if (currentDiscard < needDiscard) { // Fill remainder greedily by color order.
			for (GemType t : GemType.values()) {
				if (currentDiscard >= needDiscard) {
					break;
				}
				int have = afterGold.getOrDefault(t, 0);
				int already = discardToApply.getOrDefault(t, 0);
				int canAdd = Math.max(0, have - already);
				if (canAdd <= 0) {
					continue;
				}
				int add = Math.min(canAdd, needDiscard - currentDiscard);
				discardToApply.put(t, already + add);
				currentDiscard += add;
			}
		}
		player.removeGems(discardToApply); // Return to bank.
		board.addGems(discardToApply);
	}

	/**
	 * Executes a reserve card action.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param cardIndex the index of the card in the visible cards
	 * @return true if the action was successful
	 */
	public boolean reserveCard(int level, int cardIndex) { // Overload without discard hints.
		return reserveCard(level, cardIndex, null); // Delegate to full method.
	}

	/**
	 * Same as {@link #reserveCard(int, int)} with optional discard preference when
	 * receiving gold would exceed the gem limit (used by web client).
	 *
	 * @param gemsToDiscard map of gems to return to the bank after gold is taken; may be null
	 */
	public boolean reserveCard(int level, int cardIndex, Map<GemType, Integer> gemsToDiscard) { // Face-up reserve + refill row.
		Player player = getCurrentPlayer();
		
		GameRules.ValidationResult result = rules.validateReserveCard(player); // <3 reserved, etc.
		if (!result.isValid()) {
			return false;
		}
		
		List<Card> visibleCards = board.getVisibleCards(level); // Current row.
		if (cardIndex < 0 || cardIndex >= visibleCards.size()) { // Bad slot.
			return false;
		}
		
		Card card = visibleCards.get(cardIndex); // Card to pull.

		board.removeCard(level, card); // Remove from display (may slide/refill in board impl).
		player.reserveCard(card); // Add to player’s reserved list (max 3).

		Card newCard = board.drawCardFromDeck(level); // Replenish from deck top.
		if (newCard != null) { // Deck not empty.
			board.addCard(level, newCard); // New face-up card.
		}

		giveGoldThenApplyDiscard(player, gemsToDiscard); // +1 gold then auto-discard to 10.

		statistics.recordReservation(player); // Count reserves.
		statistics.recordTurn(player, "Reserved card");
		
		checkNobleVisits(player); // Usually no noble from reserve alone, but harmless check.
		
		afterSuccessfulAction(player);
		
		return true;
	}

	/**
	 * Executes a reserve-from-top-deck action.
	 *
	 * @param level the deck level (1, 2, or 3)
	 * @return true if the action was successful
	 */
	public boolean reserveTopCard(int level) { // Blind reserve overload.
		return reserveTopCard(level, null);
	}

	/**
	 * @param gemsToDiscard optional preference when discarding after receiving gold
	 */
	public boolean reserveTopCard(int level, Map<GemType, Integer> gemsToDiscard) { // Blind reserve from deck.
		Player player = getCurrentPlayer();

		GameRules.ValidationResult result = rules.validateReserveCard(player);
		if (!result.isValid()) {
			return false;
		}

		Card topCard = board.drawCardFromDeck(level); // Pop deck.
		if (topCard == null) { // Empty deck—illegal reserve.
			return false;
		}

		player.reserveCard(topCard); // Goes straight to hand—never was visible.

		giveGoldThenApplyDiscard(player, gemsToDiscard);

		statistics.recordReservation(player);
		statistics.recordTurn(player, "Reserved top card");

		checkNobleVisits(player);
		afterSuccessfulAction(player);
		return true;
	}

	/**
	 * Executes a purchase card action.
	 *
	 * @param card the card to purchase
	 * @return true if the action was successful
	 */
	public boolean purchaseCard(Card card) { // Buy visible or reserved by reference equality on board/hand.
		Player player = getCurrentPlayer();
		
		// Calculate payment
		Map<GemType, Integer> payment = rules.calculatePayment(card, player); // Gems + gold split from hand/bonuses.
		
		// Validate action
		GameRules.ValidationResult result = rules.validatePurchaseCard(card, player, payment); // Double-check affordability.
		if (!result.isValid()) {
			return false;
		}
		
		// Execute action
		player.purchaseCard(card, payment); // Deduct gems, add card, update bonuses/prestige.
		board.addGems(payment); // Returned chips go back to bank.
		
		// Remove card from board if it's visible
		boolean removed = false; // Track whether card came from table vs reserve.
		int cardLevel = card.getLevel(); // For row lookup and refill.
		if (board.removeCard(cardLevel, card)) { // Was on display—remove succeeded.
			removed = true;
			// Draw new card from deck to replace
			Card newCard = board.drawCardFromDeck(cardLevel);
			if (newCard != null) {
				board.addCard(cardLevel, newCard); // Refill slot.
			}
		}
		
		// If card was reserved, remove from player's reserved cards
		if (!removed) { // Not on board—must have been reserved.
			player.removeReservedCard(card); // Drop from reserved list.
		}
		
		// Record statistics
		statistics.recordPurchase(player, card); // Purchases + prestige from cards stat.
		statistics.recordTurn(player, "Purchased card");
		
		// Check for noble visits
		checkNobleVisits(player); // Bonuses may now qualify.
		
		afterSuccessfulAction(player);
		
		return true;
	}

	/**
	 * Executes a purchase action for a visible card on the board.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param cardIndex the index of the card in the visible cards list
	 * @return true if the action was successful
	 */
	public boolean purchaseVisibleCard(int level, int cardIndex) { // UI passes row + index instead of Card reference.
		List<Card> cards = board.getVisibleCards(level);
		if (cardIndex < 0 || cardIndex >= cards.size()) {
			return false;
		}
		Card card = cards.get(cardIndex);
		return purchaseCard(card); // Central purchase path.
	}

	/**
	 * Executes a purchase action for one of the current player's reserved cards.
	 *
	 * @param reservedIndex the index in the reserved list
	 * @return true if the action was successful
	 */
	public boolean purchaseReservedCard(int reservedIndex) { // Buy from hand reserves by list index.
		Player player = getCurrentPlayer();
		List<Card> reserved = player.getReservedCards();
		if (reservedIndex < 0 || reservedIndex >= reserved.size()) {
			return false;
		}
		Card card = reserved.get(reservedIndex);
		return purchaseCard(card);
	}

	/**
	 * Checks if the current player can visit any nobles and handles it.
	 *
	 * @param player the player to check
	 */
	private void checkNobleVisits(Player player) { // Auto-grant first eligible noble (no player choice).
		List<Noble> visitableNobles = rules.getVisitableNobles(board.getAvailableNobles(), player); // Meets bonus requirements.
		if (!visitableNobles.isEmpty() && player.getVisitedNoble() == null) { // At most one noble visit tracked here.
			// In a full implementation, player would choose which noble to visit
			// For now, visit the first available noble
			Noble noble = visitableNobles.get(0); // Deterministic pick.
			player.visitNoble(noble); // Add prestige / mark visited.
			board.removeNoble(noble); // Noble leaves table.
		}
	}

	/**
	 * True if the current player could still take gems (bank allows a structural take),
	 * buy something, or reserve a card from the table or a deck.
	 */
	public boolean hasLegalMovesAvailable() { // Used for pass legality and AI repair detection.
		Player p = getCurrentPlayer();
		if (anyAffordablePurchase(p)) { // Any card payable with current hand+bonuses.
			return true;
		}
		if (rules.existsLegalTakeGems(p, board)) { // Bank + hand allow a 2-same or 3-diff take.
			return true;
		}
		if (anyReserveAvailable(p)) { // Under 3 reserves and a card exists to reserve.
			return true;
		}
		return false; // Truly stuck—pass allowed.
	}

	private boolean anyAffordablePurchase(Player p) { // Helper for hasLegalMovesAvailable.
		for (int level = 1; level <= 3; level++) {
			for (Card c : board.getVisibleCards(level)) {
				if (c.canAfford(p.getGems(), p.getBonuses())) { // Card model quick check.
					return true;
				}
			}
		}
		for (Card c : p.getReservedCards()) {
			if (c.canAfford(p.getGems(), p.getBonuses())) {
				return true;
			}
		}
		return false;
	}

	private boolean anyReserveAvailable(Player p) { // Helper: reserve rules + something to take.
		if (!rules.validateReserveCard(p).isValid()) { // Full reserved hand.
			return false;
		}
		for (int level = 1; level <= 3; level++) {
			if (!board.getVisibleCards(level).isEmpty()) { // Can reserve face-up.
				return true;
			}
			if (board.getDeckSize(level) > 0) { // Can blind reserve.
				return true;
			}
		}
		return false; // No cards left to reserve anywhere.
	}

	/**
	 * Executes a pass turn action.
	 *
	 * @return true if the action was successful
	 */
	public boolean passTurn() { // Only when no legal move exists.
		if (hasLegalMovesAvailable()) { // Cannot pass if you could act.
			return false;
		}
		Player player = getCurrentPlayer();
		statistics.recordTurn(player, "Passed turn");
		afterSuccessfulAction(player); // Pass still counts for endgame countdown.
		nextTurn(); // Advance turn—note: web may double-call nextTurn if not careful.
		return true;
	}

	/**
	 * Moves to the next player's turn.
	 */
	public void nextTurn() { // Circular increment in seating order.
		currentPlayerIndex = (currentPlayerIndex + 1) % players.size(); // Wrap after last player.
	}
}
