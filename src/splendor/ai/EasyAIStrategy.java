package splendor.ai; // This class lives in the AI package.

import java.util.ArrayList; // List to collect affordable card references.
import java.util.Collections; // shuffle() for random order.
import java.util.List; // List type for affordable cards.
import java.util.Random; // Seedless RNG for shuffling picks.

import splendor.controller.GameController; // To call purchaseCard / takeGems via rules.
import splendor.model.Card; // Board and reserved development cards.
import splendor.model.Player; // AI’s gems, bonuses, reserves.

/**
 * Easy AI strategy - makes random but valid moves.
 */
public class EasyAIStrategy implements AIStrategy { // Satisfies AIStrategy contract with simple heuristics.
	private final Random random = new Random(); // One RNG instance reused for all shuffles this AI makes.

	@Override
	public String makeMove(GameController controller, Player aiPlayer) { // One full turn decision for Easy.
		// Try to purchase any affordable card (random order)
		List<Card> affordableCards = new ArrayList<>(); // Will hold every card the AI can pay for right now.

		for (int level = 1; level <= 3; level++) { // Scan all visible rows.
			for (Card card : controller.getBoard().getVisibleCards(level)) { // Each face-up card at this level.
				if (canAffordCard(card, aiPlayer)) { // Inherited default: gems + bonuses + gold.
					affordableCards.add(card); // Candidate buy.
				}
			}
		}

		// Try reserved cards
		for (Card card : aiPlayer.getReservedCards()) { // Cards previously reserved face-up or from deck.
			if (canAffordCard(card, aiPlayer)) { // Can pay from hand + bonuses?
				affordableCards.add(card); // Also try buying from hand.
			}
		}

		if (!affordableCards.isEmpty()) { // At least one buy is possible in principle.
			Collections.shuffle(affordableCards, random); // Randomize try order (easy = unpredictable).
			for (Card card : affordableCards) { // Try each until one succeeds (validation edge cases).
				if (controller.purchaseCard(card)) { // Applies payment, nobles, stats, may replace row card.
					return "AI (Easy) purchased: " + card.toString(); // Describe what was bought.
				}
			}
		}

		String gemResult = tryAnyLegalGemTake(controller, aiPlayer, "AI (Easy) took gems"); // Shared fallback: 2 same or 3 diff.
		return gemResult != null ? gemResult : "AI (Easy) passed turn"; // If no gems legal, message for true pass (AIPlayer may repair).
	}

	@Override
	public String getStrategyName() { // Required by AIStrategy.
		return "Easy"; // Shown in console/web logs.
	}

}
