package splendor.data;

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
 */
public class NobleLoader {
	/**
	 * Loads nobles from a CSV file.
	 *
	 * @param filePath the path to the CSV file
	 * @return a list of nobles
	 */
	public static List<Noble> loadNobles(String filePath) {
		List<Noble> nobles = new ArrayList<>();
		
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
				
				Noble noble = parseNobleLine(line);
				if (noble != null) {
					nobles.add(noble);
				}
			}
		} catch (IOException e) {
			System.err.println("Warning: Could not load nobles from " + filePath + 
				". Using default nobles.");
			nobles = generateDefaultNobles();
		}
		
		return nobles;
	}

	/**
	 * Parses a single line from the CSV file.
	 * Expected format: nobleId,name,prestigePoints,reqRuby,reqEmerald,reqSapphire,reqDiamond,reqOnyx
	 *
	 * @param line the CSV line
	 * @return a Noble object, or null if parsing fails
	 */
	private static Noble parseNobleLine(String line) {
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
			System.err.println("Error parsing noble line: " + line);
			return null;
		}
	}

	/**
	 * Generates default nobles if file loading fails.
	 *
	 * @return a list of default nobles
	 */
	private static List<Noble> generateDefaultNobles() {
		List<Noble> nobles = new ArrayList<>();
		String[] names = {"Isabella", "Francis", "Catherine", "Charles", "Marie"};
		
		for (int i = 0; i < 5; i++) {
			Map<GemType, Integer> requirement = new HashMap<>();
			// Each noble requires 3 gems of different types
			GemType[] types = GemType.values();
			for (int j = 0; j < 3; j++) {
				requirement.put(types[j], 3);
			}
			nobles.add(new Noble(i + 1, names[i], 3, requirement));
		}
		return nobles;
	}
}
