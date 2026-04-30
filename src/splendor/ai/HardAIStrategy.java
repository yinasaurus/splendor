package splendor.ai; // AI package.

import java.util.ArrayList; // Lists for candidates, opponents, needed gems.
import java.util.Comparator; // Sorting cards by score or prestige.
import java.util.HashMap; // Gem take maps, noble requirements.
import java.util.List; // Typed collections.
import java.util.Map; // Requirements and costs.

import splendor.controller.GameController; // Full game API + config.
import splendor.model.Card; // Development cards.
import splendor.model.GemType; // Colors.
import splendor.model.Noble; // Board nobles for bonus goals.
import splendor.model.Player; // All seats for blocking logic.

/**
 * Hard AI strategy - advanced strategic planning including noble tracking.
 */
public class HardAIStrategy implements AIStrategy { // Most elaborate heuristics.
	@Override
	public String makeMove(GameController controller, Player aiPlayer) { // One Hard AI turn.
		// Check if we can win this turn
		Card winningCard = findWinningCard(controller, aiPlayer); // Affordable card reaching win threshold.
		if (winningCard != null) { // Immediate win opportunity.
			if (controller.purchaseCard(winningCard)) { // Take the win if rules allow.
				return "AI (Hard) purchased winning card: " + winningCard.toString();
			}
		}

		// Check if we're close to a noble - prioritize those gems
		Noble targetNoble = findClosestNoble(controller, aiPlayer); // Smallest bonus shortfall to any noble.
		if (targetNoble != null) { // Nobles remain and one is “closest”.
			// Try to get cards that help with noble
			Card nobleCard = findCardForNoble(controller, aiPlayer, targetNoble); // Buy that grants needed bonus color.
			if (nobleCard != null && canAffordCard(nobleCard, aiPlayer)) { // Card exists and is payable.
				if (controller.purchaseCard(nobleCard)) {
					return "AI (Hard) purchased card for noble: " + nobleCard.toString();
				}
			}
		}

		// Block opponents from taking dangerous cards (especially potential winning buys).
		Card blockingTarget = findBestBlockingCard(controller, aiPlayer); // High threat to opponents, ok for us.
		if (blockingTarget != null) { // Something worth denying.
			if (canAffordCard(blockingTarget, aiPlayer) && controller.purchaseCard(blockingTarget)) { // Snipe off table if cheap enough.
				return "AI (Hard) blocked by purchasing: " + blockingTarget.toString();
			}
			if (aiPlayer.getReservedCards().size() < 3) { // Can still reserve.
				for (int level = 1; level <= 3; level++) { // Locate blocking card on a row.
					List<Card> visible = controller.getBoard().getVisibleCards(level);
					int index = visible.indexOf(blockingTarget);
					if (index >= 0 && controller.reserveCard(level, index)) { // Yank it with a reserve token + gold.
						return "AI (Hard) blocked by reserving: " + blockingTarget.toString();
					}
				}
			}
		}

		// Find best card considering long-term strategy
		Card bestCard = findBestStrategicCard(controller, aiPlayer); // Custom composite score sort.
		if (bestCard != null) { // At least one affordable card.
			if (controller.purchaseCard(bestCard)) {
				return "AI (Hard) purchased: " + bestCard.toString();
			}
		}

		// Reserve high-value cards if beneficial
		if (aiPlayer.getReservedCards().size() < 3) { // Room in reserve hand.
			Card cardToReserve = findBestReserveTarget(controller, aiPlayer); // High prestige visible card.
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
		return takeGoalOrientedGems(controller, aiPlayer, targetNoble); // Noble-tinted then fallback gem logic.
	}

	@Override
	public String getStrategyName() { // AIStrategy.
		return "Hard";
	}

	private Card findWinningCard(GameController controller, Player aiPlayer) { // Finishes game this turn if possible.
		int currentPoints = aiPlayer.getPrestigePoints(); // Points from cards + nobles so far.
		int needed = controller.getConfig().getWinningPoints() - currentPoints; // Prestige still needed to hit win line.

		for (int level = 1; level <= 3; level++) { // Search visible rows.
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (canAffordCard(card, aiPlayer) && card.getPrestigePoints() >= needed) { // Buy closes the gap in one card.
					return card;
				}
			}
		}

		for (Card card : aiPlayer.getReservedCards()) { // Same check in hand.
			if (canAffordCard(card, aiPlayer) && card.getPrestigePoints() >= needed) {
				return card;
			}
		}

		return null; // No single buy wins yet.
	}

