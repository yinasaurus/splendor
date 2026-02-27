package splendor.ai;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GemType;
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
			Card card = affordableCards.get(random.nextInt(affordableCards.size()));
			if (controller.purchaseCard(card)) {
				return "AI (Easy) purchased: " + card.toString();
			}
		}
		
		// Take random gems
		return takeRandomGems(controller, aiPlayer);
	}

	@Override
	public String getStrategyName() {
		return "Easy";
	}

	private String takeRandomGems(GameController controller, Player aiPlayer) {
		List<GemType> availableTypes = new ArrayList<>();
		for (GemType type : GemType.values()) {
			if (type != GemType.GOLD && 
				controller.getBoard().getGemCount(type) > 0 &&
				aiPlayer.getTotalGemCount() < 10) {
				availableTypes.add(type);
			}
		}
		
		if (availableTypes.size() >= 3) {
			// Take 3 different
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			for (int i = 0; i < 3 && i < availableTypes.size(); i++) {
				gemsToTake.put(availableTypes.get(i), 1);
			}
			if (controller.takeGems(gemsToTake)) {
				return "AI (Easy) took 3 different gems";
			}
		}
		
		return "AI (Easy) passed turn";
	}
}
