package splendor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a player in the Splendor game.
 */
public class Player {
	private final String name;
	private final boolean isHuman;
	private final Map<GemType, Integer> gems;
	private final Map<GemType, Integer> bonuses;
	private final List<Card> purchasedCards;
	private final List<Card> reservedCards;
	private int prestigePoints;
	private Noble visitedNoble;

	/**
	 * Constructor for Player.
	 *
	 * @param name the player's name
	 * @param isHuman true if this is a human player, false if AI
	 */
	public Player(String name, boolean isHuman) {
		this.name = name;
		this.isHuman = isHuman;
		this.gems = new HashMap<>();
		this.bonuses = new HashMap<>();
		this.purchasedCards = new ArrayList<>();
		this.reservedCards = new ArrayList<>();
		this.prestigePoints = 0;
		this.visitedNoble = null;
		
		// Initialize all gem types to 0
		for (GemType type : GemType.values()) {
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
	public boolean isHuman() {
		return isHuman;
	}

	/**
	 * Gets the player's gems.
	 *
	 * @return a copy of the gems map
	 */
	public Map<GemType, Integer> getGems() {
		return new HashMap<>(gems);
	}

	/**
	 * Gets the player's gem bonuses from cards.
	 *
	 * @return a copy of the bonuses map
	 */
	public Map<GemType, Integer> getBonuses() {
		return new HashMap<>(bonuses);
	}

	/**
	 * Gets the player's purchased cards.
	 *
	 * @return a copy of the purchased cards list
	 */
	public List<Card> getPurchasedCards() {
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
	public int getPrestigePoints() {
		return prestigePoints;
	}

	/**
	 * Gets the visited noble.
	 *
	 * @return the visited noble, or null if none
	 */
	public Noble getVisitedNoble() {
		return visitedNoble;
	}

	/**
	 * Adds gems to the player's collection.
	 *
	 * @param gemsToAdd the gems to add
	 */
	public void addGems(Map<GemType, Integer> gemsToAdd) {
		for (Map.Entry<GemType, Integer> entry : gemsToAdd.entrySet()) {
			gems.put(entry.getKey(), gems.getOrDefault(entry.getKey(), 0) + entry.getValue());
		}
	}

	/**
	 * Removes gems from the player's collection.
	 *
	 * @param gemsToRemove the gems to remove
	 */
	public void removeGems(Map<GemType, Integer> gemsToRemove) {
		for (Map.Entry<GemType, Integer> entry : gemsToRemove.entrySet()) {
			int current = gems.getOrDefault(entry.getKey(), 0);
			gems.put(entry.getKey(), Math.max(0, current - entry.getValue()));
		}
	}

	/**
	 * Purchases a card, adding it to purchased cards and updating bonuses and prestige.
	 *
	 * @param card the card to purchase
	 * @param gemsPaid the gems paid for the card
	 */
	public void purchaseCard(Card card, Map<GemType, Integer> gemsPaid) {
		purchasedCards.add(card);
		prestigePoints += card.getPrestigePoints();
		bonuses.put(card.getBonusGem(), bonuses.getOrDefault(card.getBonusGem(), 0) + 1);
		removeGems(gemsPaid);
	}

	/**
	 * Reserves a card.
	 *
	 * @param card the card to reserve
	 */
	public void reserveCard(Card card) {
		reservedCards.add(card);
	}

	/**
	 * Removes a reserved card.
	 *
	 * @param card the card to remove from reserved
	 */
	public void removeReservedCard(Card card) {
		reservedCards.remove(card);
	}

	/**
	 * Visits a noble, adding prestige points.
	 *
	 * @param noble the noble to visit
	 */
	public void visitNoble(Noble noble) {
		this.visitedNoble = noble;
		prestigePoints += noble.getPrestigePoints();
	}

	/**
	 * Gets the total number of gems the player has.
	 *
	 * @return the total gem count
	 */
	public int getTotalGemCount() {
		return gems.values().stream().mapToInt(Integer::intValue).sum();
	}

	/**
	 * Gets the total number of bonuses the player has.
	 *
	 * @return the total bonus count
	 */
	public int getTotalBonusCount() {
		return bonuses.values().stream().mapToInt(Integer::intValue).sum();
	}
}
