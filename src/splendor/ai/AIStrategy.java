package splendor.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GameBoard;
import splendor.model.GemType;
import splendor.model.Player;
import splendor.rules.GameRules;

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

	/**
	 * Performs some legal take-gems move when the rules allow taking gems.
	 * AI strategies must not end a turn without acting while
	 * {@link GameController#hasLegalMovesAvailable()} is still true; this covers
	 * cases such as taking two of the same color (four or more in the bank),
	 * which simpler heuristics often omit while only trying three different colors.
	 *
	 * @return a message if gems were taken, or {@code null} if no gem take is legal
	 */
	default String tryAnyLegalGemTake(GameController controller, Player aiPlayer, String successMessage) {
		GameRules rules = controller.getRules();
		GameBoard board = controller.getBoard();
		if (!rules.existsLegalTakeGems(aiPlayer, board)) {
			return null;
		}
		Map<GemType, Integer> avail = board.getAvailableGems();
		Map<GemType, Integer> playerGems = aiPlayer.getGems();

		for (GemType t : GemType.values()) {
			if (t == GemType.GOLD) {
				continue;
			}
			Map<GemType, Integer> take = new HashMap<>();
			take.put(t, 2);
			if (rules.validateTakeGems(take, avail, playerGems).isValid() && controller.takeGems(take)) {
				return successMessage;
			}
		}

		List<GemType> stock = new ArrayList<>();
		for (GemType t : GemType.values()) {
			if (t != GemType.GOLD && board.getGemCount(t) > 0) {
				stock.add(t);
			}
		}
		for (int i = 0; i < stock.size(); i++) {
			for (int j = i + 1; j < stock.size(); j++) {
				for (int k = j + 1; k < stock.size(); k++) {
					Map<GemType, Integer> take = new HashMap<>();
					take.put(stock.get(i), 1);
					take.put(stock.get(j), 1);
					take.put(stock.get(k), 1);
					if (rules.validateTakeGems(take, avail, playerGems).isValid() && controller.takeGems(take)) {
						return successMessage;
					}
				}
			}
		}

		return null;
	}

	/**
	 * Reserve any legal card (visible slot first, then top of deck) when reserving is allowed.
	 * Needed when the player is at the gem cap so gem takes are impossible but a reserve still is.
	 */
	default String tryAnyLegalReserve(GameController controller, Player aiPlayer, String successMessage) {
		if (!controller.getRules().validateReserveCard(aiPlayer).isValid()) {
			return null;
		}
		GameBoard board = controller.getBoard();
		for (int level = 1; level <= 3; level++) {
			List<Card> visible = board.getVisibleCards(level);
			for (int index = 0; index < visible.size(); index++) {
				if (controller.reserveCard(level, index)) {
					return successMessage;
				}
			}
		}
		for (int level = 1; level <= 3; level++) {
			if (board.getDeckSize(level) > 0 && controller.reserveTopCard(level)) {
				return successMessage;
			}
		}
		return null;
	}

	/**
	 * Try purchasing any affordable visible or reserved card (used when a strategy picked a card
	 * that fails validation but another affordable buy still works).
	 */
	default String tryAnyLegalPurchase(GameController controller, Player aiPlayer, String successMessage) {
		List<Card> order = new ArrayList<>();
		for (int level = 1; level <= 3; level++) {
			order.addAll(controller.getBoard().getVisibleCards(level));
		}
		order.addAll(aiPlayer.getReservedCards());
		for (Card card : order) {
			if (canAffordCard(card, aiPlayer) && controller.purchaseCard(card)) {
				return successMessage;
			}
		}
		return null;
	}
}
