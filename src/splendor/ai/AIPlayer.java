package splendor.ai; // This compilation unit is part of package splendor.ai (groups related classes).

import splendor.controller.GameController; // Brings in the class that runs game actions and holds state.
import splendor.model.Player; // Brings in the model type for a participant in the game.

/**
 * AI player that uses Strategy pattern for different difficulty levels.
 */
public class AIPlayer { // Public class: other packages can construct and call makeMove on an AI player.
	private final AIStrategy strategy; // The chosen difficulty/behavior; final so it never changes after construction.

	/**
	 * Small delay to make AI turns feel more natural to humans.
	 * Applied on both console and web server flows since both use AIPlayer.makeMove().
	 */
	private static final long THINKING_DELAY_MS = 1000; // Milliseconds to sleep before deciding; static = one value for all instances.

	/**
	 * Constructor for AIPlayer with default medium strategy.
	 */
	public AIPlayer() { // No-arg constructor for callers that do not pick a strategy explicitly.
		this.strategy = new MediumAIStrategy(); // default is medium strategy
	}

	/**
	 * Constructor for AIPlayer with specified strategy.
	 *
	 * @param strategy the AI strategy to use
	 */
	public AIPlayer(AIStrategy strategy) { // Lets callers inject Easy / Medium / Hard (Strategy pattern).
		this.strategy = strategy; // Remember which AIStrategy implementation to delegate to.
	}

	/**
	 * Makes a decision for the AI player's turn.
	 *
	 * @param controller the game controller
	 * @return a string describing the action taken
	 */
	public String makeMove(GameController controller) { // Entry point: perform one turn for whoever is current player.
		Player aiPlayer = controller.getCurrentPlayer(); // The Player object the AI controls this turn (gems, cards, etc.).
		// Simulate thinking so players can follow the game flow.
		// Also prevents "instant" multi-moves in fast console/web loops.
		if (THINKING_DELAY_MS > 0) { // Skip sleeping if delay were ever set to zero (not the case now).
			try { // sleep can throw a checked exception; try/catch is required.
				Thread.sleep(THINKING_DELAY_MS); // Block this thread for THINKING_DELAY_MS milliseconds.
			} catch (InterruptedException ie) { // Another thread interrupted this sleep.
				Thread.currentThread().interrupt(); // Preserve interrupt status so higher-level code can see it.
			}//will show that it happens but will not stop the game halfway
		}
		String result = strategy.makeMove(controller, aiPlayer); // Delegate the real decision to Easy/Medium/Hard logic.
		if (!controller.hasLegalMovesAvailable()) { // If no legal moves remain, the strategy’s message is enough (true pass case).
			return result; // Return whatever the strategy reported (e.g. “passed turn”).
		}
		String repair = strategy.tryAnyLegalGemTake(controller, aiPlayer, // If moves still exist, try a guaranteed legal gem take.
			"AI (" + strategy.getStrategyName() + ") took gems"); // Message logged/shown if gem repair succeeds.
		if (repair != null) { // tryAnyLegalGemTake returns null when no gem take is legal.
			return repair; // Prefer the repair outcome so the turn is not a silent skip.
		}
		if (!controller.hasLegalMovesAvailable()) { // Gem repair may have been unnecessary; re-check after failed repair.
			return result; // Still no moves or repair did nothing meaningful—return original strategy string.
		}
		repair = strategy.tryAnyLegalReserve(controller, aiPlayer, // e.g. hand full of gems: reserve may still be legal.
			"AI (" + strategy.getStrategyName() + ") reserved a card");
		if (repair != null) { // A reserve succeeded.
			return repair;
		}
		if (!controller.hasLegalMovesAvailable()) { // Again, bail early if the game state no longer requires action.
			return result;
		}
		repair = strategy.tryAnyLegalPurchase(controller, aiPlayer, // Last resort: try every affordable visible/reserved card.
			"AI (" + strategy.getStrategyName() + ") purchased a card");
		if (repair != null) { // A purchase succeeded.
			return repair;
		}
		return result; // All repairs failed; return the strategy’s original string (rare if rules are consistent).
	}

	/**
	 * Gets the strategy name.
	 *
	 * @return the strategy name
	 */
	public String getStrategyName() { // Exposes “Easy”, “Medium”, or “Hard” for UI or logs.
		return strategy.getStrategyName(); // Forward to the concrete strategy implementation.
	}
}
