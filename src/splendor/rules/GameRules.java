package splendor.rules;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.config.GameConfig;
import splendor.model.Card;
import splendor.model.GemType;
import splendor.model.Noble;
import splendor.model.Player;

/**
 * Validates game rules and actions according to Splendor rules.
 */
public class GameRules {
	private final GameConfig config;

	/**
	 * Constructor for GameRules.
	 *
	 * @param config the game configuration
	 */
	public GameRules(GameConfig config) {
		this.config = config;
	}

	/**
	 * Validates taking gems action.
	 *
	 * @param gemsToTake the gems the player wants to take
	 * @param availableGems the gems available on the board
	 * @param playerGems the player's current gems
	 * @return a validation result
	 */
	public ValidationResult validateTakeGems(Map<GemType, Integer> gemsToTake, 
			Map<GemType, Integer> availableGems, Map<GemType, Integer> playerGems) {
		
		// Count total gems to take
		int totalToTake = gemsToTake.values().stream().mapToInt(Integer::intValue).sum();
		
		// Rule: Can take 2 of same color if there are at least 4 available
		// Rule: Can take 3 different colors
		// Rule: Cannot take more than 10 gems total
		
		if (totalToTake == 0) {
			return new ValidationResult(false, "Must take at least one gem.");
		}
		
		if (totalToTake > 3) {
			return new ValidationResult(false, "Cannot take more than 3 gems in one turn.");
		}
		
		// Check if taking 2 of same color
		boolean takingTwoSame = false;
		for (int count : gemsToTake.values()) {
			if (count == 2) {
				takingTwoSame = true;
				break;
			}
		}
		
		if (takingTwoSame) {
			// Must be exactly 2 of one color and 0 of others
			int nonZeroCount = 0;
			GemType twoColor = null;
			for (Map.Entry<GemType, Integer> entry : gemsToTake.entrySet()) {
				if (entry.getValue() > 0) {
					nonZeroCount++;
					if (entry.getValue() == 2) {
						twoColor = entry.getKey();
					}
				}
			}
			
			if (nonZeroCount != 1 || twoColor == null) {
				return new ValidationResult(false, 
					"When taking 2 gems of same color, must take exactly 2 of one color only.");
			}
			
			// Check if at least 4 available
			if (availableGems.getOrDefault(twoColor, 0) < 4) {
				return new ValidationResult(false, 
					"Need at least 4 gems of " + twoColor.getName() + " to take 2.");
			}
		} else {
			// Taking different colors - must be 3 different colors
			if (totalToTake != 3) {
				return new ValidationResult(false, 
					"When taking different colors, must take exactly 3 different gems.");
			}
			
			int differentColors = 0;
			for (Map.Entry<GemType, Integer> entry : gemsToTake.entrySet()) {
				if (entry.getValue() == 1) {
					differentColors++;
				} else if (entry.getValue() > 1) {
					return new ValidationResult(false, 
						"When taking different colors, can only take 1 of each color.");
				}
			}
			
			if (differentColors != 3) {
				return new ValidationResult(false, 
					"Must take exactly 3 different colored gems.");
			}
		}
		
		// Check if gems are available
		for (Map.Entry<GemType, Integer> entry : gemsToTake.entrySet()) {
			if (entry.getKey() == GemType.GOLD) {
				return new ValidationResult(false, "Cannot take gold gems directly.");
			}
			if (availableGems.getOrDefault(entry.getKey(), 0) < entry.getValue()) {
				return new ValidationResult(false, 
					"Not enough " + entry.getKey().getName() + " gems available.");
			}
		}
		
		// Check if player would exceed gem limit
		int currentTotal = playerGems.values().stream().mapToInt(Integer::intValue).sum();
		if (currentTotal + totalToTake > config.getMaxGemsPerPlayer()) {
			return new ValidationResult(false, 
				"Taking these gems would exceed the maximum gem limit of " + 
				config.getMaxGemsPerPlayer() + ".");
		}
		
		return new ValidationResult(true, "Valid action.");
	}

	/**
	 * Validates reserving a card action.
	 *
	 * @param player the player attempting to reserve
	 * @return a validation result
	 */
	public ValidationResult validateReserveCard(Player player) {
		if (player.getReservedCards().size() >= config.getMaxReservedCards()) {
			return new ValidationResult(false, 
				"Already at maximum reserved cards (" + config.getMaxReservedCards() + ").");
		}
		return new ValidationResult(true, "Valid action.");
	}

