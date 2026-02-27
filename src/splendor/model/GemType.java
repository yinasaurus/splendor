package splendor.model;

/**
 * Enumeration representing the different types of gems in Splendor.
 */
public enum GemType {
	RUBY("Ruby", "R"),
	EMERALD("Emerald", "E"),
	SAPPHIRE("Sapphire", "S"),
	DIAMOND("Diamond", "D"),
	ONYX("Onyx", "O"),
	GOLD("Gold", "G");

	private final String name;
	private final String abbreviation;

	/**
	 * Constructor for GemType.
	 *
	 * @param name the full name of the gem
	 * @param abbreviation the single letter abbreviation
	 */
	GemType(String name, String abbreviation) {
		this.name = name;
		this.abbreviation = abbreviation;
	}

	/**
	 * Gets the full name of the gem.
	 *
	 * @return the gem name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the abbreviation of the gem.
	 *
	 * @return the gem abbreviation
	 */
	public String getAbbreviation() {
		return abbreviation;
	}

	/**
	 * Gets a GemType from its abbreviation.
	 *
	 * @param abbr the abbreviation
	 * @return the corresponding GemType, or null if not found
	 */
	public static GemType fromAbbreviation(String abbr) {
		for (GemType type : values()) {
			if (type.abbreviation.equalsIgnoreCase(abbr)) {
				return type;
			}
		}
		return null;
	}
}
