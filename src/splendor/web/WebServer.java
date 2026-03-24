package splendor.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import splendor.ai.AIPlayer;
import splendor.ai.EasyAIStrategy;
import splendor.ai.HardAIStrategy;
import splendor.ai.MediumAIStrategy;
import splendor.controller.GameController;
import splendor.model.Card;
import splendor.model.GameBoard;
import splendor.model.GemType;
import splendor.model.Noble;
import splendor.model.Player;
import splendor.rules.GameRules;

/**
 * Simple embedded HTTP server that exposes the Splendor game via a web UI.
 * This keeps all game logic in Java while allowing a browser-based interface.
 */
public class WebServer {

	private static GameController controller;
	private static Map<Integer, AIPlayer> aiPlayers = new HashMap<>();

	public static void main(String[] args) throws IOException {
		System.out.println("Starting Splendor Web Server on http://localhost:8080");

		// Initialize a default game (will be replaced when player starts a new game).
		startNewGame(2, new String[] { "human", "human", "human", "human" });

		HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

		// Static files
		server.createContext("/", new StaticFileHandler("web/index.html", "text/html; charset=utf-8"));
		server.createContext("/styles.css", new StaticFileHandler("web/styles.css", "text/css; charset=utf-8"));
		server.createContext("/app.js", new StaticFileHandler("web/app.js", "application/javascript; charset=utf-8"));

		// API endpoints
		server.createContext("/api/state", new StateHandler());
		server.createContext("/api/action", new ActionHandler());
		server.createContext("/api/newgame", new NewGameHandler());
		server.createContext("/api/quit", new QuitHandler());

		server.setExecutor(null);
		server.start();
	}

	/**
	 * Initializes a new game with the given number of players and AI settings.
	 *
	 * @param numPlayers number of players (2-4)
	 */
	private static void startNewGame(int numPlayers, String[] types) {
		if (numPlayers < 2) {
			numPlayers = 2;
		} else if (numPlayers > 4) {
			numPlayers = 4;
		}

		if (types == null || types.length < 4) {
			types = new String[] { "human", "human", "human", "human" };
		}

		List<String> playerNames = new java.util.ArrayList<>();
		List<Boolean> playerTypes = new java.util.ArrayList<>();
		for (int i = 0; i < numPlayers; i++) {
			playerNames.add("Player " + (i + 1));
			String t = types[i] == null ? "human" : types[i].toLowerCase();
			boolean isHuman = !(t.equals("easy") || t.equals("medium") || t.equals("hard"));
			playerTypes.add(isHuman);
		}
		controller = new GameController(numPlayers, playerNames, playerTypes);

		// Initialize AI map
		aiPlayers.clear();
		for (int i = 0; i < numPlayers; i++) {
			String t = types[i] == null ? "human" : types[i].toLowerCase();
			if (t.equals("easy")) {
				aiPlayers.put(i, new AIPlayer(new EasyAIStrategy()));
			} else if (t.equals("medium")) {
				aiPlayers.put(i, new AIPlayer(new MediumAIStrategy()));
			} else if (t.equals("hard")) {
				aiPlayers.put(i, new AIPlayer(new HardAIStrategy()));
			}
		}
	}

	/**
	 * Serves static files from the project directory (relative paths).
	 */
	private static class StaticFileHandler implements HttpHandler {
		private final String filePath;
		private final String contentType;

		StaticFileHandler(String filePath, String contentType) {
			this.filePath = filePath;
			this.contentType = contentType;
		}

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			java.nio.file.Path path = Paths.get(filePath);
			if (!Files.exists(path)) {
				sendResponse(exchange, 404, "Not Found", "text/plain; charset=utf-8");
				return;
			}

