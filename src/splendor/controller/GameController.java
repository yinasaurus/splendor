package splendor.controller;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.config.GameConfig;
import splendor.data.CardLoader;
import splendor.data.NobleLoader;
import splendor.model.Card;
import splendor.model.GameBoard;
import splendor.model.GemType;
import splendor.model.Noble;
import splendor.model.Player;
import splendor.rules.GameRules;
import splendor.stats.GameStatistics;

/**
 * Controls the game flow and manages game state.
 */
public class GameController {
	private final GameConfig config;
	private final GameRules rules;
	private final GameBoard board;
	private final List<Player> players;
	private final List<Card> allCards;
	private final List<Noble> allNobles;
	private int currentPlayerIndex;
	private Player winner;
	private GameStatistics statistics;
	/** When someone reaches winning prestige, everyone else gets one more turn (Splendor rules). */
	private boolean endgamePending;
	private int endgameTurnsRemaining;

	/**
	 * Constructor for GameController.
	 *
	 * @param numPlayers the number of players
	 * @param playerNames the names of the players
	 * @param playerTypes whether each player is human (true) or AI (false)
	 */
	public GameController(int numPlayers, List<String> playerNames, List<Boolean> playerTypes) {
		this.config = new GameConfig();
		this.rules = new GameRules(config);
		this.board = new GameBoard(numPlayers);
		this.players = new ArrayList<>();
		this.allCards = new ArrayList<>();
		this.allNobles = new ArrayList<>();
		this.currentPlayerIndex = 0;
		this.winner = null;
		this.endgamePending = false;
		this.endgameTurnsRemaining = 0;
		
		// Initialize players
		for (int i = 0; i < numPlayers; i++) {
			boolean isHuman = (i < playerTypes.size()) ? playerTypes.get(i) : false;
			String name = (i < playerNames.size()) ? playerNames.get(i) : "Player " + (i + 1);
			players.add(new Player(name, isHuman));
		}
		
		// Randomize turn order
		Collections.shuffle(players);
		
		// Load game data
		loadGameData();
		
		// Initialize board
		initializeBoard();
		
		// Initialize statistics
		statistics = new GameStatistics(players);
	}

	/**
	 * Loads card and noble data from files.
	 */
	private void loadGameData() {
		// Load cards for each level
		for (int level = 1; level <= 3; level++) {
			String path = config.getCardDataPath(level);
			List<Card> cards = CardLoader.loadCards(path, level);
			allCards.addAll(cards);
			
			// Shuffle cards
			Collections.shuffle(cards);
			
			// Add first 4 cards as visible, rest to deck
			int visibleCount = Math.min(4, cards.size());
			for (int i = 0; i < visibleCount; i++) {
				board.addCard(level, cards.get(i));
			}
			for (int i = visibleCount; i < cards.size(); i++) {
				board.addCardToDeck(level, cards.get(i));
			}
		}
		
		// Load nobles
		String noblesPath = config.getNoblesDataPath();
		allNobles.addAll(NobleLoader.loadNobles(noblesPath));
		
		// Shuffle and select nobles (number of players + 1)
		Collections.shuffle(allNobles);
		int noblesToSelect = Math.min(players.size() + 1, allNobles.size());
		for (int i = 0; i < noblesToSelect; i++) {
			board.addNoble(allNobles.get(i));
		}
	}

	/**
	 * Initializes the game board with gems.
	 */
	private void initializeBoard() {
		int numPlayers = players.size();
		
		// Initialize regular gems
		for (GemType type : GemType.values()) {
			if (type != GemType.GOLD) {
				String gemName = type.getName().toLowerCase();
				int count = config.getInitialGemCount(gemName, numPlayers);
				board.setGemCount(type, count);
			}
		}
		
		// Initialize gold gems
		int goldCount = config.getInitialGemCount("gold", numPlayers);
		board.setGemCount(GemType.GOLD, goldCount);
	}

	/**
	 * Gets the current player.
	 *
	 * @return the current player
	 */
	public Player getCurrentPlayer() {
		return players.get(currentPlayerIndex);
	}

	/**
	 * Gets all players.
	 *
	 * @return the list of players
	 */
	public List<Player> getPlayers() {
		return new ArrayList<>(players);
	}

	/**
	 * Gets the game board.
	 *
	 * @return the game board
	 */
	public GameBoard getBoard() {
		return board;
	}

	/**
	 * Gets the game rules.
	 *
	 * @return the game rules
	 */
	public GameRules getRules() {
		return rules;
	}

