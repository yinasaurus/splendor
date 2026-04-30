package splendor.data; // CSV → model objects for cards.

import java.io.BufferedReader; // Line-by-line file read.
import java.io.FileReader; // Character file input.
import java.io.IOException; // Missing file / IO errors.
import java.util.ArrayList; // Result list.
import java.util.HashMap; // Cost map per card.
import java.util.List; // Return type.
import java.util.Map; // Gem costs.

import splendor.model.Card; // Loaded type.
import splendor.model.GemType; // Cost and bonus colors.

/**
 * Loads card data from CSV files.
 * Supports legacy rows ({@code id,points,bonus,R,E,S,D,O}) and official-style
 * distribution sheets ({@code Card Level, Gem Color, Prestige, ... costs by color}).
 */
public class CardLoader { // All static helpers — no instances.

	private static String stripBom(String line) { // UTF-8 BOM breaks header detection.
		if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') { // BOM char at start.
			return line.substring(1); // Drop it.
		}
		return line; // Unchanged.
	}

	/**
	 * Maps sheet color names to {@link GemType} (bonus and cost columns).
	 */
	private static GemType gemTypeFromSheetColor(String raw) { // “WHITE” → DIAMOND etc.
		if (raw == null) {
			return null;
		}
		switch (raw.trim().toUpperCase()) { // Normalize spacing and case.
			case "WHITE":
				return GemType.DIAMOND;
			case "BLUE":
				return GemType.SAPPHIRE;
			case "GREEN":
				return GemType.EMERALD;
			case "RED":
				return GemType.RUBY;
			case "BLACK":
				return GemType.ONYX;
			default:
				return null; // Unknown sheet label.
		}
	}

	private static boolean isDistributionCardsHeader(String line) { // Detect modern spreadsheet format.
		String lower = line.toLowerCase();
		return lower.contains("card level") && lower.contains("gem color"); // Typical column titles.
	}

	/**
	 * Parses one distribution-format row. Columns: Level, Bonus color, Prestige, Total, (blank),
	 * White, Blue, Green, Red, Black costs.
	 */
	private static Card parseDistributionCardLine(String line, int expectedLevel, int cardId) { // One data row → Card or null.
		String[] parts = line.split(",", -1); // Keep trailing empty cells.
		if (parts.length < 10) {
			return null; // Malformed row.
		}
		int rowLevel;
		try {
			rowLevel = Integer.parseInt(parts[0].trim()); // First column = level.
		} catch (NumberFormatException e) {
			return null;
		}
		if (rowLevel != expectedLevel) {
			return null; // Row doesn’t belong in this level’s file pass.
		}
		GemType bonus = gemTypeFromSheetColor(parts[1].trim()); // Bonus gem column.
		if (bonus == null || bonus == GemType.GOLD) {
			return null; // Invalid bonus for a dev card.
		}
		int prestige = 0;
		String pts = parts[2].trim(); // Prestige column may be blank (0).
		if (!pts.isEmpty()) {
			try {
				prestige = Integer.parseInt(pts);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		Map<GemType, Integer> cost = new HashMap<>();
		// White, Blue, Green, Red, Black → D, S, E, R, O
		int[] idx = { 5, 6, 7, 8, 9 }; // Column indices for five color costs.
		GemType[] types = {
			GemType.DIAMOND, GemType.SAPPHIRE, GemType.EMERALD, GemType.RUBY, GemType.ONYX
		};
		for (int i = 0; i < idx.length; i++) {
			String c = parts[idx[i]].trim();
			int n = 0;
			if (!c.isEmpty()) {
				try {
					n = Integer.parseInt(c);
				} catch (NumberFormatException e) {
					return null;
				}
			}
			if (n > 0) { // Omit zero entries from cost map.
				cost.put(types[i], n);
			}
		}
		return new Card(cardId, rowLevel, prestige, bonus, cost); // Construct immutable card data.
	}

	/**
	 * Legacy: cardId,prestigePoints,bonusGem,costRuby,costEmerald,costSapphire,costDiamond,costOnyx
	 */
	private static Card parseLegacyCardLine(String line, int level) { // Old 8-column comma format.
		try {
			String[] parts = line.split(",");
			if (parts.length < 8) {
				return null;
			}

			int cardId = Integer.parseInt(parts[0].trim());
			int prestigePoints = Integer.parseInt(parts[1].trim());
			String bonusGemStr = parts[2].trim().toUpperCase();
			GemType bonusGem = GemType.valueOf(bonusGemStr); // Must match enum name e.g. RUBY.

			Map<GemType, Integer> cost = new HashMap<>();
			cost.put(GemType.RUBY, Integer.parseInt(parts[3].trim()));
			cost.put(GemType.EMERALD, Integer.parseInt(parts[4].trim()));
			cost.put(GemType.SAPPHIRE, Integer.parseInt(parts[5].trim()));
			cost.put(GemType.DIAMOND, Integer.parseInt(parts[6].trim()));
			cost.put(GemType.ONYX, Integer.parseInt(parts[7].trim()));

			return new Card(cardId, level, prestigePoints, bonusGem, cost);
		} catch (Exception e) {
			return null; // Any parse error → skip row.
		}
	}

	/**
	 * Loads cards from a CSV file.
	 *
	 * @param filePath the path to the CSV file
	 * @param level the card level (1, 2, or 3)
	 * @return a list of cards
	 */
	public static List<Card> loadCards(String filePath, int level) { // Entry point from GameController.
		List<Card> cards = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) { // Try-with-resources closes reader.
			String first = stripBom(br.readLine()); // First line: header or first data row.
			if (first == null) {
				return generateDefaultCards(level); // Empty file.
			}
			boolean distribution = isDistributionCardsHeader(first.trim()); // Branch on format.

			if (distribution) {
				int seq = 0; // Running counter for synthetic ids.
				String line;
				while ((line = br.readLine()) != null) { // Rest of file.
					line = line.trim();
					if (line.isEmpty()) {
						break; // Stop at blank line (section end).
					}
					int id = level * 1000 + (++seq); // e.g. 3001, 3002 for level 3.
					Card card = parseDistributionCardLine(line, level, id);
					if (card != null) {
						cards.add(card);
					}
				}
			} else {
				// Legacy: treat `first` as header row (skip); body follows
				String line;
				while ((line = br.readLine()) != null) { // `first` already consumed as header.
					line = line.trim();
					if (line.isEmpty()) {
						continue;
					}
					Card card = parseLegacyCardLine(line, level);
					if (card != null) {
						cards.add(card);
					}
				}
			}
		} catch (IOException e) {
			cards = generateDefaultCards(level); // Fallback deck on IO failure.
		}

		if (cards.isEmpty()) {
			return generateDefaultCards(level); // Nothing parsed successfully.
		}
		return cards;
	}

	/**
	 * Generates default cards if file loading fails.
	 *
	 * @param level the card level
	 * @return a list of default cards
	 */
	private static List<Card> generateDefaultCards(int level) { // Random-ish placeholder deck for broken paths.
		List<Card> cards = new ArrayList<>();
		for (int i = 0; i < 20; i++) { // Fixed count of stub cards.
			Map<GemType, Integer> cost = new HashMap<>();
			cost.put(GemType.RUBY, (int) (Math.random() * 3));
			cost.put(GemType.EMERALD, (int) (Math.random() * 3));
			cost.put(GemType.SAPPHIRE, (int) (Math.random() * 3));
			cost.put(GemType.DIAMOND, (int) (Math.random() * 3));
			cost.put(GemType.ONYX, (int) (Math.random() * 3));

			GemType[] gemTypes = GemType.values();
			GemType bonusGem = gemTypes[(int) (Math.random() * 5)]; // Exclude gold by using first five? values() includes GOLD at end - index 5 is GOLD. *5 gives 0-4 = non-gold if order is RUBY..ONYX first five. GemType order: RUBY,EMERALD,SAPPHIRE,DIAMOND,ONYX,GOLD - indices 0-4 are non-gold.

			int prestige = (level == 1) ? 0 : (level == 2) ? 1 : (int) (Math.random() * 3) + 2; // Level-based band.

			cards.add(new Card(i + 1, level, prestige, bonusGem, cost));
		}
		return cards;
	}
}
