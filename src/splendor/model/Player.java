package splendor.model; // Domain model: one seat at the table.

import java.util.ArrayList; // Purchased and reserved card lists.
import java.util.HashMap; // Gems and bonuses per color.
import java.util.List; // Ordered reserves, purchased history.
import java.util.Map; // GemType → count.

/**
 * Represents a player in the Splendor game.
 */
public class Player { // Mutable game state for one participant.
	private final String name; // Display / lobby name.
	private final boolean isHuman; // false → AI drives this seat in UI loop.
	private final Map<GemType, Integer> gems; // Tokens in hand (including gold).
	private final Map<GemType, Integer> bonuses; // Permanent discounts from bought cards.
	private final List<Card> purchasedCards; // Bought development cards (order = buy order).
	private final List<Card> reservedCards; // Up to 3 face-down/held cards.
	private int prestigePoints; // From cards + visited noble.
	private Noble visitedNoble; // At most one noble visit tracked (null until visit).

	/**
	 * Constructor for Player.
	 *
	 * @param name the player's name
	 * @param isHuman true if this is a human player, false if AI
	 */
	public Player(String name, boolean isHuman) { // Fresh player at game start.
		this.name = name;
		this.isHuman = isHuman;
		this.gems = new HashMap<>(); // Filled to 0 for every GemType below.
		this.bonuses = new HashMap<>();
		this.purchasedCards = new ArrayList<>();
		this.reservedCards = new ArrayList<>();
		this.prestigePoints = 0;
		this.visitedNoble = null;

		// Initialize all gem types to 0
		for (GemType type : GemType.values()) { // So getOrDefault never misses a key.
			this.gems.put(type, 0);
			this.bonuses.put(type, 0);
		}
	}

	/**
	 * Gets the player's name.
	 *
	 * @return the name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Checks if this is a human player.
	 *
	 * @return true if human, false if AI
	 */
	public boolean isHuman() { // Branch in SplendorGame main loop.
		return isHuman;
	}

	/**
	 * Gets the player's gems.
	 *
	 * @return a copy of the gems map
	 */
	public Map<GemType, Integer> getGems() { // **Copy** — external code cannot mutate hand directly.
		return new HashMap<>(gems);
	}

	/**
	 * Gets the player's gem bonuses from cards.
	 *
	 * @return a copy of the bonuses map
	 */
	public Map<GemType, Integer> getBonuses() { // Copy of discount chips.
		return new HashMap<>(bonuses);
	}

	/**
	 * Gets the player's purchased cards.
	 *
	 * @return a copy of the purchased cards list
	 */
	public List<Card> getPurchasedCards() { // Copy of list (cards themselves shared).
		return new ArrayList<>(purchasedCards);
	}

	/**
	 * Gets the player's reserved cards.
	 *
	 * @return a copy of the reserved cards list
	 */
	public List<Card> getReservedCards() {
		return new ArrayList<>(reservedCards);
	}

	/**
	 * Gets the player's prestige points.
	 *
	 * @return the prestige points
	 */
	public int getPrestigePoints() { // Total score for win checks.
		return prestigePoints;
	}

	/**
	 * Gets the visited noble.
	 *
	 * @return the visited noble, or null if none
	 */
	public Noble getVisitedNoble() { // For display “already visited”.
		return visitedNoble;
	}

	/**
	 * Adds gems to the player's collection.
	 *
	 * @param gemsToAdd the gems to add
	 */
	public void addGems(Map<GemType, Integer> gemsToAdd) { // Take from bank / gold from reserve.
		for (Map.Entry<GemType, Integer> entry : gemsToAdd.entrySet()) {
			gems.put(entry.getKey(), gems.getOrDefault(entry.getKey(), 0) + entry.getValue()); // Increment per color.
		}
	}

	/**
	 * Removes gems from the player's collection.
	 *
	 * @param gemsToRemove the gems to remove
	 */
	public void removeGems(Map<GemType, Integer> gemsToRemove) { // Pay for card or discard to bank.
		for (Map.Entry<GemType, Integer> entry : gemsToRemove.entrySet()) {
			int current = gems.getOrDefault(entry.getKey(), 0);
			gems.put(entry.getKey(), Math.max(0, current - entry.getValue())); // Floor at 0.
		}
	}

	/**
	 * Purchases a card, adding it to purchased cards and updating bonuses and prestige.
	 *
	 * @param card the card to purchase
	 * @param gemsPaid the gems paid for the card
	 */
	public void purchaseCard(Card card, Map<GemType, Integer> gemsPaid) { // Controller already validated payment map.
		purchasedCards.add(card); // Engine grows.
		prestigePoints += card.getPrestigePoints(); // Add printed points.
		bonuses.put(card.getBonusGem(), bonuses.getOrDefault(card.getBonusGem(), 0) + 1); // +1 discount of bonus color.
		removeGems(gemsPaid); // Deduct payment from hand.
	}

	/**
	 * Reserves a card.
	 *
	 * @param card the card to reserve
	 */
	public void reserveCard(Card card) { // From table or blind top.
		reservedCards.add(card);
	}

	/**
	 * Removes a reserved card.
	 *
	 * @param card the card to remove from reserved
	 */
	public void removeReservedCard(Card card) { // When buying reserved copy.
		reservedCards.remove(card); // List.remove by equals/reference.
	}

	/**
	 * Visits a noble, adding prestige points.
	 *
	 * @param noble the noble to visit
	 */
	public void visitNoble(Noble noble) { // Controller auto-picks first eligible noble.
		this.visitedNoble = noble; // Remember for UI.
		prestigePoints += noble.getPrestigePoints(); // Noble points stack.
	}

	/**
	 * Gets the total number of gems the player has.
	 *
	 * @return the total gem count
	 */
	public int getTotalGemCount() { // For 10-chip limit checks.
		return gems.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Gets the total number of bonuses the player has.
	 *
	 * @return the total bonus count
	 */
	public int getTotalBonusCount() { // Sum of all discount chips (all colors).
		return bonuses.values().stream().mapToInt(Integer::intValue).sum();
	}
}
