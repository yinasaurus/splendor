package splendor.model;

import java.util.HashMap;
import java.util.Map;

/**
 * Represents a Noble in Splendor.
 * Nobles visit players who meet their gem requirements and provide prestige points.
 */
public class Noble {
	private final int nobleId;
	private final int prestigePoints;
	private final Map<GemType, Integer> requirement;
	private final String name;

	/**
	 * Constructor for Noble.
	 *
	 * @param nobleId unique identifier for the noble
	 * @param name the name of the noble
	 * @param prestigePoints the prestige points this noble provides
	 * @param requirement the gem requirement map (gem type to quantity required)
	 */
	public Noble(int nobleId, String name, int prestigePoints, Map<GemType, Integer> requirement) {
		this.nobleId = nobleId;
		this.name = name;
		this.prestigePoints = prestigePoints;
		this.requirement = new HashMap<>(requirement);
	}

	/**
	 * Gets the noble ID.
	 *
	 * @return the noble ID
	 */
	public int getNobleId() {
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
	public int getPrestigePoints() {
		return prestigePoints;
	}

	/**
	 * Gets the requirement map.
	 *
	 * @return a copy of the requirement map
	 */
	public Map<GemType, Integer> getRequirement() {
		return new HashMap<>(requirement);
	}

	/**
	 * Checks if a player meets the noble's requirements.
	 *
	 * @param playerBonuses the player's gem bonuses from cards
	 * @return true if the player meets the requirements
	 */
	public boolean meetsRequirement(Map<GemType, Integer> playerBonuses) {
		for (Map.Entry<GemType, Integer> req : requirement.entrySet()) {
			int required = req.getValue();
			int available = playerBonuses.getOrDefault(req.getKey(), 0);
			if (available < required) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("Noble [").append(name).append("] ");
		sb.append("Points:").append(prestigePoints).append(" ");
		sb.append("Requires:");
		for (Map.Entry<GemType, Integer> entry : requirement.entrySet()) {
			sb.append(" ").append(entry.getKey().getAbbreviation()).append(":").append(entry.getValue());
		}
		return sb.toString();
	}
}
