package splendor.ai; // Package: groups AI-related types together.

import java.util.ArrayList; // Resizable list for gem colors in stock.
import java.util.HashMap; // Map for gem counts in take patterns.
import java.util.List; // List interface type.
import java.util.Map; // Map interface for costs/bonuses in helpers.
import splendor.controller.GameController; // Game engine reference passed into strategies.
import splendor.model.Card; // Development cards on board or reserved.
import splendor.model.GameBoard; // Bank gems, visible rows, decks.
import splendor.model.GemType; // Enum of gem colors (and gold).
import splendor.model.Player; // Current AI’s hand, bonuses, reserves.
import splendor.rules.GameRules; // Validates whether a gem take (etc.) is legal.

/**
 * Strategy interface for AI players.
 * Implements Strategy Pattern to allow different AI difficulty levels.
 */
public interface AIStrategy { // Interface: concrete classes supply makeMove / getStrategyName.
	/**
	 * Makes a decision for the AI player's turn.
	 *
	 * @param controller the game controller
	 * @param aiPlayer the AI player
	 * @return a string describing the action taken, or null if no action
	 */
	String makeMove(GameController controller, Player aiPlayer); // Implemented by Easy/Medium/Hard; one turn’s decision.

	/**
	 * Gets the strategy name.
	 *
	 * @return the strategy name
	 */
	String getStrategyName(); // Short label for logs/UI (“Easy”, “Medium”, “Hard”).

	/**
	 * Evaluates the value of a card for the AI player.
	 *
	 * @param card the card to evaluate
	 * @param player the player
	 * @return the card value score
	 */
	default double evaluateCard(Card card, Player player) { // Default = shared by all implementors; higher = more desirable.
		double value = card.getPrestigePoints() * 3.0; // Weight prestige heavily in the heuristic score.

		// Bonus for cards that match player's existing bonuses
		Map<GemType, Integer> bonuses = player.getBonuses(); // Permanent discounts from cards already bought.
		if (bonuses.getOrDefault(card.getBonusGem(), 0) > 0) { // This card’s bonus color already stacked?
			value += 1.0; // Synergy bonus
		} // this is for how many permanent bonuses the player already has from bonuses

		// Penalty for expensive cards
		int totalCost = card.getCost().values().stream().mapToInt(Integer::intValue).sum(); // Sum all gem pips on cost.
		// total cost of a card
		value -= totalCost * 0.2; // Slightly prefer cheaper cards when prestige is similar.

		return value; // Final heuristic score for comparing two cards.
	}

	/**
	 * Checks if player can afford a card.
	 *
	 * @param card the card to check
	 * @param player the player
	 * @return true if affordable
	 */
	default boolean canAffordCard(Card card, Player player) { // Thin wrapper around Card’s affordability logic.
		return card.canAfford(player.getGems(), player.getBonuses()); // Gems + bonuses + gold substitution.
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
	default String tryAnyLegalGemTake(GameController controller, Player aiPlayer, String successMessage) { // Brute-force legal takes.
		GameRules rules = controller.getRules(); // Access validation helpers.
		GameBoard board = controller.getBoard(); // Bank supply and visible state.
		if (!rules.existsLegalTakeGems(aiPlayer, board)) { // Quick check: hand limit + bank make any take impossible?
			return null; // Caller may try reserve/buy instead.
		}
		Map<GemType, Integer> avail = board.getAvailableGems(); // Snapshot of bank for validateTakeGems.
		Map<GemType, Integer> playerGems = aiPlayer.getGems(); // Current hand for limit checks.

		for (GemType t : GemType.values()) { // Try “take 2 same color” for each non-gold type.
			if (t == GemType.GOLD) { // Gold is never taken from the bank this way.
				continue; // Skip to next enum constant.
			}
			Map<GemType, Integer> take = new HashMap<>(); // Build one legal-pattern map.
			take.put(t, 2); // Splendor rule: exactly 2 of one color, bank must have ≥4.
			if (rules.validateTakeGems(take, avail, playerGems).isValid() && controller.takeGems(take)) { // Rules OK and apply move.
				return successMessage; // Human-readable success string for UI/log.
			}
		}

		List<GemType> stock = new ArrayList<>(); // Colors with at least one chip in bank.
		for (GemType t : GemType.values()) { // Enumerate bank colors.
			if (t != GemType.GOLD && board.getGemCount(t) > 0) { // Non-gold with supply.
				stock.add(t); // Candidate for “3 different” take.
			}
		}
		for (int i = 0; i < stock.size(); i++) { // First color of triple.
			for (int j = i + 1; j < stock.size(); j++) { // Second color, distinct from i.
				for (int k = j + 1; k < stock.size(); k++) { // Third color, distinct from i and j.
					Map<GemType, Integer> take = new HashMap<>(); // Exactly three 1s on three colors.
					take.put(stock.get(i), 1);
					take.put(stock.get(j), 1);
					take.put(stock.get(k), 1);
					if (rules.validateTakeGems(take, avail, playerGems).isValid() && controller.takeGems(take)) { // Legal 3-diff take.
						return successMessage;
					}
				}
			}
		}

		return null; // No pattern worked (should be rare if existsLegalTakeGems was true).
	}

	/**
	 * Reserve any legal card (visible slot first, then top of deck) when reserving is allowed.
	 * Needed when the player is at the gem cap so gem takes are impossible but a reserve still is.
	 */
	default String tryAnyLegalReserve(GameController controller, Player aiPlayer, String successMessage) { // Exhaustive reserve attempts.
		if (!controller.getRules().validateReserveCard(aiPlayer).isValid()) { // e.g. already 3 reserved.
			return null; // Cannot reserve at all.
		}
		GameBoard board = controller.getBoard(); // Need visible rows and deck sizes.
		for (int level = 1; level <= 3; level++) { // Try face-up cards level 1→3.
			List<Card> visible = board.getVisibleCards(level); // Up to 4 slots per level.
			for (int index = 0; index < visible.size(); index++) { // Each slot index.
				if (controller.reserveCard(level, index)) { // First successful reserve wins.
					return successMessage;
				}
			}
		}
		for (int level = 1; level <= 3; level++) { // If no visible reserve worked, blind reserve from deck tops.
			if (board.getDeckSize(level) > 0 && controller.reserveTopCard(level)) { // Deck not empty and action succeeds.
				return successMessage;
			}
		}
		return null; // No reserve succeeded (unexpected if validate passed and board has cards).
	}

	/**
	 * Try purchasing any affordable visible or reserved card (used when a strategy picked a card
	 * that fails validation but another affordable buy still works).
	 */
	default String tryAnyLegalPurchase(GameController controller, Player aiPlayer, String successMessage) { // Ordered brute-force buy.
		List<Card> order = new ArrayList<>(); // Cards to try in sequence.
		for (int level = 1; level <= 3; level++) { // Gather visible cards.
			order.addAll(controller.getBoard().getVisibleCards(level)); // Append whole row.
		}
		order.addAll(aiPlayer.getReservedCards()); // Then try reserved pile.
		for (Card card : order) { // First successful purchase stops.
			if (canAffordCard(card, aiPlayer) && controller.purchaseCard(card)) { // Afford + full rules + execute.
				return successMessage;
			}
		}
		return null; // Nothing could be bought.
	}
}
