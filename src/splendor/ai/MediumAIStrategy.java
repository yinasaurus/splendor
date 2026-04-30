package splendor.ai; // AI package.

import java.util.ArrayList; // Lists for card candidates and priority gem types.
import java.util.Comparator; // Sort cards by score and gems by need.
import java.util.HashMap; // Build gem take map and “needed” counts.
import java.util.List; // Typed lists.
import java.util.Map; // Cost maps and needed-gem tallies.

import splendor.controller.GameController; // Game actions.
import splendor.model.Card; // Development cards.
import splendor.model.GemType; // Colors for costs and takes.
import splendor.model.Player; // AI state.

/**
 * Medium AI strategy - evaluates cards and makes strategic decisions.
 */
public class MediumAIStrategy implements AIStrategy { // Stronger heuristics than Easy.
	@Override
	public String makeMove(GameController controller, Player aiPlayer) { // One turn for Medium AI.
		// Find best affordable card
		Card bestCard = findBestAffordableCard(controller, aiPlayer); // Highest evaluateCard among affordable.

		if (bestCard != null) { // Something is buyable.
			if (controller.purchaseCard(bestCard)) { // Try that single best pick first.
				return "AI (Medium) purchased: " + bestCard.toString(); // Success message.
			}
		}

		// Try to reserve a high-value card if we have space
		if (aiPlayer.getReservedCards().size() < 3) { // Splendor max 3 reserved.
			Card cardToReserve = findBestCardToReserve(controller, aiPlayer); // Visible card with prestige > 0, best points.
			if (cardToReserve != null) { // Found a target to reserve.
				for (int level = 1; level <= 3; level++) { // Find which row contains that Card instance.
					List<Card> visible = controller.getBoard().getVisibleCards(level);
					int index = visible.indexOf(cardToReserve); // Slot index or -1.
					if (index >= 0 && controller.reserveCard(level, index)) { // Reserve face-up + gold if possible.
						return "AI (Medium) reserved: " + cardToReserve.toString();
					}
				}
			}
		}

		// Take gems strategically
		return takeStrategicGems(controller, aiPlayer); // Heuristic gem take + shared legal fallback + pass string.
	}

	@Override
	public String getStrategyName() { // AIStrategy API.
		return "Medium";
	}

	private Card findBestAffordableCard(GameController controller, Player aiPlayer) { // Chooses one buy target.
		List<Card> candidates = new ArrayList<>(); // All cards AI can afford.

		// Check visible cards
		for (int level = 1; level <= 3; level++) { // Every level.
			for (Card card : controller.getBoard().getVisibleCards(level)) { // Every visible slot.
				if (canAffordCard(card, aiPlayer)) { // Payment possible with hand + bonuses.
					candidates.add(card);
				}
			}
		}

		// Check reserved cards
		for (Card card : aiPlayer.getReservedCards()) { // Hand reserves.
			if (canAffordCard(card, aiPlayer)) {
				candidates.add(card);
			}
		}

		if (candidates.isEmpty()) { // Cannot buy anything.
			return null; // Caller will try reserve / gems.
		}

		// Sort by value and return best
		candidates.sort(Comparator.comparingDouble(card -> -evaluateCard(card, aiPlayer))); // Descending heuristic score.
		return candidates.get(0); // Best card after sort.
	}

	private Card findBestCardToReserve(GameController controller, Player aiPlayer) { // Pick something worth holding.
		List<Card> candidates = new ArrayList<>(); // Only prestige > 0 visible cards.

		// Prefer level 3 cards with high prestige
		for (int level = 3; level >= 1; level--) { // High level first.
			for (Card card : controller.getBoard().getVisibleCards(level)) { // Each visible.
				if (card.getPrestigePoints() > 0) { // Ignore pure 0-point engine cards for reserve target.
					candidates.add(card);
				}
			}
		}

		if (candidates.isEmpty()) { // No prestige on board (unusual) — no reserve candidate here.
			return null;
		}

		candidates.sort(Comparator.comparingInt(Card::getPrestigePoints).reversed()); // Highest points first.
		return candidates.get(0); // Top prestige among candidates.
	}

	private String takeStrategicGems(GameController controller, Player aiPlayer) { // Prefer gems that unblock buys.
		// Analyze which gems we need most
		Map<GemType, Integer> needed = new HashMap<>(); // Color → total shortfall weight.

		// Check what gems we need for affordable cards
		for (int level = 1; level <= 3; level++) { // Look at cards we cannot yet afford.
			for (Card card : controller.getBoard().getVisibleCards(level)) {
				if (!canAffordCard(card, aiPlayer)) { // Only study unaffordable cards for “what to collect”.
					Map<GemType, Integer> cost = card.getCost(); // Printed cost.
					Map<GemType, Integer> bonuses = aiPlayer.getBonuses(); // Discounts.

					for (Map.Entry<GemType, Integer> entry : cost.entrySet()) { // Each color in cost.
						if (entry.getKey() != GemType.GOLD) { // Gold is wild, not a bank take target.
							int required = entry.getValue(); // Pips needed after bonuses conceptually.
							int have = aiPlayer.getGems().getOrDefault(entry.getKey(), 0) +
								bonuses.getOrDefault(entry.getKey(), 0); // Tokens + permanent discount chips.
							if (required > have) { // Shortfall for this color on this card.
								needed.put(entry.getKey(),
									needed.getOrDefault(entry.getKey(), 0) + (required - have)); // Accumulate urgency.
							}
						}
					}
				}
			}
		}

		// Take gems we need most
		if (!needed.isEmpty()) { // We computed some priorities.
			List<GemType> priorityGems = new ArrayList<>(needed.keySet()); // Distinct colors we want.
			priorityGems.sort(Comparator.comparingInt(needed::get).reversed()); // Largest shortfall first.

			Map<GemType, Integer> gemsToTake = new HashMap<>(); // Up to 3 distinct colors, 1 each.
			for (int i = 0; i < Math.min(3, priorityGems.size()); i++) { // At most three entries.
				GemType type = priorityGems.get(i); // Next most needed color.
				if (controller.getBoard().getGemCount(type) > 0) { // Bank has that color.
					gemsToTake.put(type, 1); // Take one of it.
				}
			}

			if (gemsToTake.size() == 3 && controller.takeGems(gemsToTake)) { // Splendor: exactly 3 different when taking 3 singles.
				return "AI (Medium) took strategic gems";
			}
		}

		// Fallback: take any available gems
		List<GemType> available = new ArrayList<>(); // Bank colors we could touch.
		for (GemType type : GemType.values()) { // All enum values.
			if (type != GemType.GOLD && // No gold from bank.
				controller.getBoard().getGemCount(type) > 0 && // Supply exists.
				aiPlayer.getTotalGemCount() < 10) { // Hand not already full (take still validated in controller).
				available.add(type);
			}
		}

		if (available.size() >= 3) { // Need three colors for this simple fallback.
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			for (int i = 0; i < 3; i++) { // First three available colors in enum order within list.
				gemsToTake.put(available.get(i), 1);
			}
			if (controller.takeGems(gemsToTake)) { // Try generic 3-diff take.
				return "AI (Medium) took gems";
			}
		}

		String fallback = tryAnyLegalGemTake(controller, aiPlayer, "AI (Medium) took gems"); // Includes 2-same and all 3-diff combos.
		return fallback != null ? fallback : "AI (Medium) passed turn"; // True pass if nothing legal.
	}
}
