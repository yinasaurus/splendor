package splendor.ai;

import splendor.controller.GameController;
import splendor.model.Player;

/**
 * AI player that uses Strategy pattern for different difficulty levels.
 */
public class AIPlayer {
	private final AIStrategy strategy;

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
