package splendor.ui; // Terminal menus and ANSI-colored state dump.

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GemType;
import splendor.model.Noble;
import splendor.model.Player;
import splendor.stats.GameStatistics;

/**
 * Console-based user interface for the Splendor game.
 */
public class ConsoleUI { // Reads stdin; calls GameController for mutations.
	// ANSI color codes for nicer console output (works in most terminals, including WSL)
	private static final String RESET = "\033[0m"; // Reset formatting.
	private static final String BOLD = "\033[1m"; // Bold text.
	private static final String FG_GREEN = "\033[32m"; // Current player marker / success.
	private static final String FG_RED = "\033[31m"; // Errors.
	private static final String FG_YELLOW = "\033[33m"; // Card indices / AI label.
	private static final String FG_CYAN = "\033[36m"; // Headers / human label.
	private static final String FG_MAGENTA = "\033[35m"; // Nobles.
	private static final String FG_BLUE = "\033[34m"; // Separator lines.

	private final Scanner scanner; // Keyboard input.
	private final GameController controller; // Game brain.

	/**
	 * Constructor for ConsoleUI.
	 *
	 * @param controller the game controller
	 */
	public ConsoleUI(GameController controller) {
		this.scanner = new Scanner(System.in);
		this.controller = controller;
	}

	/**
	 * Displays the current game state.
	 */
	public void displayGameState() { // Full snapshot: bank, rows, nobles, all players.
		System.out.println("\n" + FG_CYAN + repeatString("=", 60) + RESET);
		System.out.println(BOLD + "CURRENT GAME STATE" + RESET);
		System.out.println(FG_CYAN + repeatString("=", 60) + RESET);

		// Display board gems
		System.out.println("\n" + BOLD + "Available Gems on Board:" + RESET);
		for (GemType type : GemType.values()) {
			int count = controller.getBoard().getGemCount(type);
			if (count > 0) {
				String colorCode = getGemColor(type);
				System.out.print("  " + colorCode + type.getAbbreviation() + RESET + ": " + count + "  ");
			}
		}
		System.out.println();

		// Display visible cards
		for (int level = 1; level <= 3; level++) {
			System.out.println("\n" + BOLD + "Level " + level + " Cards:" + RESET);
			List<Card> cards = controller.getBoard().getVisibleCards(level);
			for (int i = 0; i < cards.size(); i++) {
				System.out.println("  " + FG_YELLOW + "[" + i + "]" + RESET + " " + cards.get(i));
			}
		}

		// Display nobles
		System.out.println("\n" + BOLD + "Available Nobles:" + RESET);
		for (Noble noble : controller.getBoard().getAvailableNobles()) {
			System.out.println("  " + FG_MAGENTA + noble + RESET);
		}

		// Display all players
		System.out.println("\n" + FG_BLUE + repeatString("-", 60) + RESET);
		for (Player player : controller.getPlayers()) {
			displayPlayerInfo(player);
		}
		System.out.println(FG_CYAN + repeatString("=", 60) + RESET + "\n");
	}

	/**
	 * Displays information about a player.
	 *
	 * @param player the player to display
	 */
	private void displayPlayerInfo(Player player) {
		boolean isCurrent = player == controller.getCurrentPlayer(); // Highlight active seat.
		String marker = isCurrent ? (FG_GREEN + ">>> " + RESET) : "    ";

		System.out.println(marker + BOLD + player.getName() + RESET +
			(player.isHuman() ? FG_CYAN + " (Human)" + RESET : FG_YELLOW + " (AI)" + RESET));
		System.out.println("    Prestige Points: " + BOLD + player.getPrestigePoints() + RESET);

		System.out.print("    Gems: ");
		for (GemType type : GemType.values()) {
			int count = player.getGems().getOrDefault(type, 0); // Copy map each call—OK for console.
			if (count > 0) {
				String colorCode = getGemColor(type);
				System.out.print(colorCode + type.getAbbreviation() + RESET + ":" + count + " ");
			}
		}
		System.out.println();

		System.out.print("    Bonuses: ");
		for (GemType type : GemType.values()) {
			if (type != GemType.GOLD) { // Bonuses only on five colors.
				int count = player.getBonuses().getOrDefault(type, 0);
				if (count > 0) {
					String colorCode = getGemColor(type);
					System.out.print(colorCode + type.getAbbreviation() + RESET + ":" + count + " ");
				}
			}
		}
		System.out.println();

		if (!player.getReservedCards().isEmpty()) {
			System.out.println("    Reserved Cards: " + FG_YELLOW + player.getReservedCards().size() + RESET);
		}

		if (player.getVisitedNoble() != null) {
			System.out.println("    Visited Noble: " + FG_MAGENTA + player.getVisitedNoble().getName() + RESET);
		}
	}