	/**
	 * Validates purchasing a card action.
	 *
	 * @param card the card to purchase
	 * @param player the player attempting to purchase
	 * @param gemsToPay the gems the player wants to pay
	 * @return a validation result
	 */
	public ValidationResult validatePurchaseCard(Card card, Player player, 
			Map<GemType, Integer> gemsToPay) {
		
		Map<GemType, Integer> playerGems = player.getGems();
		Map<GemType, Integer> playerBonuses = player.getBonuses();
		Map<GemType, Integer> cost = card.getCost();
		
		// Calculate total resources (gems + bonuses)
		Map<GemType, Integer> totalResources = new HashMap<>(playerGems);
		for (Map.Entry<GemType, Integer> bonus : playerBonuses.entrySet()) {
			totalResources.put(bonus.getKey(), 
				totalResources.getOrDefault(bonus.getKey(), 0) + bonus.getValue());
		}
		
		// Check if player has enough resources
		Map<GemType, Integer> remainingCost = new HashMap<>(cost);
		int goldNeeded = 0;
		
		for (Map.Entry<GemType, Integer> costEntry : remainingCost.entrySet()) {
			if (costEntry.getKey() == GemType.GOLD) {
				continue; // Gold is not in card costs
			}
			
			int required = costEntry.getValue();
			int available = totalResources.getOrDefault(costEntry.getKey(), 0);
			
			if (available < required) {
				goldNeeded += (required - available);
			}
		}
		
		// Check if player has enough gems to pay
		Map<GemType, Integer> actualPayment = new HashMap<>();
		int goldUsed = 0;
		
		for (Map.Entry<GemType, Integer> costEntry : cost.entrySet()) {
			if (costEntry.getKey() == GemType.GOLD) {
				continue;
			}
			
			int required = costEntry.getValue();
			int fromBonuses = playerBonuses.getOrDefault(costEntry.getKey(), 0);
			int fromGems = Math.min(required, 
				playerGems.getOrDefault(costEntry.getKey(), 0));
			
			if (fromBonuses + fromGems < required) {
				int deficit = required - fromBonuses - fromGems;
				if (gemsToPay.getOrDefault(GemType.GOLD, 0) < goldUsed + deficit) {
					return new ValidationResult(false, "Insufficient gems to purchase card.");
				}
				goldUsed += deficit;
			}
			
			actualPayment.put(costEntry.getKey(), fromGems);
		}
		
		actualPayment.put(GemType.GOLD, goldUsed);
		
		// Verify payment matches what player wants to pay
		for (Map.Entry<GemType, Integer> payment : gemsToPay.entrySet()) {
			if (payment.getValue() > playerGems.getOrDefault(payment.getKey(), 0)) {
				return new ValidationResult(false, 
					"Player does not have enough " + payment.getKey().getName() + " gems.");
			}
		}
		
		return new ValidationResult(true, "Valid action.");
	}

	/**
	 * Calculates the gems needed to pay for a card.
	 *
	 * @param card the card to purchase
	 * @param player the player purchasing
	 * @return a map of gems needed to pay
	 */
	public Map<GemType, Integer> calculatePayment(Card card, Player player) {
		Map<GemType, Integer> payment = new HashMap<>();
		Map<GemType, Integer> cost = card.getCost();
		Map<GemType, Integer> playerGems = player.getGems();
		Map<GemType, Integer> playerBonuses = player.getBonuses();
		
		for (Map.Entry<GemType, Integer> costEntry : cost.entrySet()) {
			if (costEntry.getKey() == GemType.GOLD) {
				continue;
			}
			
			int required = costEntry.getValue();
			int fromBonuses = playerBonuses.getOrDefault(costEntry.getKey(), 0);
			int needed = Math.max(0, required - fromBonuses);
			
			if (needed > 0) {
				int fromGems = Math.min(needed, 
					playerGems.getOrDefault(costEntry.getKey(), 0));
				if (fromGems > 0) {
					payment.put(costEntry.getKey(), fromGems);
				}
				
				int stillNeeded = needed - fromGems;
				if (stillNeeded > 0) {
					int currentGold = payment.getOrDefault(GemType.GOLD, 0);
					payment.put(GemType.GOLD, currentGold + stillNeeded);
				}
			}
		}
		
		return payment;
	}

	/**
	 * Checks if a player can visit a noble.
	 *
	 * @param noble the noble to visit
	 * @param player the player
	 * @return true if the player can visit the noble
	 */
	public boolean canVisitNoble(Noble noble, Player player) {
		return noble.meetsRequirement(player.getBonuses());
	}

	/**
	 * Finds nobles that can visit a player.
	 *
	 * @param nobles the list of available nobles
	 * @param player the player
	 * @return a list of nobles that can visit
	 */
	public List<Noble> getVisitableNobles(List<Noble> nobles, Player player) {
		List<Noble> visitable = new ArrayList<>();
		for (Noble noble : nobles) {
			if (canVisitNoble(noble, player)) {
				visitable.add(noble);
			}
		}
		return visitable;
	}

	/**
	 * Checks if a player has won the game.
	 *
	 * @param player the player to check
	 * @return true if the player has won
	 */
	public boolean hasWon(Player player) {
		return player.getPrestigePoints() >= config.getWinningPoints();
	}

	/**
	 * Determines the winner(s) in case of tie.
	 * According to Splendor rules: player with fewest cards wins.
	 *
	 * @param players the list of players
	 * @return the winning player, or null if no winner
	 */
	public Player determineWinner(List<Player> players) {
		Player winner = null;
		int maxPoints = 0;
		int minCards = Integer.MAX_VALUE;
		
		for (Player player : players) {
			if (player.getPrestigePoints() >= config.getWinningPoints()) {
				if (player.getPrestigePoints() > maxPoints) {
					maxPoints = player.getPrestigePoints();
					minCards = player.getPurchasedCards().size();
					winner = player;
				} else if (player.getPrestigePoints() == maxPoints) {
					// Tie - player with fewer cards wins
					if (player.getPurchasedCards().size() < minCards) {
						minCards = player.getPurchasedCards().size();
						winner = player;
					}
				}
			}
		}
		
		return winner;
	}

	/**
	 * Represents the result of a validation.
	 */
	public static class ValidationResult {
		private final boolean valid;
		private final String message;

		/**
		 * Constructor for ValidationResult.
		 *
		 * @param valid whether the action is valid
		 * @param message the validation message
		 */
		public ValidationResult(boolean valid, String message) {
			this.valid = valid;
			this.message = message;
		}

		/**
		 * Checks if the result is valid.
		 *
		 * @return true if valid
		 */
		public boolean isValid() {
			return valid;
		}

		/**
		 * Gets the validation message.
		 *
		 * @return the message
		 */
		public String getMessage() {
			return message;
		}
	}
}
