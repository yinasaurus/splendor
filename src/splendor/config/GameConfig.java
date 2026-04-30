package splendor.config; // Package for settings and paths (keeps config separate from rules/model).

import java.io.FileInputStream; // Byte stream to read config.properties from disk.
import java.io.IOException; // Thrown if the file is missing or unreadable.
import java.util.Properties; // Key=value store matching .properties file format.

/**
 * Loads and manages game configuration from config.properties file.
 */
public class GameConfig { // Single place to read tuning knobs and data file locations.
	private static final String CONFIG_FILE = "config.properties"; // Filename expected in the process working directory.
	private Properties properties; // In-memory map loaded from file (or defaults).

	/**
	 * Constructor that loads configuration from file.
	 */
	public GameConfig() { // Called when GameController (etc.) starts up.
		properties = new Properties(); // Empty map before load.
		loadConfig(); // Fill from file or defaults on failure.
	}

	/**
	 * Loads configuration from the properties file.
	 */
	private void loadConfig() { // Internal: try disk, else fall back.
		try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) { // Auto-closes stream after load.
			properties.load(fis); // Parses KEY=value lines into properties.
		} catch (IOException e) { // Missing file, permission, etc.
			System.err.println("Warning: Could not load config.properties. Using default values."); // User-visible hint.
			setDefaults(); // Populate properties so getters still work.
		}
	}

	/**
	 * Sets default configuration values.
	 */
	private void setDefaults() { // Mirrors typical Splendor-style settings when no file exists.
		properties.setProperty("winning.points", "15"); // Prestige needed to trigger endgame round.
		properties.setProperty("gems.ruby.initial", "4,5,7"); // Comma list: 2 / 3 / 4 players — chip counts per color.
		properties.setProperty("gems.emerald.initial", "4,5,7"); // Same pattern for each non-wild color.
		properties.setProperty("gems.sapphire.initial", "4,5,7");
		properties.setProperty("gems.diamond.initial", "4,5,7");
		properties.setProperty("gems.onyx.initial", "4,5,7");
		properties.setProperty("gems.gold.initial", "5,5,5"); // Gold (wild) bank sizes by player count.
		properties.setProperty("cards.level1.path", "data/Splendor_CardDistribution - Cards.csv"); // CSV for loaders (levels may share file).
		properties.setProperty("cards.level2.path", "data/Splendor_CardDistribution - Cards.csv");
		properties.setProperty("cards.level3.path", "data/Splendor_CardDistribution - Cards.csv");
		properties.setProperty("nobles.path", "data/Splendor_CardDistribution - Nobles.csv"); // Noble definitions.
		properties.setProperty("max.gems.per.player", "10"); // Splendor hand limit.
		properties.setProperty("max.reserved.cards", "3"); // Max reserved development cards per player.
	}

	/**
	 * Gets the winning points threshold.
	 *
	 * @return the winning points
	 */
	public int getWinningPoints() { // Used by rules and Hard AI “win this turn” logic.
		return Integer.parseInt(properties.getProperty("winning.points", "15")); // Parse string; default if key missing.
	}

	/**
	 * Gets the initial gem count for a specific gem type and player count.
	 *
	 * @param gemType the gem type (ruby, emerald, sapphire, diamond, onyx, gold)
	 * @param numPlayers the number of players (2, 3, or 4)
	 * @return the initial gem count
	 */
	public int getInitialGemCount(String gemType, int numPlayers) { // Board setup: how many chips of this color.
		String key = "gems." + gemType.toLowerCase() + ".initial"; // e.g. gems.ruby.initial
		String value = properties.getProperty(key, "4,5,7"); // Default triple if key absent.
		String[] counts = value.split(","); // Split "4,5,7" into three strings.

		if (numPlayers == 2 && counts.length > 0) { // Two-player game uses first slot.
			return Integer.parseInt(counts[0].trim());
		} else if (numPlayers == 3 && counts.length > 1) { // Three players use middle slot.
			return Integer.parseInt(counts[1].trim());
		} else if (numPlayers == 4 && counts.length > 2) { // Four players use third slot.
			return Integer.parseInt(counts[2].trim());
		}
		// Default fallback
		return Integer.parseInt(counts[0].trim()); // Bad numPlayers or short list — use first number.
	}

	/**
	 * Gets the path to a card data file.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the file path
	 */
	public String getCardDataPath(int level) { // CardLoader asks per deck level.
		return properties.getProperty("cards.level" + level + ".path", // Key cards.level1.path etc.
			"data/level" + level + "_cards.csv"); // Fallback path if property missing.
	}

	/**
	 * Gets the path to the nobles data file.
	 *
	 * @return the file path
	 */
	public String getNoblesDataPath() { // NobleLoader entry point.
		return properties.getProperty("nobles.path", "data/nobles.csv"); // Default CSV name if unset.
	}

	/**
	 * Gets the maximum gems a player can hold.
	 *
	 * @return the maximum gem count
	 */
	public int getMaxGemsPerPlayer() { // GameRules validate take / reserve discard chains.
		return Integer.parseInt(properties.getProperty("max.gems.per.player", "10"));
	}

	/**
	 * Gets the maximum reserved cards a player can have.
	 *
	 * @return the maximum reserved cards
	 */
	public int getMaxReservedCards() { // Cap on reserve actions and AI reserve logic.
		return Integer.parseInt(properties.getProperty("max.reserved.cards", "3"));
	}
}
