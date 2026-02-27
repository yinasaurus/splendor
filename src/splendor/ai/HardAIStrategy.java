package splendor.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GemType;
import splendor.model.Noble;
import splendor.model.Player;

/**
 * Hard AI strategy - advanced strategic planning including noble tracking.
 */
public class HardAIStrategy implements AIStrategy {
	@Override
	public String makeMove(GameController controller, Player aiPlayer) {
		// Check if we can win this turn
		Card winningCard = findWinningCard(controller, aiPlayer);
		if (winningCard != null) {
			if (controller.purchaseCard(winningCard)) {
				return "AI (Hard) purchased winning card: " + winningCard.toString();
			}
		}
		
		// Check if we're close to a noble - prioritize those gems
		Noble targetNoble = findClosestNoble(controller, aiPlayer);
		if (targetNoble != null) {
			// Try to get cards that help with noble
			Card nobleCard = findCardForNoble(controller, aiPlayer, targetNoble);
			if (nobleCard != null && canAffordCard(nobleCard, aiPlayer)) {
				if (controller.purchaseCard(nobleCard)) {
					return "AI (Hard) purchased card for noble: " + nobleCard.toString();
				}
			}
		}
		
		// Find best card considering long-term strategy
		Card bestCard = findBestStrategicCard(controller, aiPlayer);
		if (bestCard != null) {
			if (controller.purchaseCard(bestCard)) {
				return "AI (Hard) purchased: " + bestCard.toString();
			}
		}
		
		// Reserve high-value cards if beneficial
		if (aiPlayer.getReservedCards().size() < 3) {
			Card cardToReserve = findBestReserveTarget(controller, aiPlayer);
			if (cardToReserve != null) {
				for (int level = 1; level <= 3; level++) {
					List<Card> visible = controller.getBoard().getVisibleCards(level);
					int index = visible.indexOf(cardToReserve);
					if (index >= 0 && controller.reserveCard(level, index)) {
						return "AI (Hard) reserved: " + cardToReserve.toString();
					}
				}
			}
		}
		
		// Take gems to work towards specific goals
		return takeGoalOrientedGems(controller, aiPlayer, targetNoble);
	}

	@Override
	public String getStrategyName() {
		return "Hard";
	}