	/**
	 * Handles a human player's turn.
	 *
	 * @return true if the turn was completed successfully
	 */
	public boolean handleHumanTurn() {
		Player player = controller.getCurrentPlayer();

		System.out.println("\n" + FG_GREEN + ">>> " + player.getName() + "'s Turn <<<" + RESET);
		System.out.println(BOLD + "What would you like to do?" + RESET);
		System.out.println("1. " + FG_CYAN + "Take gems" + RESET);
		System.out.println("2. " + FG_CYAN + "Reserve a card" + RESET);
		System.out.println("3. " + FG_CYAN + "Purchase a card" + RESET);
		System.out.println("4. " + FG_CYAN + "View game state" + RESET);
		System.out.println(FG_YELLOW + "Type !help at any time to view the rule summary." + RESET);
		System.out.print(BOLD + "Enter choice (1-4 or !help): " + RESET);

		String choice = scanner.nextLine().trim();

		// Show help/rules if requested
		if ("!help".equalsIgnoreCase(choice) || "help".equalsIgnoreCase(choice)) {
			displayHelp();
			// After showing help, re-prompt for action without ending the turn
			return handleHumanTurn();
		}

		switch (choice) {
			case "1":
				return handleTakeGems();
			case "2":
				return handleReserveCard();
			case "3":
				return handlePurchaseCard();
			case "4":
				displayGameState();
				return handleHumanTurn(); // Recursive call to get action
			default:
				System.out.println(FG_RED + "Invalid choice. Please try again." + RESET);
				return handleHumanTurn();
		}
	}

	/**
	 * Displays a quick rule / help summary for the player.
	 */
	private void displayHelp() {
		// Simulate a popup by clearing the screen first (works in most terminals, including WSL)
		clearScreen();

		System.out.println("\n" + FG_CYAN + repeatString("=", 60) + RESET);
		System.out.println(BOLD + "SPLENDOR - QUICK RULES / HELP" + RESET);
		System.out.println(FG_CYAN + repeatString("=", 60) + RESET);
		System.out.println("\n" + BOLD + "Goal:" + RESET);
		System.out.println("- Be the first to reach at least " + controller.getConfig().getWinningPoints() + " prestige points.");
		System.out.println("\n" + BOLD + "On Your Turn (choose ONE action):" + RESET);
		System.out.println("1) Take Gems");
		System.out.println("   - Option A: Take 3 different colored gems (e.g., R E S).");
		System.out.println("   - Option B: Take 2 of the same color (only if 4+ of that color are on the board).");
		System.out.println("   - You cannot take GOLD directly and you cannot exceed 10 total gems.");
		System.out.println("\n2) Reserve a Card");
		System.out.println("   - Reserve a face-up card from the board.");
		System.out.println("   - Max 3 reserved cards.");
		System.out.println("   - Gain 1 GOLD gem (wild) if available.");
		System.out.println("\n3) Purchase a Card");
		System.out.println("   - Buy a face-up card OR one of your reserved cards.");
		System.out.println("   - Pay cost using gems in hand + permanent bonuses from cards you own.");
		System.out.println("   - GOLD gems can substitute any color.");
		System.out.println("\n" + BOLD + "Card Bonuses (\"discounts\"):" + RESET);
		System.out.println("- Every development card you buy has a color shown at the top (e.g. Ruby).");
		System.out.println("- After you buy it, that card gives you +1 permanent bonus of that color.");
		System.out.println("- Each bonus acts like a built‑in gem discount for ALL future cards of that color.");
		System.out.println("- Bonuses also count towards noble requirements (they only look at bonuses, not loose gems).");
		System.out.println("\n" + BOLD + "Card Types on the Board:" + RESET);
		System.out.println("- Level 1 cards: Cheap, usually 0 prestige. Good for building early discounts (engine).");
		System.out.println("- Level 2 cards: Medium cost, often give 1–2 prestige. Mid‑game power cards.");
		System.out.println("- Level 3 cards: Expensive, give a lot of prestige. Use your engine to afford these.");
		System.out.println("- Nobles: Free extra prestige if your PERMANENT bonuses meet their color requirements.");
		System.out.println("  (You do NOT pay gems for nobles; you just need enough bonus cards.)");
		System.out.println("\n" + BOLD + "Nobles:" + RESET);
		System.out.println("- At end of your turn, if your permanent bonuses meet a noble's requirements,");
		System.out.println("  that noble visits you automatically and grants extra prestige.");
		System.out.println("\n" + BOLD + "Winning & Tie-breaker:" + RESET);
		System.out.println("- When any player reaches the winning points, finish the round.");
		System.out.println("- Highest prestige wins; if tied, the player with FEWER purchased cards wins.");
		System.out.println("\n" + BOLD + "Tips:" + RESET);
		System.out.println("- Buy cheap cards early to build discounts.");
		System.out.println("- Watch nobles – building towards them can be very strong.");
		System.out.println(FG_CYAN + repeatString("=", 60) + RESET);
		System.out.println("\n" + FG_YELLOW + "Press Enter to return to your turn..." + RESET);
		scanner.nextLine();

		// Clear again to return to a clean turn screen
		clearScreen();
	}

