package splendor.main; // JVM entry point for console Splendor.

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import java.util.HashMap;
import java.util.Map;

import splendor.ai.AIPlayer;
import splendor.ai.EasyAIStrategy;
import splendor.ai.MediumAIStrategy;
import splendor.ai.HardAIStrategy;
import splendor.controller.GameController;
import splendor.model.Player;
import splendor.ui.ConsoleUI;

/**
 * Main class for the Splendor card game application.
 */
public class SplendorGame { // Console-only launcher (web uses different main).
	private static final Scanner scanner = new Scanner(System.in); // Shared stdin reader for whole session.

	/**
	 * Main entry point for the application.
	 *
	 * @param args command line arguments
	 */
	public static void main(String[] args) { // java splendor.main.SplendorGame
		System.out.println("Welcome to Splendor!");
		System.out.println("===================\n");

		// Optional tutorial before starting the game
		System.out.print("Would you like to see a short tutorial? (y/n): ");
		String tutorialChoice = scanner.nextLine().trim().toLowerCase();
		if (tutorialChoice.startsWith("y")) {
			showTutorial();
			System.out.println();
		}

		// Get number of players
		int numPlayers = getNumberOfPlayers();

		// Get player names and types
		List<String> playerNames = new ArrayList<>(); // Parallel index with playerTypes.
		List<Boolean> playerTypes = new ArrayList<>(); // true = human.
		Map<Integer, AIPlayer> aiPlayers = new HashMap<>(); // Key = setup order index (see shuffle caveat).

		for (int i = 0; i < numPlayers; i++) {
			System.out.print("Enter name for Player " + (i + 1) + ": ");
			String name = scanner.nextLine().trim();
			if (name.isEmpty()) {
				name = "Player " + (i + 1);
			}
			playerNames.add(name);

			System.out.print("Is " + name + " a human player? (y/n): ");
			String response = scanner.nextLine().trim().toLowerCase();
			boolean isHuman = response.startsWith("y");
			playerTypes.add(isHuman);

			if (!isHuman) {
				// Get AI difficulty
				System.out.print("AI difficulty for " + name + " (easy/medium/hard): ");
				String difficulty = scanner.nextLine().trim().toLowerCase();
				if (difficulty.isEmpty()) {
					difficulty = "medium"; // Default if user just presses Enter
				}

				AIPlayer aiPlayer;
				switch (difficulty) {
					case "easy":
						aiPlayer = new AIPlayer(new EasyAIStrategy());
						break;
					case "hard":
						aiPlayer = new AIPlayer(new HardAIStrategy());
						break;
					default:
						aiPlayer = new AIPlayer(new MediumAIStrategy());
						break;
				}
				aiPlayers.put(i, aiPlayer); // Maps registration slot → AI (shuffle can desync from current player index).
			}
		}

		// Create game controller
		GameController controller = new GameController(numPlayers, playerNames, playerTypes); // Shuffles players inside.
		ConsoleUI ui = new ConsoleUI(controller);

		// Main game loop
		while (!controller.isGameOver()) {
			ui.displayGameState();

			if (controller.getCurrentPlayer().isHuman()) {
				// Human player's turn
				boolean turnComplete = false;
				while (!turnComplete) {
					turnComplete = ui.handleHumanTurn(); // Retry until valid action completes.
				}
			} else {
				// AI player's turn
				Player currentPlayer = controller.getCurrentPlayer();
				AIPlayer aiPlayer = aiPlayers.get(controller.getPlayers().indexOf(currentPlayer)); // Lookup by shuffled index.
				if (aiPlayer == null) {
					aiPlayer = new AIPlayer(); // Default medium
				}

				System.out.println("\n>>> " + currentPlayer.getName() + "'s Turn (AI - " +
					aiPlayer.getStrategyName() + ") <<<");
				String aiAction = aiPlayer.makeMove(controller);
				System.out.println(aiAction);

				// Small delay for readability
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					// Ignore
				}
			}

			// Move to next player
			controller.nextTurn();
		}

		// Display winner and statistics
		ui.displayGameState();
		ui.displayWinner();
		ui.displayStatistics();

		scanner.close();
	}

	/**
	 * Shows a short text-based tutorial explaining how to play.
	 */
	private static void showTutorial() {
		System.out.println("\n=== TUTORIAL: How to Play Splendor ===\n");
		System.out.println("Goal:");
		System.out.println("- Be the first player to reach enough prestige points (default 15) to win.\n");

		System.out.println("On Your Turn (you choose ONE action):");
		System.out.println("1) Take Gems");
		System.out.println("   - Option A: Take 3 different colored gems (e.g. R E S).");
		System.out.println("   - Option B: Take 2 of the same color (only if there are 4+ of that color left).");
		System.out.println("   - You CANNOT take GOLD directly and you cannot exceed 10 total gems.\n");

		System.out.println("2) Reserve a Card");
		System.out.println("   - Reserve a face-up card from the board.");
		System.out.println("   - You may have at most 3 reserved cards.");
		System.out.println("   - When you reserve, you gain 1 GOLD (wild) gem if any are available.\n");

		System.out.println("3) Purchase a Card");
		System.out.println("   - Buy a face-up card OR one of your reserved cards.");
		System.out.println("   - Pay its cost using gems in hand + permanent bonuses from cards you already own.");
		System.out.println("   - GOLD gems can pay for any color.\n");

		System.out.println("Card Levels:");
		System.out.println("- Level 1: Cheap engine cards. Usually 0 points but give permanent color bonuses (discounts).");
		System.out.println("- Level 2: Mid-cost cards. Often 1–2 prestige. Good mid‑game value.");
		System.out.println("- Level 3: Expensive cards with lots of prestige. Use your engine to afford these.\n");

		System.out.println("Bonuses (Discounts):");
		System.out.println("- Every development card has a color (Ruby/Emerald/etc.).");
		System.out.println("- After buying it, you get +1 permanent bonus of that color.");
		System.out.println("- Each bonus reduces the number of gems of that color you must pay for future cards.");
		System.out.println("- Bonuses also count toward noble requirements (nobles care about bonuses, not loose gems).\n");

		System.out.println("Nobles:");
		System.out.println("- Each noble shows color requirements (e.g. 3 Ruby bonuses, 3 Emerald bonuses).");
		System.out.println("- At the END of your turn, if your bonuses meet a noble's requirements,");
		System.out.println("  that noble visits you automatically and gives extra prestige points.");
		System.out.println("- You never pay gems for nobles.\n");

		System.out.println("Winning & Tie-breaker:");
		System.out.println("- When a player reaches the winning points, finish the round so everyone has equal turns.");
		System.out.println("- Winner is the player with the highest prestige.");
		System.out.println("- If tied, the player with FEWER purchased cards wins.\n");

		System.out.println("Useful In-Game Commands:");
		System.out.println("- During your turn, type !help instead of 1–4 to open a rule popup.");
		System.out.println("- Use option 4 (View game state) to see all cards, nobles, and player status.\n");

		System.out.println("Press Enter to start the game...");
		scanner.nextLine();
	}

	/**
	 * Gets the number of players from user input.
	 *
	 * @return the number of players (2-4)
	 */
	private static int getNumberOfPlayers() {
		while (true) { // Until valid integer in range.
			System.out.print("Enter number of players (2-4): ");
			try {
				int num = Integer.parseInt(scanner.nextLine().trim());
				if (num >= 2 && num <= 4) {
					return num;
				} else {
					System.out.println("Please enter a number between 2 and 4.");
				}
			} catch (NumberFormatException e) {
				System.out.println("Invalid input. Please enter a number.");
			}
		}
	}
}
