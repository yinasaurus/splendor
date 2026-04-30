package splendor.data; // CSV → Noble objects.

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import splendor.model.GemType;
import splendor.model.Noble;

/**
 * Loads noble data from CSV files.
 * Supports legacy rows ({@code id,name,points,reqR,reqE,reqS,reqD,reqO}) and distribution
 * sheets ({@code Game, points, total cost, Black, Red, Green, Blue, White} dev cards).
 */
public class NobleLoader { // Static parsing helpers only.

	private static String stripBom(String line) { // Remove UTF-8 BOM if present.
		if (line != null && !line.isEmpty() && line.charAt(0) == '\uFEFF') {
			return line.substring(1);
		}
		return line;
	}

	private static boolean isDistributionNoblesHeader(String line) { // Recognize Splendor distribution sheet header row.
		String lower = line.toLowerCase();
		return lower.contains("black dev cards") || (lower.contains("game") && lower.contains("prestige"));
	}

	/**
	 * Distribution: Game, Prestige Points, Total Dev Card Cost, Black, Red, Green, Blue, White.
	 */
	private static Noble parseDistributionNobleLine(String line, int nobleId, String name) { // One noble row.
		String[] parts = line.split(",", -1);
		if (parts.length < 8) {
			return null;
		}
		String game = parts[0].trim(); // "Base" or "Expansion" row filter.
		if (!game.equalsIgnoreCase("Base") && !game.equalsIgnoreCase("Expansion")) {
			return null;
		}
		int prestigePoints;
		try {
			prestigePoints = Integer.parseInt(parts[1].trim());
		} catch (NumberFormatException e) {
			return null;
		}
		// Columns 3–7: Black, Red, Green, Blue, White → O, R, E, S, D
		GemType[] types = {
			GemType.ONYX, GemType.RUBY, GemType.EMERALD, GemType.SAPPHIRE, GemType.DIAMOND
		};
		Map<GemType, Integer> requirement = new HashMap<>();
		for (int i = 0; i < 5; i++) {
			String c = parts[3 + i].trim();
			if (c.isEmpty()) {
				continue; // 0 requirement for this color.
			}
			try {
				int n = Integer.parseInt(c);
				if (n > 0) {
					requirement.put(types[i], n);
				}
			} catch (NumberFormatException e) {
				return null;
			}
		}
		if (requirement.isEmpty()) {
			return null; // No requirements parsed.
		}
		return new Noble(nobleId, name, prestigePoints, requirement);
	}

	/**
	 * Legacy: nobleId,name,prestigePoints,reqRuby,reqEmerald,reqSapphire,reqDiamond,reqOnyx
	 */
	private static Noble parseLegacyNobleLine(String line) { // Eight fixed columns.
		try {
			String[] parts = line.split(",");
			if (parts.length < 8) {
				return null;
			}

			int nobleId = Integer.parseInt(parts[0].trim());
			String name = parts[1].trim();
			int prestigePoints = Integer.parseInt(parts[2].trim());

			Map<GemType, Integer> requirement = new HashMap<>();
			requirement.put(GemType.RUBY, Integer.parseInt(parts[3].trim()));
			requirement.put(GemType.EMERALD, Integer.parseInt(parts[4].trim()));
			requirement.put(GemType.SAPPHIRE, Integer.parseInt(parts[5].trim()));
			requirement.put(GemType.DIAMOND, Integer.parseInt(parts[6].trim()));
			requirement.put(GemType.ONYX, Integer.parseInt(parts[7].trim()));

			return new Noble(nobleId, name, prestigePoints, requirement);
		} catch (Exception e) {
			return null;
		}
	}

	/**
	 * Loads nobles from a CSV file.
	 *
	 * @param filePath the path to the CSV file
	 * @return a list of nobles
	 */
	public static List<Noble> loadNobles(String filePath) { // Called from GameController.loadGameData.
		List<Noble> nobles = new ArrayList<>();

		try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
			String first = stripBom(br.readLine());
			if (first == null) {
				return generateDefaultNobles();
			}
			boolean distribution = isDistributionNoblesHeader(first);

			if (distribution) {
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) {
						break;
					}
					String[] p = line.split(",", -1);
					if (p.length < 1) {
						continue;
					}
					String g = p[0].trim();
					if (!g.equalsIgnoreCase("Base") && !g.equalsIgnoreCase("Expansion")) {
						continue;
					}
					// Friendly in-game label (CSV "Base"/"Expansion" are sheet categories, not character names).
					int id = nobles.size() + 1; // 1-based sequential ids.
					String name = "Noble " + id;
					Noble n = parseDistributionNobleLine(line, id, name);
					if (n != null) {
						nobles.add(n);
					}
				}
			} else {
				// Legacy: first line (header) already consumed as `first`; skip it like the old loader
				String line;
				while ((line = br.readLine()) != null) {
					line = line.trim();
					if (line.isEmpty()) {
						continue;
					}
					Noble noble = parseLegacyNobleLine(line);
					if (noble != null) {
						nobles.add(noble);
					}
				}
			}
		} catch (IOException e) {
			return generateDefaultNobles();
		}

		if (nobles.isEmpty()) {
			return generateDefaultNobles();
		}
		return nobles;
	}

	/**
	 * Generates default nobles if file loading fails.
	 *
	 * @return a list of default nobles
	 */
	private static List<Noble> generateDefaultNobles() { // Hardcoded small set.
		List<Noble> nobles = new ArrayList<>();
		String[] names = { "Isabella", "Francis", "Catherine", "Charles", "Marie" };

		for (int i = 0; i < 5; i++) {
			Map<GemType, Integer> requirement = new HashMap<>();
			GemType[] types = GemType.values();
			for (int j = 0; j < 3; j++) {
				requirement.put(types[j], 3); // First three enum colors need 3 bonuses each.
			}
			nobles.add(new Noble(i + 1, names[i], 3, requirement));
		}
		return nobles;
	}
}
