package splendor.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Development Card in Splendor.
 * Cards can be purchased by players and provide prestige points and gem bonuses.
 */
public class Card {
	private final int level;
	private final int prestigePoints;
	private final GemType bonusGem;
	private final Map<GemType, Integer> cost;
	private final int cardId;

	/**
	 * Constructor for Card.
	 *
	 * @param cardId unique identifier for the card
	 * @param level the level of the card (1, 2, or 3)
	 * @param prestigePoints the prestige points this card provides
	 * @param bonusGem the gem type this card provides as a bonus
	 * @param cost the cost map (gem type to quantity required)
	 */
	public Card(int cardId, int level, int prestigePoints, GemType bonusGem, Map<GemType, Integer> cost) {
		this.cardId = cardId;
		this.level = level;
		this.prestigePoints = prestigePoints;
		this.bonusGem = bonusGem;
		this.cost = new HashMap<>(cost);
	}

	/**
	 * Gets the card level.
	 *
	 * @return the level (1, 2, or 3)
	 */
	public int getLevel() {
		return level;
	}

	/**
	 * Gets the prestige points.
	 *
	 * @return the prestige points
	 */
	public int getPrestigePoints() {
		return prestigePoints;
	}

	/**
	 * Gets the bonus gem type.
	 *
	 * @return the bonus gem type
	 */
	public GemType getBonusGem() {
		return bonusGem;
	}

	/**
	 * Gets the cost map.
	 *
	 * @return a copy of the cost map
	 */
	public Map<GemType, Integer> getCost() {
		return new HashMap<>(cost);
	}

	/**
	 * Gets the card ID.
	 *
	 * @return the card ID
	 */
	public int getCardId() {
		return cardId;
	}

	/**
	 * Checks if a player can afford this card given their gems and bonuses.
	 *
	 * @param playerGems the player's gem collection
	 * @param playerBonuses the player's gem bonuses from cards
	 * @return true if the player can afford the card
	 */
	public boolean canAfford(Map<GemType, Integer> playerGems, Map<GemType, Integer> playerBonuses) {
		Map<GemType, Integer> totalResources = new HashMap<>(playerGems);
		
		// Add bonuses to available resources
		for (Map.Entry<GemType, Integer> bonus : playerBonuses.entrySet()) {
			totalResources.put(bonus.getKey(), 
				totalResources.getOrDefault(bonus.getKey(), 0) + bonus.getValue());
		}

		// Check if player has enough resources for each gem type in cost.
		// Gold must be consumed cumulatively across deficits.
		int goldRemaining = totalResources.getOrDefault(GemType.GOLD, 0);
		for (Map.Entry<GemType, Integer> costEntry : cost.entrySet()) {
			if (costEntry.getKey() == GemType.GOLD) {
				continue;
			}
			int required = costEntry.getValue();
			int available = totalResources.getOrDefault(costEntry.getKey(), 0);
			
			if (available < required) {
				int deficit = required - available;
				if (goldRemaining < deficit) {
					return false;
				}
				goldRemaining -= deficit;
			}
		}
		
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Card L").append(level).append(" ");
		sb.append("Points:").append(prestigePoints).append(" ");
		sb.append("Bonus:").append(bonusGem.getAbbreviation()).append(" ");
		sb.append("Cost:");
		for (Map.Entry<GemType, Integer> entry : cost.entrySet()) {
			sb.append(" ").append(entry.getKey().getAbbreviation()).append(":").append(entry.getValue());
		}
		return sb.toString();
	}
}
