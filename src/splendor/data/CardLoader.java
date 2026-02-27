package splendor.data;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.model.Card;
import splendor.model.GemType;

/**
 * Loads card data from CSV files.
 */
public class CardLoader {
	/**
	 * Loads cards from a CSV file.
	 *
	 * @param filePath the path to the CSV file
	 * @param level the card level (1, 2, or 3)
	 * @return a list of cards
	 */
	public static List<Card> loadCards(String filePath, int level) {
		List<Card> cards = new ArrayList<>();
		
		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String line;
			boolean isFirstLine = true;
			
			while ((line = br.readLine()) != null) {
				if (isFirstLine) {
					isFirstLine = false;
					continue; // Skip header
				}
				
				line = line.trim();
				if (line.isEmpty()) {
					continue;
				}
				
				Card card = parseCardLine(line, level);
				if (card != null) {
					cards.add(card);
				}
			}
		} catch (IOException e) {
			System.err.println("Warning: Could not load cards from " + filePath + 
				". Using default cards.");
			cards = generateDefaultCards(level);
		}
		
		return cards;
	}

	/**
	 * Parses a single line from the CSV file.
	 * Expected format: cardId,prestigePoints,bonusGem,costRuby,costEmerald,costSapphire,costDiamond,costOnyx
	 *
	 * @param line the CSV line
	 * @param level the card level
	 * @return a Card object, or null if parsing fails
	 */
	private static Card parseCardLine(String line, int level) {
		try {
			String[] parts = line.split(",");
			if (parts.length < 8) {
				return null;
			}
			
			int cardId = Integer.parseInt(parts[0].trim());
			int prestigePoints = Integer.parseInt(parts[1].trim());
			String bonusGemStr = parts[2].trim().toUpperCase();
			GemType bonusGem = GemType.valueOf(bonusGemStr);
			
			Map<GemType, Integer> cost = new HashMap<>();
			cost.put(GemType.RUBY, Integer.parseInt(parts[3].trim()));
			cost.put(GemType.EMERALD, Integer.parseInt(parts[4].trim()));
			cost.put(GemType.SAPPHIRE, Integer.parseInt(parts[5].trim()));
			cost.put(GemType.DIAMOND, Integer.parseInt(parts[6].trim()));
			cost.put(GemType.ONYX, Integer.parseInt(parts[7].trim()));
			
			return new Card(cardId, level, prestigePoints, bonusGem, cost);
		} catch (Exception e) {
			System.err.println("Error parsing card line: " + line);
			return null;
		}
	}

	/**
	 * Generates default cards if file loading fails.
	 *
	 * @param level the card level
	 * @return a list of default cards
	 */
	private static List<Card> generateDefaultCards(int level) {
		List<Card> cards = new ArrayList<>();
		// Generate some basic default cards
		// This is a fallback - in production, you'd want proper card data
		for (int i = 0; i < 20; i++) {
			Map<GemType, Integer> cost = new HashMap<>();
			cost.put(GemType.RUBY, (int)(Math.random() * 3));
			cost.put(GemType.EMERALD, (int)(Math.random() * 3));
			cost.put(GemType.SAPPHIRE, (int)(Math.random() * 3));
			cost.put(GemType.DIAMOND, (int)(Math.random() * 3));
			cost.put(GemType.ONYX, (int)(Math.random() * 3));
			
			GemType[] gemTypes = GemType.values();
			GemType bonusGem = gemTypes[(int)(Math.random() * 5)]; // Exclude GOLD
			
			int prestige = (level == 1) ? 0 : (level == 2) ? 1 : (int)(Math.random() * 3) + 2;
			
			cards.add(new Card(i + 1, level, prestige, bonusGem, cost));
		}
		return cards;
	}
}
