package splendor.config;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Loads and manages game configuration from config.properties file.
 */
public class GameConfig {
	private static final String CONFIG_FILE = "config.properties";
	private Properties properties;

	/**
	 * Constructor that loads configuration from file.
	 */
	public GameConfig() {
		properties = new Properties();
		loadConfig();
	}

	/**
	 * Loads configuration from the properties file.
	 */
	private void loadConfig() {
		try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
			properties.load(fis);
		} catch (IOException e) {
			System.err.println("Warning: Could not load config.properties. Using default values.");
			setDefaults();
		}
	}

	/**
	 * Sets default configuration values.
	 */
	private void setDefaults() {
		properties.setProperty("winning.points", "15");
		properties.setProperty("gems.ruby.initial", "4,5,7");
		properties.setProperty("gems.emerald.initial", "4,5,7");
		properties.setProperty("gems.sapphire.initial", "4,5,7");
		properties.setProperty("gems.diamond.initial", "4,5,7");
		properties.setProperty("gems.onyx.initial", "4,5,7");
		properties.setProperty("gems.gold.initial", "5,5,5");
		properties.setProperty("cards.level1.path", "data/Splendor_CardDistribution - Cards.csv");
		properties.setProperty("cards.level2.path", "data/Splendor_CardDistribution - Cards.csv");
		properties.setProperty("cards.level3.path", "data/Splendor_CardDistribution - Cards.csv");
		properties.setProperty("nobles.path", "data/Splendor_CardDistribution - Nobles.csv");
		properties.setProperty("max.gems.per.player", "10");
		properties.setProperty("max.reserved.cards", "3");
	}

	/**
	 * Gets the winning points threshold.
	 *
	 * @return the winning points
	 */
	public int getWinningPoints() {
		return Integer.parseInt(properties.getProperty("winning.points", "15"));
	}

	/**
	 * Gets the initial gem count for a specific gem type and player count.
	 *
	 * @param gemType the gem type (ruby, emerald, sapphire, diamond, onyx, gold)
	 * @param numPlayers the number of players (2, 3, or 4)
	 * @return the initial gem count
	 */
	public int getInitialGemCount(String gemType, int numPlayers) {
		String key = "gems." + gemType.toLowerCase() + ".initial";
		String value = properties.getProperty(key, "4,5,7");
		String[] counts = value.split(",");
		
		if (numPlayers == 2 && counts.length > 0) {
			return Integer.parseInt(counts[0].trim());
		} else if (numPlayers == 3 && counts.length > 1) {
			return Integer.parseInt(counts[1].trim());
		} else if (numPlayers == 4 && counts.length > 2) {
			return Integer.parseInt(counts[2].trim());
		}
		// Default fallback
		return Integer.parseInt(counts[0].trim());
	}

	/**
	 * Gets the path to a card data file.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the file path
	 */
	public String getCardDataPath(int level) {
		return properties.getProperty("cards.level" + level + ".path", 
			"data/level" + level + "_cards.csv");
	}

	/**
	 * Gets the path to the nobles data file.
	 *
	 * @return the file path
	 */
	public String getNoblesDataPath() {
		return properties.getProperty("nobles.path", "data/nobles.csv");
	}

	/**
	 * Gets the maximum gems a player can hold.
	 *
	 * @return the maximum gem count
	 */
	public int getMaxGemsPerPlayer() {
		return Integer.parseInt(properties.getProperty("max.gems.per.player", "10"));
	}

	/**
	 * Gets the maximum reserved cards a player can have.
	 *
	 * @return the maximum reserved cards
	 */
	public int getMaxReservedCards() {
		return Integer.parseInt(properties.getProperty("max.reserved.cards", "3"));
	}
}