	/**
	 * Gets the game configuration.
	 *
	 * @return the game configuration
	 */
	public GameConfig getConfig() {
		return config;
	}

	/**
	 * Gets the winner of the game.
	 *
	 * @return the winner, or null if no winner yet
	 */
	public Player getWinner() {
		return winner;
	}

	/**
	 * Gets the game statistics.
	 *
	 * @return the game statistics
	 */
	public GameStatistics getStatistics() {
		return statistics;
	}

	/**
	 * Checks if the game is over.
	 *
	 * @return true if the game is over
	 */
	public boolean isGameOver() {
		return winner != null;
	}

	/**
	 * True after someone has reached winning prestige until the last round finishes.
	 */
	public boolean isEndgamePending() {
		return endgamePending && winner == null;
	}

	/**
	 * After a successful action by {@code actor}, either start the endgame countdown
	 * (first time someone reaches winning prestige) or count down and resolve the winner.
	 */
	private void afterSuccessfulAction(Player actor) {
		if (winner != null) {
			return;
		}
		if (!endgamePending) {
			if (rules.hasWon(actor)) {
				endgamePending = true;
				// Splendor rule: once someone reaches winning prestige, finish the
				// current round so everyone has the same number of turns.
				// Rounds are cycles through player indices starting at index 0.
				// If the winner is at index i, only players i+1..(N-1) still have
				// turns remaining in this round.
				endgameTurnsRemaining = Math.max(0, (players.size() - 1) - currentPlayerIndex);
				if (endgameTurnsRemaining <= 0) {
					winner = rules.determineWinner(players);
					if (winner == null) {
						winner = rules.determineWinnerByPrestige(players);
					}
				}
			}
		} else {
			endgameTurnsRemaining--;
			if (endgameTurnsRemaining <= 0) {
				winner = rules.determineWinner(players);
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
	public boolean takeGems(Map<GemType, Integer> gemsToTake) {
		Player player = getCurrentPlayer();
		
		// Validate action
		GameRules.ValidationResult result = rules.validateTakeGems(
			gemsToTake, board.getAvailableGems(), player.getGems());
		
		if (!result.isValid()) {
			return false;
		}
		
		// Execute action
		board.removeGems(gemsToTake);
		player.addGems(gemsToTake);
		
		// Record statistics
		int gemCount = gemsToTake.values().stream().mapToInt(Integer::intValue).sum();
		statistics.recordGemsTaken(player, gemCount);
		statistics.recordTurn(player, "Took gems");
		
		// Check for noble visits
		checkNobleVisits(player);
		
		afterSuccessfulAction(player);
		
		return true;
	}

	/**
	 * Executes take-gems with a resilient discard flow.
	 * If discard selection is missing/partial/invalid, it auto-adjusts discard
	 * so the action can still complete and the player ends at max gems.
	 */
	public GameRules.ValidationResult takeGemsWithDiscard(
		Map<GemType, Integer> gemsToTake,
		Map<GemType, Integer> gemsToDiscard
	) {
		Player player = getCurrentPlayer();
		Map<GemType, Integer> requestedDiscard =
			gemsToDiscard == null ? new HashMap<>() : new HashMap<>(gemsToDiscard);
		int requestedDiscardCount = requestedDiscard.values().stream().mapToInt(Integer::intValue).sum();

		// Validate take pattern/supply with discard count considered for max-gem check.
		Map<GemType, Integer> validationPlayerGems = player.getGems();
		if (requestedDiscardCount > 0) {
			int target = Math.max(0, player.getTotalGemCount() - requestedDiscardCount);
			int running = validationPlayerGems.values().stream().mapToInt(Integer::intValue).sum();
			if (running > target) {
				for (GemType t : GemType.values()) {
					if (running <= target) break;
					int have = validationPlayerGems.getOrDefault(t, 0);
					if (have <= 0) continue;
					int cut = Math.min(have, running - target);
					validationPlayerGems.put(t, have - cut);
					running -= cut;
				}
			}
		}
		GameRules.ValidationResult vr = rules.validateTakeGems(
			gemsToTake, board.getAvailableGems(), validationPlayerGems
		);
		if (!vr.isValid()) {
			return vr;
		}

		// Apply taking gems first.
		board.removeGems(gemsToTake);
		player.addGems(gemsToTake);

		int maxGems = config.getMaxGemsPerPlayer();
		int needDiscard = Math.max(0, player.getTotalGemCount() - maxGems);
		Map<GemType, Integer> discardToApply = new HashMap<>();

		// Keep only valid requested discard that the player currently has.
		if (needDiscard > 0) {
			Map<GemType, Integer> afterTake = player.getGems();
			for (Map.Entry<GemType, Integer> e : requestedDiscard.entrySet()) {
				int qty = e.getValue() == null ? 0 : e.getValue();
				if (qty <= 0) continue;
				int have = afterTake.getOrDefault(e.getKey(), 0);
				if (have <= 0) continue;
				discardToApply.put(e.getKey(), Math.min(qty, have));
			}
			int currentDiscard = discardToApply.values().stream().mapToInt(Integer::intValue).sum();

			// Prefer discarding colors just taken, then any remaining colors.
			if (currentDiscard < needDiscard) {
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
			if (currentDiscard < needDiscard) {
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

		if (needDiscard > 0) {
			player.removeGems(discardToApply);
			board.addGems(discardToApply);
		}

		int gemCount = gemsToTake.values().stream().mapToInt(Integer::intValue).sum();
		statistics.recordGemsTaken(player, gemCount);
		statistics.recordTurn(player, "Took gems");
		checkNobleVisits(player);
		afterSuccessfulAction(player);
		return new GameRules.ValidationResult(true, "Gems taken.");
	}

	/**
	 * After a successful reserve, take 1 gold from the bank if any remains, then
	 * discard down to {@link GameConfig#getMaxGemsPerPlayer()} (same auto-fill idea
	 * as {@link #takeGemsWithDiscard}). Splendor rules: you always receive gold
	 * when reserving if gold is available; over 10 gems you must return tokens.
	 */
	private void giveGoldThenApplyDiscard(Player player, Map<GemType, Integer> requestedDiscard) {
		if (board.getGemCount(GemType.GOLD) > 0) {
			Map<GemType, Integer> oneGold = new HashMap<>();
			oneGold.put(GemType.GOLD, 1);
			board.removeGems(oneGold);
			player.addGems(oneGold);
		}
		int maxGems = config.getMaxGemsPerPlayer();
		int needDiscard = Math.max(0, player.getTotalGemCount() - maxGems);
		if (needDiscard <= 0) {
			return;
		}
		Map<GemType, Integer> req = requestedDiscard == null ? new HashMap<>() : new HashMap<>(requestedDiscard);
		Map<GemType, Integer> discardToApply = new HashMap<>();
		Map<GemType, Integer> afterGold = player.getGems();
		for (Map.Entry<GemType, Integer> e : req.entrySet()) {
			int qty = e.getValue() == null ? 0 : e.getValue();
			if (qty <= 0) {
				continue;
			}
			int have = afterGold.getOrDefault(e.getKey(), 0);
			if (have <= 0) {
				continue;
			}
			discardToApply.put(e.getKey(), Math.min(qty, have));
		}
		int currentDiscard = discardToApply.values().stream().mapToInt(Integer::intValue).sum();
		if (currentDiscard < needDiscard) {
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
		player.removeGems(discardToApply);
		board.addGems(discardToApply);
	}

	/**
	 * Executes a reserve card action.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param cardIndex the index of the card in the visible cards
	 * @return true if the action was successful
	 */
	public boolean reserveCard(int level, int cardIndex) {
		return reserveCard(level, cardIndex, null);
	}

	/**
	 * Same as {@link #reserveCard(int, int)} with optional discard preference when
	 * receiving gold would exceed the gem limit (used by web client).
	 *
	 * @param gemsToDiscard map of gems to return to the bank after gold is taken; may be null
	 */
	public boolean reserveCard(int level, int cardIndex, Map<GemType, Integer> gemsToDiscard) {
		Player player = getCurrentPlayer();

		GameRules.ValidationResult result = rules.validateReserveCard(player);
		if (!result.isValid()) {
			return false;
		}

		List<Card> visibleCards = board.getVisibleCards(level);
		if (cardIndex < 0 || cardIndex >= visibleCards.size()) {
			return false;
		}

		Card card = visibleCards.get(cardIndex);

		board.removeCard(level, card);
		player.reserveCard(card);

		Card newCard = board.drawCardFromDeck(level);
		if (newCard != null) {
			board.addCard(level, newCard);
		}

		giveGoldThenApplyDiscard(player, gemsToDiscard);

		statistics.recordReservation(player);
		statistics.recordTurn(player, "Reserved card");

		checkNobleVisits(player);

		afterSuccessfulAction(player);

		return true;
	}

	/**
	 * Executes a reserve-from-top-deck action.
	 *
	 * @param level the deck level (1, 2, or 3)
	 * @return true if the action was successful
	 */
	public boolean reserveTopCard(int level) {
		return reserveTopCard(level, null);
	}

	/**
	 * @param gemsToDiscard optional preference when discarding after receiving gold
	 */
	public boolean reserveTopCard(int level, Map<GemType, Integer> gemsToDiscard) {
		Player player = getCurrentPlayer();

		GameRules.ValidationResult result = rules.validateReserveCard(player);
		if (!result.isValid()) {
			return false;
		}

		Card topCard = board.drawCardFromDeck(level);
		if (topCard == null) {
			return false;
		}

		player.reserveCard(topCard);

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
	public boolean purchaseCard(Card card) {
		Player player = getCurrentPlayer();
		
		// Calculate payment
		Map<GemType, Integer> payment = rules.calculatePayment(card, player);
		
		// Validate action
		GameRules.ValidationResult result = rules.validatePurchaseCard(card, player, payment);
		if (!result.isValid()) {
			return false;
		}
		
		// Execute action
		player.purchaseCard(card, payment);
		board.addGems(payment);
		
		// Remove card from board if it's visible
		boolean removed = false;
		int cardLevel = card.getLevel();
		if (board.removeCard(cardLevel, card)) {
			removed = true;
			// Draw new card from deck to replace
			Card newCard = board.drawCardFromDeck(cardLevel);
			if (newCard != null) {
				board.addCard(cardLevel, newCard);
			}
		}
		
		// If card was reserved, remove from player's reserved cards
		if (!removed) {
			player.removeReservedCard(card);
		}
		
		// Record statistics
		statistics.recordPurchase(player, card);
		statistics.recordTurn(player, "Purchased card");
		
		// Check for noble visits
		checkNobleVisits(player);
		
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
	public boolean purchaseVisibleCard(int level, int cardIndex) {
		List<Card> cards = board.getVisibleCards(level);
		if (cardIndex < 0 || cardIndex >= cards.size()) {
			return false;
		}
		Card card = cards.get(cardIndex);
		return purchaseCard(card);
	}

	/**
	 * Executes a purchase action for one of the current player's reserved cards.
	 *
	 * @param reservedIndex the index of the reserved card
	 * @return true if the action was successful
	 */
	public boolean purchaseReservedCard(int reservedIndex) {
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
	private void checkNobleVisits(Player player) {
		List<Noble> visitableNobles = rules.getVisitableNobles(board.getAvailableNobles(), player);
		if (!visitableNobles.isEmpty() && player.getVisitedNoble() == null) {
			// In a full implementation, player would choose which noble to visit
			// For now, visit the first available noble
			Noble noble = visitableNobles.get(0);
			player.visitNoble(noble);
			board.removeNoble(noble);
		}
	}

	/**
	 * True if the current player could still take gems (bank allows a structural take),
	 * buy something, or reserve a card from the table or a deck.
	 */
	public boolean hasLegalMovesAvailable() {
		Player p = getCurrentPlayer();
		if (anyAffordablePurchase(p)) {
			return true;
		}
		if (rules.existsLegalTakeGems(p, board)) {
			return true;
		}
		if (anyReserveAvailable(p)) {
			return true;
		}
		return false;
	}

	private boolean anyAffordablePurchase(Player p) {
		for (int level = 1; level <= 3; level++) {
			for (Card c : board.getVisibleCards(level)) {
				if (c.canAfford(p.getGems(), p.getBonuses())) {
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

	private boolean anyReserveAvailable(Player p) {
		if (!rules.validateReserveCard(p).isValid()) {
			return false;
		}
		for (int level = 1; level <= 3; level++) {
			if (!board.getVisibleCards(level).isEmpty()) {
				return true;
			}
			if (board.getDeckSize(level) > 0) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Executes a pass turn action.
	 *
	 * @return true if the action was successful
	 */
	public boolean passTurn() {
		if (hasLegalMovesAvailable()) {
			return false;
		}
		Player player = getCurrentPlayer();
		statistics.recordTurn(player, "Passed turn");
		afterSuccessfulAction(player);
		nextTurn();
		return true;
	}

	/**
	 * Moves to the next player's turn.
	 */
	public void nextTurn() {
		currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
	}
}
