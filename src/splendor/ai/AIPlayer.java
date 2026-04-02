package splendor.ai;

import splendor.controller.GameController;
import splendor.model.Player;

/**
 * AI player that uses Strategy pattern for different difficulty levels.
 */
public class AIPlayer {
	private final AIStrategy strategy;

	/**
	 * Small delay to make AI turns feel more natural to humans.
	 * Applied on both console and web server flows since both use AIPlayer.makeMove().
	 */
	private static final long THINKING_DELAY_MS = 2000;

	/**
	 * Constructor for AIPlayer with default medium strategy.
	 */
	public AIPlayer() {
		this.strategy = new MediumAIStrategy();
	}

	/**
	 * Constructor for AIPlayer with specified strategy.
	 *
	 * @param strategy the AI strategy to use
	 */
	public AIPlayer(AIStrategy strategy) {
		this.strategy = strategy;
	}

	/**
	 * Makes a decision for the AI player's turn.
	 *
	 * @param controller the game controller
	 * @return a string describing the action taken
	 */
	public String makeMove(GameController controller) {
		Player aiPlayer = controller.getCurrentPlayer();
		// Simulate thinking so players can follow the game flow.
		// Also prevents "instant" multi-moves in fast console/web loops.
		if (THINKING_DELAY_MS > 0) {
			try {
				Thread.sleep(THINKING_DELAY_MS);
			} catch (InterruptedException ie) {
				Thread.currentThread().interrupt();
			}
		}
		return strategy.makeMove(controller, aiPlayer);
	}

	/**
	 * Gets the strategy name.
	 *
	 * @return the strategy name
	 */
	public String getStrategyName() {
		return strategy.getStrategyName();
	}
}
