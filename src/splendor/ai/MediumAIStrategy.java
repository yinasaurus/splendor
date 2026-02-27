package splendor.ai;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GemType;
import splendor.model.Player;

/**
 * Medium AI strategy - evaluates cards and makes strategic decisions.
 */
public class MediumAIStrategy implements AIStrategy {
	@Override
	public String makeMove(GameController controller, Player aiPlayer) {
		// Find best affordable card
		Card bestCard = findBestAffordableCard(controller, aiPlayer);
		
		if (bestCard != null) {
			if (controller.purchaseCard(bestCard)) {
				return "AI (Medium) purchased: " + bestCard.toString();
			}
		}
		
		// Try to reserve a high-value card if we have space
		if (aiPlayer.getReservedCards().size() < 3) {
			Card cardToReserve = findBestCardToReserve(controller, aiPlayer);
			if (cardToReserve != null) {
				for (int level = 1; level <= 3; level++) {
					List<Card> visible = controller.getBoard().getVisibleCards(level);
					int index = visible.indexOf(cardToReserve);
					if (index >= 0 && controller.reserveCard(level, index)) {
						return "AI (Medium) reserved: " + cardToReserve.toString();
					}
				}
			}
		}
		
		// Take gems strategically
		return takeStrategicGems(controller, aiPlayer);
	}

	@Override
	public String getStrategyName() {
		return "Medium";
	}

	private Card findBestAffordableCard(GameController controller, Player aiPlayer) {
		List<Card> candidates = new ArrayList<>();
		
		// Check visible cards
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (canAffordCard(card, aiPlayer)) {
					candidates.add(card);
				}
			}
		}
		
		// Check reserved cards
		for (Card card : aiPlayer.getReservedCards()) {
			if (canAffordCard(card, aiPlayer)) {
				candidates.add(card);
			}
		}
		
		if (candidates.isEmpty()) {
			return null;
		}
		
		// Sort by value and return best
		candidates.sort(Comparator.comparingDouble(card -> -evaluateCard(card, aiPlayer)));
		return candidates.get(0);
	}

	private Card findBestCardToReserve(GameController controller, Player aiPlayer) {
		List<Card> candidates = new ArrayList<>();
		
		// Prefer level 3 cards with high prestige
		for (int level = 3; level >= 1; level--) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (card.getPrestigePoints() > 0) {
					candidates.add(card);
				}
			}
		}
		
		if (candidates.isEmpty()) {
			return null;
		}
		
		candidates.sort(Comparator.comparingInt(Card::getPrestigePoints).reversed());
		return candidates.get(0);
	}

	private String takeStrategicGems(GameController controller, Player aiPlayer) {
		// Analyze which gems we need most
		Map<GemType, Integer> needed = new HashMap<>();
		
		// Check what gems we need for affordable cards
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (!canAffordCard(card, aiPlayer)) {
					Map<GemType, Integer> cost = card.getCost();
					Map<GemType, Integer> bonuses = aiPlayer.getBonuses();
					
					for (Map.Entry<GemType, Integer> entry : cost.entrySet()) {
						if (entry.getKey() != GemType.GOLD) {
							int required = entry.getValue();
							int have = aiPlayer.getGems().getOrDefault(entry.getKey(), 0) +
								bonuses.getOrDefault(entry.getKey(), 0);
							if (required > have) {
								needed.put(entry.getKey(), 
									needed.getOrDefault(entry.getKey(), 0) + (required - have));
							}
						}
					}
				}
			}
		}
		
		// Take gems we need most
		if (!needed.isEmpty()) {
			List<GemType> priorityGems = new ArrayList<>(needed.keySet());
			priorityGems.sort(Comparator.comparingInt(needed::get).reversed());
			
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			for (int i = 0; i < Math.min(3, priorityGems.size()); i++) {
				GemType type = priorityGems.get(i);
				if (controller.getBoard().getGemCount(type) > 0) {
					gemsToTake.put(type, 1);
				}
			}
			
			if (gemsToTake.size() == 3 && controller.takeGems(gemsToTake)) {
				return "AI (Medium) took strategic gems";
			}
		}
		
		// Fallback: take any available gems
		List<GemType> available = new ArrayList<>();
		for (GemType type : GemType.values()) {
			if (type != GemType.GOLD && 
				controller.getBoard().getGemCount(type) > 0 &&
				aiPlayer.getTotalGemCount() < 10) {
				available.add(type);
			}
		}
		
		if (available.size() >= 3) {
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			for (int i = 0; i < 3; i++) {
				gemsToTake.put(available.get(i), 1);
			}
			if (controller.takeGems(gemsToTake)) {
				return "AI (Medium) took gems";
			}
		}
		
		return "AI (Medium) passed turn";
	}
}
