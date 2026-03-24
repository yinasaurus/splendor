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
 * Supports legacy rows ({@code id,points,bonus,R,E,S,D,O}) and official-style
 * distribution sheets ({@code Card Level, Gem Color, Prestige, ... costs by color}).
 */
public class CardLoader {

	private static String stripBom(String line) {
		if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
			return line.substring(1);
		}
		return line;
	}

	/**
	 * Maps sheet color names to {@link GemType} (bonus and cost columns).
	 */
	private static GemType gemTypeFromSheetColor(String raw) {
		if (raw == null) {
			return null;
		}
		switch (raw.trim().toUpperCase()) {
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
				return null;
		}
	}

	private static boolean isDistributionCardsHeader(String line) {
		String lower = line.toLowerCase();
		return lower.contains("card level") && lower.contains("gem color");
	}

	/**
	 * Parses one distribution-format row. Columns: Level, Bonus color, Prestige, Total, (blank),
	 * White, Blue, Green, Red, Black costs.
	 */
	private static Card parseDistributionCardLine(String line, int expectedLevel, int cardId) {
		String[] parts = line.split(",", -1);
		if (parts.length < 10) {
			return null;
		}
		int rowLevel;
		try {
			rowLevel = Integer.parseInt(parts[0].trim());
		} catch (NumberFormatException e) {
			return null;
		}
		if (rowLevel != expectedLevel) {
			return null;
		}
		GemType bonus = gemTypeFromSheetColor(parts[1].trim());
		if (bonus == null || bonus == GemType.GOLD) {
			return null;
		}
		int prestige = 0;
		String pts = parts[2].trim();
		if (!pts.isEmpty()) {
			try {
				prestige = Integer.parseInt(pts);
			} catch (NumberFormatException e) {
				return null;
			}
		}
		Map<GemType, Integer> cost = new HashMap<>();
		// White, Blue, Green, Red, Black → D, S, E, R, O
		int[] idx = { 5, 6, 7, 8, 9 };
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
			if (n > 0) {
				cost.put(types[i], n);
			}
		}
		return new Card(cardId, rowLevel, prestige, bonus, cost);
	}

	/**
	 * Legacy: cardId,prestigePoints,bonusGem,costRuby,costEmerald,costSapphire,costDiamond,costOnyx
	 */
	private static Card parseLegacyCardLine(String line, int level) {
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
			return null;
		}
	}

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
			String first = stripBom(br.readLine());
			if (first == null) {
				return generateDefaultCards(level);
			}
			boolean distribution = isDistributionCardsHeader(first.trim());

			if (distribution) {
				int seq = 0;
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) {
						break;
					}
					int id = level * 1000 + (++seq);
					Card card = parseDistributionCardLine(line, level, id);
					if (card != null) {
						cards.add(card);
					}
				}
			} else {
				// Legacy: treat `first` as header row (skip); body follows
				String line;
				while ((line = br.readLine()) != null) {
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
			cards = generateDefaultCards(level);
		}

		if (cards.isEmpty()) {
			return generateDefaultCards(level);
		}
		return cards;
	}

	/**
	 * Generates default cards if file loading fails.
	 *
	 * @param level the card level
	 * @return a list of default cards
	 */
	private static List<Card> generateDefaultCards(int level) {
		List<Card> cards = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			Map<GemType, Integer> cost = new HashMap<>();
			cost.put(GemType.RUBY, (int) (Math.random() * 3));
			cost.put(GemType.EMERALD, (int) (Math.random() * 3));
			cost.put(GemType.SAPPHIRE, (int) (Math.random() * 3));
			cost.put(GemType.DIAMOND, (int) (Math.random() * 3));
			cost.put(GemType.ONYX, (int) (Math.random() * 3));

			GemType[] gemTypes = GemType.values();
			GemType bonusGem = gemTypes[(int) (Math.random() * 5)];

			int prestige = (level == 1) ? 0 : (level == 2) ? 1 : (int) (Math.random() * 3) + 2;

			cards.add(new Card(i + 1, level, prestige, bonusGem, cost));
		}
		return cards;
	}
}
