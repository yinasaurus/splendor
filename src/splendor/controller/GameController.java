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
				endgameTurnsRemaining = Math.max(0, players.size() - 1);
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
	 * Executes a reserve card action.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param cardIndex the index of the card in the visible cards
	 * @return true if the action was successful
	 */
	public boolean reserveCard(int level, int cardIndex) {
		Player player = getCurrentPlayer();
		
		// Validate action
		GameRules.ValidationResult result = rules.validateReserveCard(player);
		if (!result.isValid()) {
			return false;
		}
		
		// Get the card
		List<Card> visibleCards = board.getVisibleCards(level);
		if (cardIndex < 0 || cardIndex >= visibleCards.size()) {
			return false;
		}
		
		Card card = visibleCards.get(cardIndex);
		
		// Execute action
		board.removeCard(level, card);
		player.reserveCard(card);
		
		// Draw new card from deck to replace
		Card newCard = board.drawCardFromDeck(level);
		if (newCard != null) {
			board.addCard(level, newCard);
		}
		
		// Give player a gold gem if available
		if (board.getGemCount(GemType.GOLD) > 0) {
			Map<GemType, Integer> gold = new HashMap<>();
			gold.put(GemType.GOLD, 1);
			board.removeGems(gold);
			player.addGems(gold);
		}
		
		// Record statistics
		statistics.recordReservation(player);
		statistics.recordTurn(player, "Reserved card");
		
		// Check for noble visits
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
	 * Moves to the next player's turn.
	 */
	public void nextTurn() {
		currentPlayerIndex = (currentPlayerIndex + 1) % players.size();
	}
}