	/**
	 * Clears the console screen (best-effort, works in most ANSI-capable terminals).
	 */
	private void clearScreen() {
		System.out.print("\033[H\033[2J"); // ANSI home + clear.
		System.out.flush();
	}

	/**
	 * Gets a color code for a given gem type.
	 *
	 * @param type the gem type
	 * @return ANSI color code string
	 */
	private String getGemColor(GemType type) {
		switch (type) {
			case RUBY:
				return FG_RED;
			case EMERALD:
				return FG_GREEN;
			case SAPPHIRE:
				return FG_BLUE;
			case DIAMOND:
				return FG_CYAN;
			case ONYX:
				return FG_MAGENTA;
			case GOLD:
				return FG_YELLOW;
			default:
				return "";
		}
	}

	/**
	 * Handles taking gems action.
	 *
	 * @return true if successful
	 */
	private boolean handleTakeGems() {
		System.out.println("\n" + BOLD + "Take Gems:" + RESET);
		System.out.println("Option 1: Take 3 different colored gems (enter 3 gem types)");
		System.out.println("Option 2: Take 2 gems of the same color (enter 1 gem type twice)");
		System.out.println("Gem types: R=Ruby, E=Emerald, S=Sapphire, D=Diamond, O=Onyx");
		System.out.print("Enter gem types (space-separated): ");

		String input = scanner.nextLine().trim().toUpperCase();
		String[] parts = input.split("\\s+"); // Split on whitespace.

		Map<GemType, Integer> gemsToTake = new HashMap<>();

		for (String part : parts) {
			GemType type = GemType.fromAbbreviation(part);
			if (type == null || type == GemType.GOLD) {
				System.out.println(FG_RED + "Invalid gem type: " + part + RESET);
				return false;
			}
			gemsToTake.put(type, gemsToTake.getOrDefault(type, 0) + 1); // Count duplicates for “2 same”.
		}

		if (controller.takeGems(gemsToTake)) {
			System.out.println(FG_GREEN + "Successfully took gems!" + RESET);
			return true;
		} else {
			System.out.println(FG_RED + "Invalid action. Please try again." + RESET);
			return false;
		}
	}

	/**
	 * Handles reserving a card action.
	 *
	 * @return true if successful
	 */
	private boolean handleReserveCard() {
		System.out.println("\n" + BOLD + "Reserve a Card:" + RESET);
		System.out.print(BOLD + "Enter card level (1, 2, or 3): " + RESET);

		try {
			int level = Integer.parseInt(scanner.nextLine().trim());
			if (level < 1 || level > 3) {
				System.out.println(FG_RED + "Invalid level." + RESET);
				return false;
			}

			List<Card> cards = controller.getBoard().getVisibleCards(level);
			if (cards.isEmpty()) {
				System.out.println(FG_YELLOW + "No cards available at level " + level + RESET);
				return false;
			}

			System.out.println(BOLD + "Available cards at level " + level + ":" + RESET);
			for (int i = 0; i < cards.size(); i++) {
				System.out.println("  [" + i + "] " + cards.get(i));
			}

			System.out.print(BOLD + "Enter card index: " + RESET);
			int index = Integer.parseInt(scanner.nextLine().trim());

			if (controller.reserveCard(level, index)) {
				System.out.println(FG_GREEN + "Successfully reserved card!" + RESET);
				return true;
			} else {
				System.out.println(FG_RED + "Invalid action. Please try again." + RESET);
				return false;
			}
		} catch (NumberFormatException e) {
			System.out.println(FG_RED + "Invalid input." + RESET);
			return false;
		}
	}

	/**
	 * Handles purchasing a card action.
	 *
	 * @return true if successful
	 */
	private boolean handlePurchaseCard() {
		System.out.println("\n" + BOLD + "Purchase a Card:" + RESET);
		System.out.println("1. Purchase from visible cards");
		System.out.println("2. Purchase from reserved cards");
		System.out.print(BOLD + "Enter choice (1 or 2): " + RESET);

		String choice = scanner.nextLine().trim();

		if (choice.equals("1")) {
			return handlePurchaseVisibleCard();
		} else if (choice.equals("2")) {
			return handlePurchaseReservedCard();
		} else {
			System.out.println(FG_RED + "Invalid choice." + RESET);
			return false;
		}
	}

