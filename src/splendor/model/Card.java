package splendor.model; // Domain model: development cards.

import java.util.HashMap; // Copy cost on getCost / constructor.
import java.util.Map; // Gem costs and lookups in canAfford.

/**
 * Represents a Development Card in Splendor.
 * Cards can be purchased by players and provide prestige points and gem bonuses.
 */
public class Card { // Immutable after construction except identity is fixed.
	private final int level; // 1, 2, or 3 — deck tier.
	private final int prestigePoints; // Victory points printed on card (often 0 on level 1).
	private final GemType bonusGem; // Permanent discount color this card grants when bought.
	private final Map<GemType, Integer> cost; // Colored gem cost (not gold in data—gold pays in rules).
	private final int cardId; // From CSV or generated id.

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
		this.cost = new HashMap<>(cost); // Internal defensive copy.
	}

	/**
	 * Gets the card level.
	 *
	 * @return the level (1, 2, or 3)
	 */
	public int getLevel() { // Which row / deck.
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
	public GemType getBonusGem() { // Discount color.
		return bonusGem;
	}

	/**
	 * Gets the cost map.
	 *
	 * @return a copy of the cost map
	 */
	public Map<GemType, Integer> getCost() { // Safe for callers to read without mutating card.
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
	public boolean canAfford(Map<GemType, Integer> playerGems, Map<GemType, Integer> playerBonuses) { // Quick check before rules.validate.
		Map<GemType, Integer> totalResources = new HashMap<>(playerGems); // Start with hand.

		// Add bonuses to available resources
		for (Map.Entry<GemType, Integer> bonus : playerBonuses.entrySet()) { // Merge discounts as if extra gems.
			totalResources.put(bonus.getKey(),
				totalResources.getOrDefault(bonus.getKey(), 0) + bonus.getValue());
		}

		// Check if player has enough resources for each gem type in cost.
		// Gold must be consumed cumulatively across deficits.
		int goldRemaining = totalResources.getOrDefault(GemType.GOLD, 0); // Wild pool for covering shortfalls.
		for (Map.Entry<GemType, Integer> costEntry : cost.entrySet()) { // Each color in printed cost.
			if (costEntry.getKey() == GemType.GOLD) { // Cost maps normally omit gold.
				continue;
			}
			int required = costEntry.getValue(); // Pips needed for this color after “virtual” merge.
			int available = totalResources.getOrDefault(costEntry.getKey(), 0); // Gems+bonuses for this color.

			if (available < required) { // Must spend gold for the gap.
				int deficit = required - available; // How many wilds needed.
				if (goldRemaining < deficit) { // Not enough gold.
					return false;
				}
				goldRemaining -= deficit; // Spend wilds.
			}
		}

		return true; // Every color satisfied.
	}

	@Override
	public String toString() { // Console / debug line.
		StringBuilder sb = new StringBuilder();
		sb.append("Card L").append(level).append(" ");
		sb.append("Points:").append(prestigePoints).append(" ");
		sb.append("Bonus:").append(bonusGem.getAbbreviation()).append(" ");
		sb.append("Cost:");
		for (Map.Entry<GemType, Integer> entry : cost.entrySet()) { // R:2 E:1 style.
			sb.append(" ").append(entry.getKey().getAbbreviation()).append(":").append(entry.getValue());
		}
		return sb.toString();
	}
}