	private Noble findClosestNoble(GameController controller, Player aiPlayer) { // Noble requiring fewest missing bonuses.
		List<Noble> nobles = controller.getBoard().getAvailableNobles(); // Still on board.
		Noble closest = null; // Best noble so far.
		int minMissing = Integer.MAX_VALUE; // Lower = closer to visit.

		for (Noble noble : nobles) { // Score each noble.
			Map<GemType, Integer> requirement = noble.getRequirement(); // Bonus counts needed.
			Map<GemType, Integer> bonuses = aiPlayer.getBonuses(); // Our discounts.

			int totalMissing = 0; // Sum of shortfalls across colors.
			for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
				int have = bonuses.getOrDefault(req.getKey(), 0); // Cards owned giving that color bonus.
				int need = req.getValue(); // Noble’s requirement for that color.
				totalMissing += Math.max(0, need - have); // How many bonus steps still missing.
			}

			if (totalMissing < minMissing) { // Strictly closer than previous best.
				minMissing = totalMissing;
				closest = noble;
			}
		}

		return closest; // May be null if no nobles (list empty loop).
	}

	private Card findCardForNoble(GameController controller, Player aiPlayer, Noble noble) { // Buy that progresses noble.
		Map<GemType, Integer> requirement = noble.getRequirement();
		Map<GemType, Integer> bonuses = aiPlayer.getBonuses();

		// Find which gem type we need most for this noble
		GemType mostNeeded = null; // Bonus color we lack most.
		int maxNeeded = 0; // Largest gap need-have.

		for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
			int have = bonuses.getOrDefault(req.getKey(), 0);
			int need = req.getValue();
			if (need > have && (need - have) > maxNeeded) { // Track biggest shortfall.
				maxNeeded = need - have;
				mostNeeded = req.getKey();
			}
		}

		if (mostNeeded == null) { // Already meet noble (shouldn’t buy for this reason).
			return null;
		}

		// Find affordable cards that give us the needed bonus
		List<Card> candidates = new ArrayList<>();
		for (int level = 1; level <= 3; level++) {
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (card.getBonusGem() == mostNeeded && canAffordCard(card, aiPlayer)) { // Right discount color and payable.
					candidates.add(card);
				}
			}
		}

		if (candidates.isEmpty()) {
			return null;
		}

		candidates.sort(Comparator.comparingDouble(card -> -evaluateCard(card, aiPlayer))); // Best heuristic among them.
		return candidates.get(0);
	}

	private Card findBestStrategicCard(GameController controller, Player aiPlayer) { // General strongest buy.
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
		candidates.sort((c1, c2) -> { // Custom comparator: higher score sorts earlier.
			double score1 = evaluateCard(c1, aiPlayer) +
				(c1.getPrestigePoints() * 2.0) -
				(c1.getCost().values().stream().mapToInt(Integer::intValue).sum() * 0.3);
			double score2 = evaluateCard(c2, aiPlayer) +
				(c2.getPrestigePoints() * 2.0) -
				(c2.getCost().values().stream().mapToInt(Integer::intValue).sum() * 0.3);
			return Double.compare(score2, score1); // Descending.
		});

		return candidates.get(0);
	}

	private Card findBestReserveTarget(GameController controller, Player aiPlayer) { // Big point cards to hold.
		// Reserve high-value cards that opponents might want
		List<Card> candidates = new ArrayList<>();

		for (int level = 3; level >= 1; level--) { // Expensive rows first.
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (card.getPrestigePoints() >= 3) { // Only “big” cards.
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

	private String takeGoalOrientedGems(GameController controller, Player aiPlayer, Noble targetNoble) { // Gems aimed at noble then cards.
		Map<GemType, Integer> gemsToTake = new HashMap<>(); // Build up to 3 singles (must be exactly 3 for this path’s take).

		if (targetNoble != null) { // Prefer colors that nobles care about (via bonus progress — here uses requirement keys with bank take as proxy).
			// Take gems needed for noble
			Map<GemType, Integer> requirement = targetNoble.getRequirement();
			Map<GemType, Integer> bonuses = aiPlayer.getBonuses();

			for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
				int have = bonuses.getOrDefault(req.getKey(), 0);
				int need = req.getValue();
				if (need > have && controller.getBoard().getGemCount(req.getKey()) > 0) { // Short on that “color family” and bank has chip.
					gemsToTake.put(req.getKey(), 1); // Add one of that color to the take set.
					if (gemsToTake.size() >= 3) break; // Cap at three colors for this branch.
				}
			}
		}

		// Fill remaining slots with gems needed for affordable cards
		if (gemsToTake.size() < 3) { // Need a full set of 3 distinct for validate in this method’s first take attempt.
			// Similar logic to MediumAIStrategy
			List<GemType> needed = new ArrayList<>(); // Ordered wish list from unaffordable cards’ costs.
			for (int level = 1; level <= 3; level++) {
				for (Card card : controller.getBoard().getVisibleCards(level)) {
					if (!canAffordCard(card, aiPlayer)) {
						Map<GemType, Integer> cost = card.getCost();
						for (Map.Entry<GemType, Integer> entry : cost.entrySet()) {
							if (entry.getKey() != GemType.GOLD &&
								!gemsToTake.containsKey(entry.getKey()) &&
								controller.getBoard().getGemCount(entry.getKey()) > 0) {
								needed.add(entry.getKey()); // May duplicate; still fills map up to 3 keys.
							}
						}
					}
				}
			}

			for (GemType type : needed) { // Pour into take map until 3 types.
				if (gemsToTake.size() >= 3) break;
				gemsToTake.put(type, 1);
			}
		}

		if (gemsToTake.size() == 3 && controller.takeGems(gemsToTake)) { // Only tries when exactly three distinct colors chosen.
			return "AI (Hard) took goal-oriented gems";
		}

		String fallback = tryAnyLegalGemTake(controller, aiPlayer, "AI (Hard) took goal-oriented gems"); // 2-same or exhaustive 3-diff.
		return fallback != null ? fallback : "AI (Hard) passed turn";
	}

	private Card findBestBlockingCard(GameController controller, Player aiPlayer) { // Deny opponent high-value buys.
		List<Player> all = controller.getPlayers(); // Every seat in turn order.
		List<Player> opponents = new ArrayList<>(); // Everyone except this AI.
		for (Player p : all) {
			if (p != aiPlayer) {
				opponents.add(p);
			}
		}
		if (opponents.isEmpty()) { // Solo test edge case.
			return null;
		}

		double bestThreat = 0.0; // Best combined score seen.
		Card best = null; // Card to block.
		int winningPoints = controller.getConfig().getWinningPoints(); // Win threshold for instant-win detection.

		for (int level = 1; level <= 3; level++) { // Each visible card could be blocked.
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				double threat = 0.0; // Max danger this card poses to any one opponent.
				for (Player opp : opponents) {
					if (!canAffordCard(card, opp)) { // Opponent cannot buy — no need to block.
						continue;
					}
					double thisOppThreat = card.getPrestigePoints() * 3.0 + evaluateCard(card, opp); // How much they want it.
					// Huge danger if opponent can win immediately by buying this card.
					if (opp.getPrestigePoints() + card.getPrestigePoints() >= winningPoints) {
						thisOppThreat += 100.0; // Massive priority.
					}
					if (thisOppThreat > threat) { // Worst opponent for this card.
						threat = thisOppThreat;
					}
				}
				if (threat <= 0.0) { // No opponent can buy it.
					continue;
				}
				// Slightly prefer blocking cards that still help us.
				double myValue = evaluateCard(card, aiPlayer); // Our interest in owning it.
				double combined = threat + (myValue * 0.35); // Blend threat and selfish value.
				if (combined > bestThreat) {
					bestThreat = combined;
					best = card;
				}
			}
		}

		return bestThreat > 8.0 ? best : null; // Ignore weak threats below threshold.
	}
}