	private Card findWinningCard(GameController controller, Player aiPlayer) {
		int currentPoints = aiPlayer.getPrestigePoints();
		int needed = controller.getConfig().getWinningPoints() - currentPoints;
		
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (canAffordCard(card, aiPlayer) && card.getPrestigePoints() >= needed) {
					return card;
				}
			}
		}
		
		for (Card card : aiPlayer.getReservedCards()) {
			if (canAffordCard(card, aiPlayer) && card.getPrestigePoints() >= needed) {
				return card;
			}
		}
		
		return null;
	}

	private Noble findClosestNoble(GameController controller, Player aiPlayer) {
		List<Noble> nobles = controller.getBoard().getAvailableNobles();
		Noble closest = null;
		int minMissing = Integer.MAX_VALUE;
		
		for (Noble noble : nobles) {
			Map<GemType, Integer> requirement = noble.getRequirement();
			Map<GemType, Integer> bonuses = aiPlayer.getBonuses();
			
			int totalMissing = 0;
			for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
				int have = bonuses.getOrDefault(req.getKey(), 0);
				int need = req.getValue();
				totalMissing += Math.max(0, need - have);
			}
			
			if (totalMissing < minMissing) {
				minMissing = totalMissing;
				closest = noble;
			}
		}
		
		return closest;
	}

	private Card findCardForNoble(GameController controller, Player aiPlayer, Noble noble) {
		Map<GemType, Integer> requirement = noble.getRequirement();
		Map<GemType, Integer> bonuses = aiPlayer.getBonuses();
		
		// Find which gem type we need most for this noble
		GemType mostNeeded = null;
		int maxNeeded = 0;
		
		for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
			int have = bonuses.getOrDefault(req.getKey(), 0);
			int need = req.getValue();
			if (need > have && (need - have) > maxNeeded) {
				maxNeeded = need - have;
				mostNeeded = req.getKey();
			}
		}
		
		if (mostNeeded == null) {
			return null;
		}
		
		// Find affordable cards that give us the needed bonus
		List<Card> candidates = new ArrayList<>();
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (card.getBonusGem() == mostNeeded && canAffordCard(card, aiPlayer)) {
					candidates.add(card);
				}
			}
		}
		
		if (candidates.isEmpty()) {
			return null;
		}
		
		candidates.sort(Comparator.comparingDouble(card -> -evaluateCard(card, aiPlayer)));
		return candidates.get(0);
	}

	private Card findBestStrategicCard(GameController controller, Player aiPlayer) {
		List<Card> candidates = new ArrayList<>();
		
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (canAffordCard(card, aiPlayer)) {
					candidates.add(card);
				}
			}
		}
		
		for (Card card : aiPlayer.getReservedCards()) {
			if (canAffordCard(card, aiPlayer)) {
				candidates.add(card);
			}
		}
		
		if (candidates.isEmpty()) {
			return null;
		}
		
		// Evaluate considering multiple factors
		candidates.sort((c1, c2) -> {
			double score1 = evaluateCard(c1, aiPlayer) + 
				(c1.getPrestigePoints() * 2.0) - 
				(c1.getCost().values().stream().mapToInt(Integer::intValue).sum() * 0.3);
			double score2 = evaluateCard(c2, aiPlayer) + 
				(c2.getPrestigePoints() * 2.0) - 
				(c2.getCost().values().stream().mapToInt(Integer::intValue).sum() * 0.3);
			return Double.compare(score2, score1);
		});
		
		return candidates.get(0);
	}

	private Card findBestReserveTarget(GameController controller, Player aiPlayer) {
		// Reserve high-value cards that opponents might want
		List<Card> candidates = new ArrayList<>();
		
		for (int level = 3; level >= 1; level--) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (card.getPrestigePoints() >= 3) {
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

	private String takeGoalOrientedGems(GameController controller, Player aiPlayer, Noble targetNoble) {
		Map<GemType, Integer> gemsToTake = new HashMap<>();
		
		if (targetNoble != null) {
			// Take gems needed for noble
			Map<GemType, Integer> requirement = targetNoble.getRequirement();
			Map<GemType, Integer> bonuses = aiPlayer.getBonuses();
			
			for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
				int have = bonuses.getOrDefault(req.getKey(), 0);
				int need = req.getValue();
				if (need > have && controller.getBoard().getGemCount(req.getKey()) > 0) {
					gemsToTake.put(req.getKey(), 1);
					if (gemsToTake.size() >= 3) break;
				}
			}
		}
		
		// Fill remaining slots with gems needed for affordable cards
		if (gemsToTake.size() < 3) {
			// Similar logic to MediumAIStrategy
			List<GemType> needed = new ArrayList<>();
			for (int level = 1; level <= 3; level++) {
				for (Card card : controller.getBoard().getVisibleCards(level)) {
					if (!canAffordCard(card, aiPlayer)) {
						Map<GemType, Integer> cost = card.getCost();
						for (Map.Entry<GemType, Integer> entry : cost.entrySet()) {
							if (entry.getKey() != GemType.GOLD && 
								!gemsToTake.containsKey(entry.getKey()) &&
								controller.getBoard().getGemCount(entry.getKey()) > 0) {
								needed.add(entry.getKey());
							}
						}
					}
				}
			}
			
			for (GemType type : needed) {
				if (gemsToTake.size() >= 3) break;
				gemsToTake.put(type, 1);
			}
		}
		
		if (gemsToTake.size() == 3 && controller.takeGems(gemsToTake)) {
			return "AI (Hard) took goal-oriented gems";
		}
		
		// Fallback
		return "AI (Hard) passed turn";
	}
}
