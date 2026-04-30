package splendor.model; // Domain model: nobles on the board.

import java.util.HashMap; // Defensive copy of requirement map.
import java.util.Map; // Bonus requirements per color.

/**
 * Represents a Noble in Splendor.
 * Nobles visit players who meet their gem requirements and provide prestige points.
 */
public class Noble { // Immutable identity; requirement map copied in constructor.
	private final int nobleId; // Stable id from CSV or assigned in loader.
	private final int prestigePoints; // Points granted when visited.
	private final Map<GemType, Integer> requirement; // Needed **bonuses** (from cards), not loose gems.
	private final String name; // Display name.

	/**
	 * Constructor for Noble.
	 *
	 * @param nobleId unique identifier for the noble
	 * @param name the name of the noble
	 * @param prestigePoints the prestige points this noble provides
	 * @param requirement the gem requirement map (gem type to quantity required)
	 */
	public Noble(int nobleId, String name, int prestigePoints, Map<GemType, Integer> requirement) { // Copies requirement.
		this.nobleId = nobleId;
		this.name = name;
		this.prestigePoints = prestigePoints;
		this.requirement = new HashMap<>(requirement); // Protect internal map from caller mutation.
	}

	/**
	 * Gets the noble ID.
	 *
	 * @return the noble ID
	 */
	public int getNobleId() { // For logs / JSON.
		return nobleId;
	}

	/**
	 * Gets the noble name.
	 *
	 * @return the noble name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the prestige points.
	 *
	 * @return the prestige points
	 */
	public int getPrestigePoints() { // Added to player when noble visits.
		return prestigePoints;
	}

	/**
	 * Gets the requirement map.
	 *
	 * @return a copy of the requirement map
	 */
	public Map<GemType, Integer> getRequirement() { // Caller cannot mutate noble’s internal map.
		return new HashMap<>(requirement);
	}

	/**
	 * Checks if a player meets the noble's requirements.
	 *
	 * @param playerBonuses the player's gem bonuses from cards
	 * @return true if the player meets the requirements
	 */
	public boolean meetsRequirement(Map<GemType, Integer> playerBonuses) { // Compare bonus counts only.
		for (Map.Entry<GemType, Integer> req : requirement.entrySet()) { // Each color requirement.
			int required = req.getValue(); // Needed bonus pips of this color.
			int available = playerBonuses.getOrDefault(req.getKey(), 0); // Owned bonuses.
			if (available < required) { // Short on this color.
				return false; // Noble not satisfied.
			}
		}
		return true; // All entries satisfied.
	}

	@Override
	public String toString() { // Human-readable one-liner for console.
		StringBuilder sb = new StringBuilder(); // Efficient string build.
		sb.append("Noble [").append(name).append("] "); // Name in brackets.
		sb.append("Points:").append(prestigePoints).append(" "); // Prestige reward.
		sb.append("Requires:"); // Prefix for cost line.
		for (Map.Entry<GemType, Integer> entry : requirement.entrySet()) { // Abbrev:count pairs.
			sb.append(" ").append(entry.getKey().getAbbreviation()).append(":").append(entry.getValue());
		}
		return sb.toString(); // Final text.
	}
}
