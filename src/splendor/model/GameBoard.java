package splendor.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the game board containing gems, cards, and nobles.
 */
public class GameBoard {
	private final Map<GemType, Integer> availableGems;
	private final List<List<Card>> cardTiers; // Three tiers (levels 1, 2, 3) - visible cards
	private final List<List<Card>> cardDecks; // Three decks (levels 1, 2, 3) - remaining cards
	private final List<Noble> availableNobles;
	private final int numPlayers;

	/**
	 * Constructor for GameBoard.
	 *
	 * @param numPlayers the number of players in the game
	 */
	public GameBoard(int numPlayers) {
		this.numPlayers = numPlayers;
		this.availableGems = new HashMap<>();
		this.cardTiers = new ArrayList<>();
		this.cardDecks = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			cardTiers.add(new ArrayList<>());
			cardDecks.add(new ArrayList<>());
		}
		this.availableNobles = new ArrayList<>();
		
		// Initialize all gem types to 0
		for (GemType type : GemType.values()) {
			availableGems.put(type, 0);
		}
	}

	/**
	 * Gets the number of players.
	 *
	 * @return the number of players
	 */
	public int getNumPlayers() {
		return numPlayers;
	}

	/**
	 * Gets the available gems.
	 *
	 * @return a copy of the available gems map
	 */
	public Map<GemType, Integer> getAvailableGems() {
		return new HashMap<>(availableGems);
	}

	/**
	 * Gets the gem count for a specific type.
	 *
	 * @param type the gem type
	 * @return the count
	 */
	public int getGemCount(GemType type) {
		return availableGems.getOrDefault(type, 0);
	}

	/**
	 * Sets the gem count for a specific type.
	 *
	 * @param type the gem type
	 * @param count the count to set
	 */
	public void setGemCount(GemType type, int count) {
		availableGems.put(type, count);
	}

	/**
	 * Adds gems to the board.
	 *
	 * @param gems the gems to add
	 */
	public void addGems(Map<GemType, Integer> gems) {
		for (Map.Entry<GemType, Integer> entry : gems.entrySet()) {
			availableGems.put(entry.getKey(), 
				availableGems.getOrDefault(entry.getKey(), 0) + entry.getValue());
		}
	}

	/**
	 * Removes gems from the board.
	 *
	 * @param gems the gems to remove
	 */
	public void removeGems(Map<GemType, Integer> gems) {
		for (Map.Entry<GemType, Integer> entry : gems.entrySet()) {
			int current = availableGems.getOrDefault(entry.getKey(), 0);
			availableGems.put(entry.getKey(), Math.max(0, current - entry.getValue()));
		}
	}

	/**
	 * Gets the visible cards for a specific tier (level).
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the list of visible cards
	 */
	public List<Card> getVisibleCards(int level) {
		if (level < 1 || level > 3) {
			return new ArrayList<>();
		}
		List<Card> tier = cardTiers.get(level - 1);
		// Return up to 4 visible cards
		int visibleCount = Math.min(4, tier.size());
		return new ArrayList<>(tier.subList(0, visibleCount));
	}

	/**
	 * Adds a card to a tier (visible cards).
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param card the card to add
	 */
	public void addCard(int level, Card card) {
		if (level >= 1 && level <= 3) {
			cardTiers.get(level - 1).add(card);
		}
	}

	/**
	 * Adds a card to the deck (not visible).
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param card the card to add
	 */
	public void addCardToDeck(int level, Card card) {
		if (level >= 1 && level <= 3) {
			cardDecks.get(level - 1).add(card);
		}
	}

	/**
	 * Draws a new card from the deck to replace a purchased card.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the drawn card, or null if deck is empty
	 */
	public Card drawCardFromDeck(int level) {
		if (level >= 1 && level <= 3) {
			List<Card> deck = cardDecks.get(level - 1);
			if (!deck.isEmpty()) {
				return deck.remove(0);
			}
		}
		return null;
	}

	/**
	 * Peeks at the top card from a deck without removing it.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the top card, or null if deck is empty
	 */
	public Card peekTopCard(int level) {
		if (level >= 1 && level <= 3) {
			List<Card> deck = cardDecks.get(level - 1);
			if (!deck.isEmpty()) {
				return deck.get(0);
			}
		}
		return null;
	}

	/**
	 * Gets the number of cards remaining in a deck.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @return the number of cards remaining
	 */
	public int getDeckSize(int level) {
		if (level >= 1 && level <= 3) {
			return cardDecks.get(level - 1).size();
		}
		return 0;
	}

	/**
	 * Removes a card from a tier.
	 *
	 * @param level the card level (1, 2, or 3)
	 * @param card the card to remove
	 * @return true if the card was removed
	 */
	public boolean removeCard(int level, Card card) {
		if (level >= 1 && level <= 3) {
			return cardTiers.get(level - 1).remove(card);
		}
		return false;
	}

	/**
	 * Gets the available nobles.
	 *
	 * @return a copy of the available nobles list
	 */
	public List<Noble> getAvailableNobles() {
		return new ArrayList<>(availableNobles);
	}

	/**
	 * Adds a noble to the board.
	 *
	 * @param noble the noble to add
	 */
	public void addNoble(Noble noble) {
		availableNobles.add(noble);
	}

	/**
	 * Removes a noble from the board.
	 *
	 * @param noble the noble to remove
	 * @return true if the noble was removed
	 */
	public boolean removeNoble(Noble noble) {
		return availableNobles.remove(noble);
	}
}
