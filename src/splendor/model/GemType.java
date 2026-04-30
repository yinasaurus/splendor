package splendor.model; // Domain model: gems / colors on cards and bank.

/**
 * Enumeration representing the different types of gems in Splendor.
 */
public enum GemType { // Fixed set of chip colors plus gold (wild).
	RUBY("Ruby", "R"), // Red gems in standard Splendor palette.
	EMERALD("Emerald", "E"), // Green.
	SAPPHIRE("Sapphire", "S"), // Blue.
	DIAMOND("Diamond", "D"), // White chips in physical game; still a color key.
	ONYX("Onyx", "O"), // Black.
	GOLD("Gold", "G"); // Wild—cannot be taken from bank like colors; from reserves.

	private final String name; // Full English name for config / display.
	private final String abbreviation; // Single letter for console (R, E, …).

	/**
	 * Constructor for GemType.
	 *
	 * @param name the full name of the gem
	 * @param abbreviation the single letter abbreviation
	 */
	GemType(String name, String abbreviation) { // Private enum ctor: called for each constant above.
		this.name = name; // Store display name.
		this.abbreviation = abbreviation; // Store short code.
	}

	/**
	 * Gets the full name of the gem.
	 *
	 * @return the gem name
	 */
	public String getName() { // e.g. "Ruby" for config keys.
		return name;
	}

	/**
	 * Gets the abbreviation of the gem.
	 *
	 * @return the gem abbreviation
	 */
	public String getAbbreviation() { // For compact UI / CSV mapping.
		return abbreviation;
	}

	/**
	 * Gets a GemType from its abbreviation.
	 *
	 * @param abbr the abbreviation
	 * @return the corresponding GemType, or null if not found
	 */
	public static GemType fromAbbreviation(String abbr) { // Parse user input like "R" or "r".
		for (GemType type : values()) { // Every enum constant.
			if (type.abbreviation.equalsIgnoreCase(abbr)) { // Case-insensitive match.
				return type; // Found.
			}
		}
		return null; // Unknown abbreviation.
	}
}
