package splendor.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.Player;

/**
 * Easy AI strategy - makes random but valid moves.
 */
public class EasyAIStrategy implements AIStrategy {
	private final Random random = new Random();

	@Override
	public String makeMove(GameController controller, Player aiPlayer) {
		// Try to purchase any affordable card (random order)
		List<Card> affordableCards = new ArrayList<>();
		
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (canAffordCard(card, aiPlayer)) {
					affordableCards.add(card);
				}
			}
		}
		
		// Try reserved cards
		for (Card card : aiPlayer.getReservedCards()) {
			if (canAffordCard(card, aiPlayer)) {
				affordableCards.add(card);
			}
		}
		
		if (!affordableCards.isEmpty()) {
			Collections.shuffle(affordableCards, random);
			for (Card card : affordableCards) {
				if (controller.purchaseCard(card)) {
					return "AI (Easy) purchased: " + card.toString();
				}
			}
		}

		String gemResult = tryAnyLegalGemTake(controller, aiPlayer, "AI (Easy) took gems");
		return gemResult != null ? gemResult : "AI (Easy) passed turn";
	}

	@Override
	public String getStrategyName() {
		return "Easy";
	}

}