			byte[] bytes = Files.readAllBytes(path);
			Headers headers = exchange.getResponseHeaders();
			headers.set("Content-Type", contentType);
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		}
	}

	/**
	 * Starts a new game based on parameters from the web UI.
	 */
	private static class NewGameHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			int numPlayers = extractIntField(body, "numPlayers", 2);
			String[] types = new String[4];
			types[0] = extractStringField(body, "p1Type", "human");
			types[1] = extractStringField(body, "p2Type", "human");
			types[2] = extractStringField(body, "p3Type", "human");
			types[3] = extractStringField(body, "p4Type", "human");
			startNewGame(numPlayers, types);

			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("numPlayers", numPlayers);
			resp.put("p1Type", types[0]);
			resp.put("p2Type", types[1]);
			resp.put("p3Type", types[2]);
			resp.put("p4Type", types[3]);
			String json = toJson(resp);
			sendResponse(exchange, 200, json, "application/json; charset=utf-8");
		}
	}

	/**
	 * Resets server-side game state to the default 2-player lobby (all human),
	 * matching startup. Used when the browser returns to the start screen.
	 */
	private static class QuitHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			startNewGame(2, new String[] { "human", "human", "human", "human" });
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	/**
	 * Returns the current game state as JSON for the web UI.
	 */
	private static class StateHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String json = buildGameStateJson();
			sendResponse(exchange, 200, json, "application/json; charset=utf-8");
		}
	}

	/**
	 * Handles game actions sent from the web UI.
	 * Expected JSON body (simplified):
	 * { "type": "takeGems", "gems": ["R","E","S"] }
	 * { "type": "reserve", "level": 1, "index": 0 }
	 * { "type": "purchaseVisible", "level": 1, "index": 0 }
	 * { "type": "purchaseReserved", "index": 0 }
	 */
	private static class ActionHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			Map<String, Object> result = handleAction(body);
			String json = toJson(result);
			sendResponse(exchange, 200, json, "application/json; charset=utf-8");
		}
	}

	private static Map<String, Object> handleAction(String body) {
		Map<String, Object> response = new HashMap<>();
		body = body.trim();
		if (body.isEmpty()) {
			response.put("success", false);
			response.put("message", "Empty request body");
			return response;
		}

		// Extremely small and simple "parser" for our limited JSON format.
		String lower = body.toLowerCase();
		boolean success = false;
		String message = "Unknown action";

		if (lower.contains("\"type\"") && lower.contains("takegems")) {
			// Extract gems array e.g. "gems":["R","E","S"]
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			int idx = lower.indexOf("\"gems\"");
			if (idx >= 0) {
				int start = body.indexOf("[", idx);
				int end = body.indexOf("]", start);
				if (start >= 0 && end > start) {
					String inner = body.substring(start + 1, end);
					String[] parts = inner.split(",");
					for (String part : parts) {
						String trimmed = part.replace("\"", "").trim().toUpperCase();
						if (!trimmed.isEmpty()) {
							GemType type = GemType.fromAbbreviation(trimmed);
							if (type != null && type != GemType.GOLD) {
								gemsToTake.put(type, gemsToTake.getOrDefault(type, 0) + 1);
							}
						}
					}
				}
			}
			if (!gemsToTake.isEmpty()) {
				GameRules.ValidationResult vr = controller.getRules().validateTakeGems(
					gemsToTake,
					controller.getBoard().getAvailableGems(),
					controller.getCurrentPlayer().getGems()
				);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = controller.takeGems(gemsToTake);
					message = success ? "Gems taken." : "Could not take gems.";
				}
			} else {
				message = "No valid gems specified";
			}
		} else if (lower.contains("\"type\"") && lower.contains("reserve")) {
			int level = extractIntField(body, "level", 1);
			int index = extractIntField(body, "index", 0);
			GameRules.ValidationResult vr = controller.getRules().validateReserveCard(controller.getCurrentPlayer());
			List<Card> visible = controller.getBoard().getVisibleCards(level);
			if (!vr.isValid()) {
				success = false;
				message = vr.getMessage();
			} else if (index < 0 || index >= visible.size()) {
				success = false;
				message = "Invalid card index for this level.";
			} else {
				success = controller.reserveCard(level, index);
				message = success ? "Card reserved." : "Cannot reserve this card.";
			}
		} else if (lower.contains("\"type\"") && lower.contains("purchasevisible")) {
			int level = extractIntField(body, "level", 1);
			int index = extractIntField(body, "index", 0);
			List<Card> visible = controller.getBoard().getVisibleCards(level);
			if (index < 0 || index >= visible.size()) {
				success = false;
				message = "Invalid card index for this level.";
			} else {
				Card card = visible.get(index);
				Map<GemType, Integer> payment = controller.getRules().calculatePayment(card, controller.getCurrentPlayer());
				GameRules.ValidationResult vr = controller.getRules().validatePurchaseCard(card, controller.getCurrentPlayer(), payment);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = controller.purchaseVisibleCard(level, index);
					message = success ? "Card purchased." : "Cannot purchase this card.";
				}
			}
		} else if (lower.contains("\"type\"") && lower.contains("purchasereserved")) {
			int index = extractIntField(body, "index", 0);
			List<Card> reserved = controller.getCurrentPlayer().getReservedCards();
			if (index < 0 || index >= reserved.size()) {
				success = false;
				message = "Invalid reserved card index.";
			} else {
				Card card = reserved.get(index);
				Map<GemType, Integer> payment = controller.getRules().calculatePayment(card, controller.getCurrentPlayer());
				GameRules.ValidationResult vr = controller.getRules().validatePurchaseCard(card, controller.getCurrentPlayer(), payment);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = controller.purchaseReservedCard(index);
					message = success ? "Reserved card purchased." : "Cannot purchase this reserved card.";
				}
			}
		}

		// Advance turns (including AI turns) if action succeeded
		if (success && !controller.isGameOver()) {
			advanceTurnsAfterHuman();
		}

		response.put("success", success);
		response.put("message", message);
		response.put("gameOver", controller.isGameOver());
		if (controller.isGameOver() && controller.getWinner() != null) {
			response.put("winner", controller.getWinner().getName());
		}

		return response;
	}

	private static int extractIntField(String json, String field, int defaultValue) {
		String search = "\"" + field + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) {
			return defaultValue;
		}
		int colon = json.indexOf(":", idx + search.length());
		if (colon < 0) {
			return defaultValue;
		}
		int end = colon + 1;
		while (end < json.length() && Character.isWhitespace(json.charAt(end))) {
			end++;
		}
		int start = end;
		while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '-')) {
			end++;
		}
		try {
			return Integer.parseInt(json.substring(start, end).trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	private static boolean extractBooleanField(String json, String field, boolean defaultValue) {
		String search = "\"" + field + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) {
			return defaultValue;
		}
		int colon = json.indexOf(":", idx + search.length());
		if (colon < 0) {
			return defaultValue;
		}
		int pos = colon + 1;
		while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
			pos++;
		}
		if (json.regionMatches(true, pos, "true", 0, 4)) {
			return true;
		}
		if (json.regionMatches(true, pos, "false", 0, 5)) {
			return false;
		}
		return defaultValue;
	}

	private static String extractStringField(String json, String field, String defaultValue) {
		String search = "\"" + field + "\"";
		int idx = json.indexOf(search);
		if (idx < 0) {
			return defaultValue;
		}
		int colon = json.indexOf(":", idx + search.length());
		if (colon < 0) {
			return defaultValue;
		}
		int start = json.indexOf("\"", colon + 1);
		if (start < 0) {
			return defaultValue;
		}
		int end = json.indexOf("\"", start + 1);
		if (end < 0) {
			return defaultValue;
		}
		return json.substring(start + 1, end);
	}

	/**
	 * After a human finishes an action, advance the turn and let any AI players
	 * take their turns until it is a human's turn again or the game ends.
	 */
	private static void advanceTurnsAfterHuman() {
		while (!controller.isGameOver()) {
			controller.nextTurn();
			Player current = controller.getCurrentPlayer();
			List<Player> players = controller.getPlayers();
			int idx = players.indexOf(current);
			AIPlayer ai = aiPlayers.get(idx);
			if (ai == null) {
				// next player is human, stop here
				break;
			}
			// Let AI take its move. AI strategies always choose legal moves.
			ai.makeMove(controller);
			if (controller.isGameOver()) {
				break;
			}
		}
	}

	private static String buildGameStateJson() {
		StringBuilder sb = new StringBuilder();
		GameBoard board = controller.getBoard();
		Player current = controller.getCurrentPlayer();

		sb.append("{");
		sb.append("\"currentPlayer\":\"").append(escape(current.getName())).append("\",");
		sb.append("\"gameOver\":").append(controller.isGameOver()).append(",");
		if (controller.isGameOver() && controller.getWinner() != null) {
			sb.append("\"winner\":\"").append(escape(controller.getWinner().getName())).append("\",");
		}

		// Players
		sb.append("\"players\":[");
		List<Player> players = controller.getPlayers();
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get(i);
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{");
			sb.append("\"name\":\"").append(escape(p.getName())).append("\",");
			sb.append("\"human\":").append(p.isHuman()).append(",");
			sb.append("\"prestige\":").append(p.getPrestigePoints()).append(",");
			sb.append("\"gems\":{");
			boolean firstGem = true;
			for (GemType type : GemType.values()) {
				int count = p.getGems().getOrDefault(type, 0);
				if (count > 0) {
					if (!firstGem) {
						sb.append(",");
					}
					firstGem = false;
					sb.append("\"").append(type.name()).append("\":").append(count);
				}
			}
			sb.append("},");
			sb.append("\"bonuses\":{");
			boolean firstBonus = true;
			for (GemType type : GemType.values()) {
				if (type == GemType.GOLD) {
					continue;
				}
				int count = p.getBonuses().getOrDefault(type, 0);
				if (count > 0) {
					if (!firstBonus) {
						sb.append(",");
					}
					firstBonus = false;
					sb.append("\"").append(type.name()).append("\":").append(count);
				}
			}
			sb.append("},");
			sb.append("\"reservedCount\":").append(p.getReservedCards().size()).append(",");
			sb.append("\"noble\":");
			if (p.getVisitedNoble() != null) {
				sb.append("\"").append(escape(p.getVisitedNoble().getName())).append("\"");
			} else {
				sb.append("null");
			}
			sb.append("}");
		}
		sb.append("],");

		// Board gems
		sb.append("\"gems\":{");
		boolean firstBoardGem = true;
		for (GemType type : GemType.values()) {
			int count = board.getGemCount(type);
			if (count > 0) {
				if (!firstBoardGem) {
					sb.append(",");
				}
				firstBoardGem = false;
				sb.append("\"").append(type.name()).append("\":").append(count);
			}
		}
		sb.append("},");

		// Visible cards by level (structured JSON so the UI can render nicely)
		sb.append("\"levels\":{");
		for (int level = 1; level <= 3; level++) {
			if (level > 1) {
				sb.append(",");
			}
			sb.append("\"").append(level).append("\":[");
			List<Card> cards = board.getVisibleCards(level);
			for (int i = 0; i < cards.size(); i++) {
				if (i > 0) {
					sb.append(",");
				}
				Card card = cards.get(i);
				sb.append("{");
				sb.append("\"id\":").append(card.getCardId()).append(",");
				sb.append("\"points\":").append(card.getPrestigePoints()).append(",");
				sb.append("\"bonusGem\":\"").append(card.getBonusGem().name()).append("\",");
				sb.append("\"bonusAbbr\":\"").append(card.getBonusGem().getAbbreviation()).append("\",");
				sb.append("\"affordable\":").append(card.canAfford(current.getGems(), current.getBonuses())).append(",");
				sb.append("\"clickHint\":\"Click to buy this card.\",");

				// Only include non-zero costs so we don't get "O:0, D:0, E:0 ..." in the UI.
				sb.append("\"cost\":{");
				boolean firstCost = true;
				for (Map.Entry<GemType, Integer> entry : card.getCost().entrySet()) {
					int qty = entry.getValue() == null ? 0 : entry.getValue().intValue();
					if (qty <= 0) {
						continue;
					}
					if (!firstCost) {
						sb.append(",");
					}
					firstCost = false;
					sb.append("\"").append(entry.getKey().name()).append("\":").append(qty);
				}
				sb.append("}");

				sb.append("}");
			}
			sb.append("]");
		}
		sb.append("},");

		// Nobles (structured JSON)
		sb.append("\"nobles\":[");
		List<Noble> nobles = board.getAvailableNobles();
		for (int i = 0; i < nobles.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			Noble noble = nobles.get(i);
			sb.append("{");
			sb.append("\"id\":").append(noble.getNobleId()).append(",");
			sb.append("\"name\":\"").append(escape(noble.getName())).append("\",");
			sb.append("\"points\":").append(noble.getPrestigePoints()).append(",");
			sb.append("\"requirements\":{");
			boolean firstReq = true;
			for (Map.Entry<GemType, Integer> entry : noble.getRequirement().entrySet()) {
				int qty = entry.getValue() == null ? 0 : entry.getValue().intValue();
				if (qty <= 0) {
					continue;
				}
				if (!firstReq) {
					sb.append(",");
				}
				firstReq = false;
				sb.append("\"").append(entry.getKey().name()).append("\":").append(qty);
			}
			sb.append("}");
			sb.append("}");
		}
		sb.append("]");

		sb.append("}");
		return sb.toString();
	}

	private static String escape(String value) {
		return value.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	private static String toJson(Map<String, Object> map) {
		StringBuilder sb = new StringBuilder();
		sb.append("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : map.entrySet()) {
			if (!first) {
				sb.append(",");
			}
			first = false;
			sb.append("\"").append(escape(entry.getKey())).append("\":");
			Object v = entry.getValue();
			if (v == null) {
				sb.append("null");
			} else if (v instanceof Number || v instanceof Boolean) {
				sb.append(v.toString());
			} else {
				sb.append("\"").append(escape(String.valueOf(v))).append("\"");
			}
		}
		sb.append("}");
		return sb.toString();
	}

	private static void sendResponse(HttpExchange exchange, int status, String body, String contentType)
			throws IOException {
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		Headers headers = exchange.getResponseHeaders();
		headers.set("Content-Type", contentType);
		headers.set("Access-Control-Allow-Origin", "*");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}
}

