package splendor.ai;

import java.util.List;
import java.util.Map;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GemType;
import splendor.model.Player;

/**
 * Strategy interface for AI players.
 * Implements Strategy Pattern to allow different AI difficulty levels.
 */
public interface AIStrategy {
	/**
	 * Makes a decision for the AI player's turn.
	 *
	 * @param controller the game controller
	 * @param aiPlayer the AI player
	 * @return a string describing the action taken, or null if no action
	 */
	String makeMove(GameController controller, Player aiPlayer);
	
	/**
	 * Gets the strategy name.
	 *
	 * @return the strategy name
	 */
	String getStrategyName();
	
	/**
	 * Evaluates the value of a card for the AI player.
	 *
	 * @param card the card to evaluate
	 * @param player the player
	 * @return the card value score
	 */
	default double evaluateCard(Card card, Player player) {
		double value = card.getPrestigePoints() * 3.0; // Prestige is valuable
		
		// Bonus for cards that match player's existing bonuses
		Map<GemType, Integer> bonuses = player.getBonuses();
		if (bonuses.getOrDefault(card.getBonusGem(), 0) > 0) {
			value += 1.0; // Synergy bonus
		}
		
		// Penalty for expensive cards
		int totalCost = card.getCost().values().stream().mapToInt(Integer::intValue).sum();
		value -= totalCost * 0.2;
		
		return value;
	}
	
	/**
	 * Checks if player can afford a card.
	 *
	 * @param card the card to check
	 * @param player the player
	 * @return true if affordable
	 */
	default boolean canAffordCard(Card card, Player player) {
		return card.canAfford(player.getGems(), player.getBonuses());
	}
}