	/**
	 * Handles purchasing a visible card.
	 *
	 * @return true if successful
	 */
	private boolean handlePurchaseVisibleCard() {
		System.out.print(BOLD + "Enter card level (1, 2, or 3): " + RESET);

		try {
			int level = Integer.parseInt(scanner.nextLine().trim());
			if (level < 1 || level > 3) {
				System.out.println(FG_RED + "Invalid level." + RESET);
				return false;
			}

			List<Card> cards = controller.getBoard().getVisibleCards(level);
			if (cards.isEmpty()) {
				System.out.println(FG_YELLOW + "No cards available at level " + level + RESET);
				return false;
			}

			System.out.println(BOLD + "Available cards at level " + level + ":" + RESET);
			for (int i = 0; i < cards.size(); i++) {
				System.out.println("  [" + i + "] " + cards.get(i));
			}

			System.out.print(BOLD + "Enter card index: " + RESET);
			int index = Integer.parseInt(scanner.nextLine().trim());

			if (index < 0 || index >= cards.size()) {
				System.out.println(FG_RED + "Invalid index." + RESET);
				return false;
			}

			Card card = cards.get(index);
			if (controller.purchaseCard(card)) {
				System.out.println(FG_GREEN + "Successfully purchased card!" + RESET);
				return true;
			} else {
				System.out.println(FG_RED + "Cannot purchase this card. Insufficient resources." + RESET);
				return false;
			}
		} catch (NumberFormatException e) {
			System.out.println(FG_RED + "Invalid input." + RESET);
			return false;
		}
	}

	/**
	 * Handles purchasing a reserved card.
	 *
	 * @return true if successful
	 */
	private boolean handlePurchaseReservedCard() {
		Player player = controller.getCurrentPlayer();
		List<Card> reserved = player.getReservedCards();

		if (reserved.isEmpty()) {
			System.out.println(FG_YELLOW + "You have no reserved cards." + RESET);
			return false;
		}

		System.out.println(BOLD + "Your reserved cards:" + RESET);
		for (int i = 0; i < reserved.size(); i++) {
			System.out.println("  [" + i + "] " + reserved.get(i));
		}

		System.out.print(BOLD + "Enter card index: " + RESET);

		try {
			int index = Integer.parseInt(scanner.nextLine().trim());
			if (index < 0 || index >= reserved.size()) {
				System.out.println(FG_RED + "Invalid index." + RESET);
				return false;
			}

			Card card = reserved.get(index);
			if (controller.purchaseCard(card)) {
				System.out.println(FG_GREEN + "Successfully purchased reserved card!" + RESET);
				return true;
			} else {
				System.out.println(FG_RED + "Cannot purchase this card. Insufficient resources." + RESET);
				return false;
			}
		} catch (NumberFormatException e) {
			System.out.println(FG_RED + "Invalid input." + RESET);
			return false;
		}
	}

	/**
	 * Displays the winner announcement.
	 */
	public void displayWinner() {
		Player winner = controller.getWinner();
		if (winner != null) {
			System.out.println("\n" + FG_CYAN + repeatString("=", 60) + RESET);
			System.out.println(BOLD + "GAME OVER!" + RESET);
			System.out.println("Winner: " + FG_GREEN + winner.getName() + RESET +
				" with " + BOLD + winner.getPrestigePoints() + RESET + " prestige points!");
			System.out.println(FG_CYAN + repeatString("=", 60) + RESET);
		}
	}

	/**
	 * Displays game statistics.
	 */
	public void displayStatistics() {
		System.out.println("\n" + FG_CYAN + repeatString("=", 60) + RESET);
		System.out.println(BOLD + "GAME STATISTICS" + RESET);
		System.out.println(FG_CYAN + repeatString("=", 60) + RESET);

		GameStatistics stats = controller.getStatistics();
		System.out.println("\nTotal Turns: " + BOLD + stats.getTotalTurns() + RESET);

		System.out.println("\nPlayer Statistics:");
		for (Player player : controller.getPlayers()) {
			GameStatistics.PlayerStats playerStats = stats.getPlayerStats(player);
			if (playerStats != null) {
				System.out.println("\n  " + BOLD + playerStats.getPlayerName() + ":" + RESET);
				System.out.println("    Turns: " + playerStats.getTurns());
				System.out.println("    Cards Purchased: " + playerStats.getPurchases());
				System.out.println("    Cards Reserved: " + playerStats.getReservations());
				System.out.println("    Gems Taken: " + playerStats.getGemsTaken());
				System.out.println("    Prestige from Cards: " + playerStats.getPrestigeFromCards());
			}
		}

		System.out.println("\n" + FG_CYAN + repeatString("=", 60) + RESET);
	}

	/**
	 * Helper method to repeat a string (Java 8 compatible).
	 *
	 * @param str the string to repeat
	 * @param count the number of times to repeat
	 * @return the repeated string
	 */
	private String repeatString(String str, int count) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < count; i++) {
			sb.append(str);
		}
		return sb.toString();
	}
}
