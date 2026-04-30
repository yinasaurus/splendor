package splendor.stats; // Turn log and per-player counters for end screen.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.model.Card;
import splendor.model.Player;

/**
 * Tracks game statistics for analysis and display.
 * Demonstrates complexity in handling game metrics.
 */
public class GameStatistics { // Owned by GameController; updated on each action.
	private final Map<Player, PlayerStats> playerStats; // One stats object per Player reference.
	private int totalTurns; // Global action counter (increments on recordTurn).
	private final List<String> gameHistory; // Human-readable chronological lines.

	/**
	 * Constructor for GameStatistics.
	 *
	 * @param players the list of players
	 */
	public GameStatistics(List<Player> players) {
		this.playerStats = new HashMap<>();
		this.totalTurns = 0;
		this.gameHistory = new ArrayList<>();

		for (Player player : players) {
			playerStats.put(player, new PlayerStats(player.getName())); // Keyed by same Player instances controller uses.
		}
	}

	/**
	 * Records a turn.
	 *
	 * @param player the player who took the turn
	 * @param action the action taken
	 */
	public void recordTurn(Player player, String action) { // Called from GameController after each successful action.
		totalTurns++;
		PlayerStats stats = playerStats.get(player);
		if (stats != null) {
			stats.incrementTurns();
			gameHistory.add("Turn " + totalTurns + ": " + player.getName() + " - " + action);
		}
	}

	/**
	 * Records a card purchase.
	 *
	 * @param player the player who purchased
	 * @param card the card purchased
	 */
	public void recordPurchase(Player player, Card card) {
		PlayerStats stats = playerStats.get(player);
		if (stats != null) {
			stats.incrementPurchases();
			stats.addPrestigePoints(card.getPrestigePoints()); // Stats track card-only prestige separately from nobles.
		}
	}

	/**
	 * Records a card reservation.
	 *
	 * @param player the player who reserved
	 */
	public void recordReservation(Player player) {
		PlayerStats stats = playerStats.get(player);
		if (stats != null) {
			stats.incrementReservations();
		}
	}

	/**
	 * Records gem taking.
	 *
	 * @param player the player who took gems
	 * @param gemCount the number of gems taken
	 */
	public void recordGemsTaken(Player player, int gemCount) {
		PlayerStats stats = playerStats.get(player);
		if (stats != null) {
			stats.addGemsTaken(gemCount);
		}
	}

	/**
	 * Gets statistics for a player.
	 *
	 * @param player the player
	 * @return the player statistics
	 */
	public PlayerStats getPlayerStats(Player player) {
		return playerStats.get(player);
	}

	/**
	 * Gets all player statistics.
	 *
	 * @return a map of player to statistics
	 */
	public Map<Player, PlayerStats> getAllStats() {
		return new HashMap<>(playerStats);
	}

	/**
	 * Gets the total number of turns.
	 *
	 * @return the total turns
	 */
	public int getTotalTurns() {
		return totalTurns;
	}

	/**
	 * Gets the game history.
	 *
	 * @return the game history
	 */
	public List<String> getGameHistory() {
		return new ArrayList<>(gameHistory);
	}

	/**
	 * Represents statistics for a single player.
	 */
	public static class PlayerStats { // Nested type: logical grouping inside GameStatistics.
		private final String playerName;
		private int turns;
		private int purchases;
		private int reservations;
		private int gemsTaken;
		private int prestigeFromCards;

		/**
		 * Constructor for PlayerStats.
		 *
		 * @param playerName the player's name
		 */
		public PlayerStats(String playerName) {
			this.playerName = playerName;
			this.turns = 0;
			this.purchases = 0;
			this.reservations = 0;
			this.gemsTaken = 0;
			this.prestigeFromCards = 0;
		}

		/**
		 * Increments the turn count.
		 */
		public void incrementTurns() {
			turns++;
		}

		/**
		 * Increments the purchase count.
		 */
		public void incrementPurchases() {
			purchases++;
		}

		/**
		 * Increments the reservation count.
		 */
		public void incrementReservations() {
			reservations++;
		}

		/**
		 * Adds gems taken.
		 *
		 * @param count the number of gems
		 */
		public void addGemsTaken(int count) {
			gemsTaken += count;
		}

		/**
		 * Adds prestige points from cards.
		 *
		 * @param points the prestige points
		 */
		public void addPrestigePoints(int points) {
			prestigeFromCards += points;
		}

		// Getters
		public String getPlayerName() { return playerName; }
		public int getTurns() { return turns; }
		public int getPurchases() { return purchases; }
		public int getReservations() { return reservations; }
		public int getGemsTaken() { return gemsTaken; }
		public int getPrestigeFromCards() { return prestigeFromCards; }
	}
}
