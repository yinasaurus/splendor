package splendor.test;

import java.util.ArrayList;
import java.util.List;

import splendor.ai.AIPlayer;
import splendor.ai.EasyAIStrategy;
import splendor.ai.MediumAIStrategy;
import splendor.controller.GameController;
import splendor.model.Player;
import splendor.ui.ConsoleUI;

/**
 * Automated test that simulates a game without user input.
 * Useful for testing and demonstration purposes.
 */
public class AutomatedGameTest {
	
	/**
	 * Main method to run automated game test.
	 *
	 * @param args command line arguments (optional: number of players)
	 */
	public static void main(String[] args) {
		System.out.println("========================================");
		System.out.println("  AUTOMATED SPLENDOR GAME TEST");
		System.out.println("========================================\n");
		
		// Get number of players from args or default to 2
		int numPlayers = 2;
		if (args.length > 0) {
			try {
				numPlayers = Integer.parseInt(args[0]);
				if (numPlayers < 2 || numPlayers > 4) {
					numPlayers = 2;
				}
			} catch (NumberFormatException e) {
				numPlayers = 2;
			}
		}
		
		System.out.println("Starting automated game with " + numPlayers + " players...\n");
		
		// Create players
		List<String> playerNames = new ArrayList<>();
		List<Boolean> playerTypes = new ArrayList<>();
		List<AIPlayer> aiPlayers = new ArrayList<>();
		
		for (int i = 0; i < numPlayers; i++) {
			playerNames.add("AI Player " + (i + 1));
			playerTypes.add(false); // All AI players
			
			// Mix of AI difficulties
			if (i == 0) {
				aiPlayers.add(new AIPlayer(new EasyAIStrategy()));
			} else if (i == 1) {
				aiPlayers.add(new AIPlayer(new MediumAIStrategy()));
			} else {
				aiPlayers.add(new AIPlayer(new MediumAIStrategy()));
			}
		}
		
		// Create game controller
		GameController controller = new GameController(numPlayers, playerNames, playerTypes);
		ConsoleUI ui = new ConsoleUI(controller);
		
		System.out.println("Game initialized. Starting automated play...\n");
		System.out.println("Players:");
		for (int i = 0; i < numPlayers; i++) {
			System.out.println("  - " + playerNames.get(i) + " (" + aiPlayers.get(i).getStrategyName() + " AI)");
		}
		System.out.println();
		
		// Main game loop
		int turnCount = 0;
		int maxTurns = 100; // Safety limit to prevent infinite loops
		
		while (!controller.isGameOver() && turnCount < maxTurns) {
			turnCount++;
			Player currentPlayer = controller.getCurrentPlayer();
			int playerIndex = controller.getPlayers().indexOf(currentPlayer);
			AIPlayer aiPlayer = aiPlayers.get(playerIndex);
			
			System.out.println("--- Turn " + turnCount + " ---");
			System.out.println(">>> " + currentPlayer.getName() + "'s Turn (AI - " + 
				aiPlayer.getStrategyName() + ") <<<");
			
			// Display current state (every 5 turns to reduce output)
			if (turnCount % 5 == 1 || turnCount <= 3) {
				ui.displayGameState();
			} else {
				// Brief status
				System.out.println("Current Status:");
				for (Player p : controller.getPlayers()) {
					System.out.println("  " + p.getName() + ": " + 
						p.getPrestigePoints() + " prestige, " + 
						p.getTotalGemCount() + " gems, " + 
						p.getPurchasedCards().size() + " cards");
				}
				System.out.println();
			}
			
			// AI makes move
			String aiAction = aiPlayer.makeMove(controller);
			System.out.println("Action: " + aiAction);
			System.out.println();
			
			// Small delay for readability
			try {
				Thread.sleep(500); // 0.5 second delay
			} catch (InterruptedException e) {
				// Ignore
			}
			
			// Move to next player
			controller.nextTurn();
		}
		
		// Display final state
		System.out.println("\n" + repeatString("=", 60));
		System.out.println("FINAL GAME STATE");
		System.out.println(repeatString("=", 60));
		ui.displayGameState();
		
		// Display winner
		ui.displayWinner();
		
		// Display statistics
		ui.displayStatistics();
		
		System.out.println("\n" + repeatString("=", 40));
		System.out.println("  GAME COMPLETE");
		System.out.println("  Total Turns: " + turnCount);
		System.out.println(repeatString("=", 40));
	}
	
	/**
	 * Helper method to repeat a string (Java 8 compatible).
	 *
	 * @param str the string to repeat
	 * @param count the number of times to repeat
	 * @return the repeated string
	 */
	private static String repeatString(String str, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(str);
		}
		return sb.toString();
	}
}
