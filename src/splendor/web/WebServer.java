package splendor.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

	private static final String DEFAULT_ROOM = "Room A";
	private static final Map<String, GameSession> sessions = new HashMap<>();

	private static class GameSession {
		GameController controller;
		Map<Integer, AIPlayer> aiPlayers = new HashMap<>();
		List<String> actionLog = new ArrayList<>();
		int turnNumber = 1;
		boolean gameStarted = false;
		String ownerName = "Host";
		int lobbyNumPlayers = 2;
		String[] lobbyTypes = new String[] { "human", "human", "human", "human" };
		Map<String, Boolean> readyByPlayer = new LinkedHashMap<>();
		Map<String, String> aiByName = new LinkedHashMap<>();
	}

	private static void addActionLog(GameSession session, String text) {
		if (text == null || text.trim().isEmpty()) {
			return;
		}
		session.actionLog.add(text.trim());
		// Keep the feed short and readable.
		if (session.actionLog.size() > 15) {
			session.actionLog = new ArrayList<>(
				session.actionLog.subList(session.actionLog.size() - 15, session.actionLog.size()));
		}
	}

	private static String cleanRoomName(String room) {
		if (room == null) {
			return DEFAULT_ROOM;
		}
		String trimmed = room.trim();
		if (trimmed.isEmpty()) {
			return DEFAULT_ROOM;
		}
		return trimmed;
	}

	private static String getRoomFromQuery(HttpExchange exchange) {
		URI uri = exchange.getRequestURI();
		if (uri == null || uri.getQuery() == null) {
			return DEFAULT_ROOM;
		}
		String query = uri.getQuery();
		for (String kv : query.split("&")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = kv.substring(0, eq);
			String value = kv.substring(eq + 1);
			if ("room".equalsIgnoreCase(key)) {
				try {
					return cleanRoomName(java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name()));
				} catch (Exception e) {
					return cleanRoomName(value);
				}
			}
		}
		return DEFAULT_ROOM;
	}

	private static GameSession getOrCreateSession(String roomName) {
		String room = cleanRoomName(roomName);
		GameSession existing = sessions.get(room);
		if (existing != null) {
			return existing;
		}
		GameSession created = new GameSession();
		startNewGame(created, 2, new String[] { "human", "human", "human", "human" });
		sessions.put(room, created);
		return created;
	}

	private static String generateRoomCode() {
		String raw = UUID.randomUUID().toString().replace("-", "").toUpperCase();
		return "SP-" + raw.substring(0, 6);
	}

	private static void initializeLobby(GameSession session, String ownerName, int numPlayers, String[] types) {
		session.ownerName = (ownerName == null || ownerName.trim().isEmpty()) ? "Host" : ownerName.trim();
		session.lobbyNumPlayers = Math.max(2, Math.min(4, numPlayers));
		session.lobbyTypes = new String[] {
			types[0] == null ? "human" : types[0].toLowerCase(),
			types[1] == null ? "human" : types[1].toLowerCase(),
			types[2] == null ? "human" : types[2].toLowerCase(),
			types[3] == null ? "human" : types[3].toLowerCase()
		};
		session.readyByPlayer.clear();
		session.readyByPlayer.put(session.ownerName, false);
		session.aiByName.clear();
		session.gameStarted = false;
		session.actionLog.clear();
		addActionLog(session, "Lobby created by " + session.ownerName + ".");
	}

	private static boolean canStartLobby(GameSession session) {
		if (session.readyByPlayer.isEmpty()) {
			return false;
		}
		int joined = session.readyByPlayer.size();
		if (joined != session.lobbyNumPlayers) {
			return false;
		}
		for (Boolean ready : session.readyByPlayer.values()) {
			if (!Boolean.TRUE.equals(ready)) {
				return false;
			}
		}
		return true;
	}

	public static void main(String[] args) throws IOException {
		int port = 8080;
		String envPort = System.getenv("PORT");
		if (envPort != null && !envPort.trim().isEmpty()) {
			try {
				port = Integer.parseInt(envPort.trim());
			} catch (NumberFormatException ignored) {
				port = 8080;
			}
		}
		System.out.println("Starting Splendor Web Server on http://localhost:" + port);

		// Initialize default room.
		getOrCreateSession(DEFAULT_ROOM);

		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

		// Static files
		server.createContext("/", new StaticFileHandler("web/index.html", "text/html; charset=utf-8"));
		server.createContext("/styles.css", new StaticFileHandler("web/styles.css", "text/css; charset=utf-8"));
		server.createContext("/app.js", new StaticFileHandler("web/app.js", "application/javascript; charset=utf-8"));

		// API endpoints
		server.createContext("/api/state", new StateHandler());
		server.createContext("/api/action", new ActionHandler());
		server.createContext("/api/newgame", new NewGameHandler());
		server.createContext("/api/quit", new QuitHandler());
		server.createContext("/api/room/create", new RoomCreateHandler());
		server.createContext("/api/room/join", new RoomJoinHandler());
		server.createContext("/api/room/ready", new RoomReadyHandler());
		server.createContext("/api/room/rename", new RoomRenameHandler());
		server.createContext("/api/room/addai", new RoomAddAiHandler());
		server.createContext("/api/room/kick", new RoomKickHandler());
		server.createContext("/api/room/start", new RoomStartHandler());

		server.setExecutor(null);
		server.start();
	}

	/**
	 * Initializes a new game with the given number of players and AI settings.
	 *
	 * @param numPlayers number of players (2-4)
	 */
	private static void startNewGame(GameSession session, int numPlayers, String[] types) {
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
		session.controller = new GameController(numPlayers, playerNames, playerTypes);

		// Initialize AI map
		session.aiPlayers.clear();
		for (int i = 0; i < numPlayers; i++) {
			String t = types[i] == null ? "human" : types[i].toLowerCase();
			if (t.equals("easy")) {
				session.aiPlayers.put(i, new AIPlayer(new EasyAIStrategy()));
			} else if (t.equals("medium")) {
				session.aiPlayers.put(i, new AIPlayer(new MediumAIStrategy()));
			} else if (t.equals("hard")) {
				session.aiPlayers.put(i, new AIPlayer(new HardAIStrategy()));
			}
		}
		session.actionLog.clear();
		addActionLog(session, "New game started.");
		session.turnNumber = 1;
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
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = extractStringField(body, "room", DEFAULT_ROOM);
			GameSession session = getOrCreateSession(room);
			int numPlayers = extractIntField(body, "numPlayers", 2);
			String[] types = new String[4];
			types[0] = extractStringField(body, "p1Type", "human");
			types[1] = extractStringField(body, "p2Type", "human");
			types[2] = extractStringField(body, "p3Type", "human");
			types[3] = extractStringField(body, "p4Type", "human");
			startNewGame(session, numPlayers, types);
			initializeLobby(session, "Host", numPlayers, types);
			session.gameStarted = true;

			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("numPlayers", numPlayers);
			resp.put("p1Type", types[0]);
			resp.put("p2Type", types[1]);
			resp.put("p3Type", types[2]);
			resp.put("p4Type", types[3]);
			resp.put("room", cleanRoomName(room));
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
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = extractStringField(body, "room", DEFAULT_ROOM);
			GameSession session = getOrCreateSession(room);
			startNewGame(session, 2, new String[] { "human", "human", "human", "human" });
			initializeLobby(session, "Host", 2, new String[] { "human", "human", "human", "human" });
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", cleanRoomName(room));
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	/**
	 * Returns the current game state as JSON for the web UI.
	 */
	private static class StateHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String room = getRoomFromQuery(exchange);
			GameSession session = getOrCreateSession(room);
			String json = buildGameStateJson(session);
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
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}

			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = extractStringField(body, "room", DEFAULT_ROOM);
			GameSession session = getOrCreateSession(room);
			Map<String, Object> result = handleAction(session, body);
			String json = toJson(result);
			sendResponse(exchange, 200, json, "application/json; charset=utf-8");
		}
	}

	private static class RoomCreateHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String requested = extractStringField(body, "room", "");
			String room = cleanRoomName((requested == null || requested.trim().isEmpty()) ? generateRoomCode() : requested);
			String owner = extractStringField(body, "ownerName", "Host");
			int numPlayers = extractIntField(body, "numPlayers", 2);
			String[] types = new String[4];
			types[0] = extractStringField(body, "p1Type", "human");
			types[1] = extractStringField(body, "p2Type", "human");
			types[2] = extractStringField(body, "p3Type", "human");
			types[3] = extractStringField(body, "p4Type", "human");

			GameSession session = getOrCreateSession(room);
			initializeLobby(session, owner, numPlayers, types);
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			resp.put("owner", session.ownerName);
			resp.put("numPlayers", session.lobbyNumPlayers);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomJoinHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String name = extractStringField(body, "name", "Guest");
			if (name == null || name.trim().isEmpty()) {
				name = "Guest";
			}
			name = name.trim();

			GameSession session = getOrCreateSession(room);
			if (!session.readyByPlayer.containsKey(name) && session.readyByPlayer.size() >= session.lobbyNumPlayers) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Room is full.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (session.aiByName.containsKey(name)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Name conflicts with AI bot.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			session.readyByPlayer.putIfAbsent(name, false);
			addActionLog(session, name + " joined lobby.");
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			resp.put("name", name);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomReadyHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String name = extractStringField(body, "name", "Guest");
			boolean ready = extractBooleanField(body, "ready", false);
			GameSession session = getOrCreateSession(room);
			if (!session.readyByPlayer.containsKey(name)) {
				session.readyByPlayer.put(name, false);
			}
			session.readyByPlayer.put(name, ready);
			addActionLog(session, name + (ready ? " is ready." : " is not ready."));
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			resp.put("name", name);
			resp.put("ready", ready);
			resp.put("canStart", canStartLobby(session));
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomStartHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String owner = extractStringField(body, "ownerName", "");
			GameSession session = getOrCreateSession(room);
			if (!session.ownerName.equals(owner)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Only owner can start.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (!canStartLobby(session)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Room must be full and everyone must be ready.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			String[] types = new String[] { "human", "human", "human", "human" };
			int idx = 1;
			for (String diff : session.aiByName.values()) {
				if (idx >= session.lobbyNumPlayers) {
					break;
				}
				types[idx] = diff;
				idx++;
			}
			startNewGame(session, session.lobbyNumPlayers, types);
			session.gameStarted = true;
			addActionLog(session, "Match started by " + session.ownerName + ".");
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomAddAiHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String owner = extractStringField(body, "ownerName", "");
			String difficulty = extractStringField(body, "difficulty", "easy").toLowerCase();
			if (!difficulty.equals("easy") && !difficulty.equals("medium") && !difficulty.equals("hard")) {
				difficulty = "easy";
			}
			GameSession session = getOrCreateSession(room);
			Map<String, Object> resp = new HashMap<>();
			if (!session.ownerName.equals(owner)) {
				resp.put("success", false);
				resp.put("message", "Only owner can add AI.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (session.readyByPlayer.size() >= session.lobbyNumPlayers) {
				resp.put("success", false);
				resp.put("message", "No free slots left.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			String aiName = "AI Bot " + (session.aiByName.size() + 1);
			while (session.readyByPlayer.containsKey(aiName)) {
				aiName = "AI Bot " + (session.aiByName.size() + 2);
			}
			session.aiByName.put(aiName, difficulty);
			session.readyByPlayer.put(aiName, true);
			addActionLog(session, aiName + " added (" + difficulty + ").");
			resp.put("success", true);
			resp.put("name", aiName);
			resp.put("difficulty", difficulty);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomKickHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String owner = extractStringField(body, "ownerName", "");
			String target = extractStringField(body, "targetName", "");
			GameSession session = getOrCreateSession(room);
			Map<String, Object> resp = new HashMap<>();
			if (!session.ownerName.equals(owner)) {
				resp.put("success", false);
				resp.put("message", "Only owner can kick.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (target == null || target.trim().isEmpty() || target.equals(session.ownerName)) {
				resp.put("success", false);
				resp.put("message", "Invalid kick target.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (!session.readyByPlayer.containsKey(target)) {
				resp.put("success", false);
				resp.put("message", "Player not found.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			session.readyByPlayer.remove(target);
			session.aiByName.remove(target);
			addActionLog(session, target + " was removed from lobby.");
			resp.put("success", true);
			resp.put("target", target);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomRenameHandler implements HttpHandler {
		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (handleCorsPreflight(exchange)) {
				return;
			}
			if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
			String room = cleanRoomName(extractStringField(body, "room", DEFAULT_ROOM));
			String oldName = extractStringField(body, "oldName", "");
			String newName = extractStringField(body, "newName", "");
			GameSession session = getOrCreateSession(room);

			if (newName == null || newName.trim().isEmpty()) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "New name cannot be empty.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			newName = newName.trim();
			if (!oldName.equals(newName) && session.readyByPlayer.containsKey(newName)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Name already used in this room.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}

			boolean oldReady = false;
			if (session.readyByPlayer.containsKey(oldName)) {
				oldReady = Boolean.TRUE.equals(session.readyByPlayer.remove(oldName));
			}
			session.readyByPlayer.put(newName, oldReady);
			if (session.ownerName.equals(oldName)) {
				session.ownerName = newName;
			}
			addActionLog(session, oldName + " is now " + newName + ".");
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("oldName", oldName);
			resp.put("newName", newName);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static Map<String, Object> handleAction(GameSession session, String body) {
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
		String actor = session.controller.getCurrentPlayer().getName();
		String actionSummary = null;

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
				GameRules.ValidationResult vr = session.controller.getRules().validateTakeGems(
					gemsToTake,
					session.controller.getBoard().getAvailableGems(),
					session.controller.getCurrentPlayer().getGems()
				);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = session.controller.takeGems(gemsToTake);
					message = success ? "Gems taken." : "Could not take gems.";
					if (success) {
						StringBuilder taken = new StringBuilder();
						for (Map.Entry<GemType, Integer> e : gemsToTake.entrySet()) {
							if (taken.length() > 0) {
								taken.append(", ");
							}
							taken.append(e.getKey().getAbbreviation()).append("x").append(e.getValue());
						}
						actionSummary = actor + " took gems: " + taken + ".";
					}
				}
			} else {
				message = "No valid gems specified";
			}
		} else if (lower.contains("\"type\"") && lower.contains("reserve")) {
			int level = extractIntField(body, "level", 1);
			int index = extractIntField(body, "index", 0);
			GameRules.ValidationResult vr = session.controller.getRules().validateReserveCard(session.controller.getCurrentPlayer());
			List<Card> visible = session.controller.getBoard().getVisibleCards(level);
			if (!vr.isValid()) {
				success = false;
				message = vr.getMessage();
			} else if (index < 0 || index >= visible.size()) {
				success = false;
				message = "Invalid card index for this level.";
			} else {
				success = session.controller.reserveCard(level, index);
				message = success ? "Card reserved." : "Cannot reserve this card.";
				if (success) {
					actionSummary = actor + " reserved a card (L" + level + " #" + index + ").";
				}
			}
		} else if (lower.contains("\"type\"") && lower.contains("purchasevisible")) {
			int level = extractIntField(body, "level", 1);
			int index = extractIntField(body, "index", 0);
			List<Card> visible = session.controller.getBoard().getVisibleCards(level);
			if (index < 0 || index >= visible.size()) {
				success = false;
				message = "Invalid card index for this level.";
			} else {
				Card card = visible.get(index);
				Map<GemType, Integer> payment = session.controller.getRules().calculatePayment(card, session.controller.getCurrentPlayer());
				GameRules.ValidationResult vr = session.controller.getRules().validatePurchaseCard(card, session.controller.getCurrentPlayer(), payment);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = session.controller.purchaseVisibleCard(level, index);
					message = success ? "Card purchased." : "Cannot purchase this card.";
					if (success) {
						actionSummary = actor + " purchased a visible card (L" + level + " #" + index + ").";
					}
				}
			}
		} else if (lower.contains("\"type\"") && lower.contains("purchasereserved")) {
			int index = extractIntField(body, "index", 0);
			List<Card> reserved = session.controller.getCurrentPlayer().getReservedCards();
			if (index < 0 || index >= reserved.size()) {
				success = false;
				message = "Invalid reserved card index.";
			} else {
				Card card = reserved.get(index);
				Map<GemType, Integer> payment = session.controller.getRules().calculatePayment(card, session.controller.getCurrentPlayer());
				GameRules.ValidationResult vr = session.controller.getRules().validatePurchaseCard(card, session.controller.getCurrentPlayer(), payment);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = session.controller.purchaseReservedCard(index);
					message = success ? "Reserved card purchased." : "Cannot purchase this reserved card.";
					if (success) {
						actionSummary = actor + " purchased reserved card #" + index + ".";
					}
				}
			}
		}

		// Advance turns (including AI turns) if action succeeded
		if (success && !session.controller.isGameOver()) {
			if (actionSummary != null) {
				addActionLog(session, actionSummary);
			}
			advanceTurnsAfterHuman(session);
		}

		response.put("success", success);
		response.put("message", message);
		response.put("gameOver", session.controller.isGameOver());
		if (session.controller.isGameOver() && session.controller.getWinner() != null) {
			response.put("winner", session.controller.getWinner().getName());
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
	private static void advanceTurnsAfterHuman(GameSession session) {
		while (!session.controller.isGameOver()) {
			session.controller.nextTurn();
			session.turnNumber++;
			Player current = session.controller.getCurrentPlayer();
			List<Player> players = session.controller.getPlayers();
			int idx = players.indexOf(current);
			AIPlayer ai = session.aiPlayers.get(idx);
			if (ai == null) {
				// next player is human, stop here
				break;
			}
			// Let AI take its move. AI strategies always choose legal moves.
			String aiAction = ai.makeMove(session.controller);
			addActionLog(session, current.getName() + ": " + (aiAction == null ? "took a turn." : aiAction));
			if (session.controller.isGameOver()) {
				break;
			}
		}
	}

	private static String buildGameStateJson(GameSession session) {
		StringBuilder sb = new StringBuilder();
		GameBoard board = session.controller.getBoard();
		Player current = session.controller.getCurrentPlayer();

		sb.append("{");
		sb.append("\"currentPlayer\":\"").append(escape(current.getName())).append("\",");
		sb.append("\"turnNumber\":").append(session.turnNumber).append(",");
		sb.append("\"isHumanTurn\":").append(current.isHuman()).append(",");
		sb.append("\"gameOver\":").append(session.controller.isGameOver()).append(",");
		if (session.controller.isGameOver() && session.controller.getWinner() != null) {
			sb.append("\"winner\":\"").append(escape(session.controller.getWinner().getName())).append("\",");
		}

		// Players
		sb.append("\"players\":[");
		List<Player> players = session.controller.getPlayers();
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
			sb.append("\"reserved\":[");
			List<Card> resCards = p.getReservedCards();
			for (int ri = 0; ri < resCards.size(); ri++) {
				if (ri > 0) {
					sb.append(",");
				}
				Card rc = resCards.get(ri);
				sb.append("{");
				sb.append("\"id\":").append(rc.getCardId()).append(",");
				sb.append("\"level\":").append(rc.getLevel()).append(",");
				sb.append("\"points\":").append(rc.getPrestigePoints()).append(",");
				sb.append("\"bonusGem\":\"").append(rc.getBonusGem().name()).append("\",");
				sb.append("\"bonusAbbr\":\"").append(rc.getBonusGem().getAbbreviation()).append("\",");
				boolean canBuy = (p == current)
						&& rc.canAfford(current.getGems(), current.getBonuses());
				sb.append("\"affordable\":").append(canBuy).append(",");
				sb.append("\"cost\":{");
				boolean firstRc = true;
				for (Map.Entry<GemType, Integer> entry : rc.getCost().entrySet()) {
					int qty = entry.getValue() == null ? 0 : entry.getValue().intValue();
					if (qty <= 0) {
						continue;
					}
					if (!firstRc) {
						sb.append(",");
					}
					firstRc = false;
					sb.append("\"").append(entry.getKey().name()).append("\":").append(qty);
				}
				sb.append("}}");
			}
			sb.append("],");
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
		sb.append("],");

		// Recent actions feed
		sb.append("\"recentActions\":[");
		for (int i = 0; i < session.actionLog.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("\"").append(escape(session.actionLog.get(i))).append("\"");
		}
		sb.append("]");
		sb.append(",");
		sb.append("\"room\":\"").append(escape(DEFAULT_ROOM)).append("\",");
		sb.append("\"lobby\":{");
		sb.append("\"owner\":\"").append(escape(session.ownerName)).append("\",");
		sb.append("\"gameStarted\":").append(session.gameStarted).append(",");
		sb.append("\"numPlayers\":").append(session.lobbyNumPlayers).append(",");
		sb.append("\"canStart\":").append(canStartLobby(session)).append(",");
		sb.append("\"players\":[");
		boolean firstLobbyPlayer = true;
		for (Map.Entry<String, Boolean> e : session.readyByPlayer.entrySet()) {
			if (!firstLobbyPlayer) {
				sb.append(",");
			}
			firstLobbyPlayer = false;
			sb.append("{");
			sb.append("\"name\":\"").append(escape(e.getKey())).append("\",");
			sb.append("\"ready\":").append(Boolean.TRUE.equals(e.getValue())).append(",");
			sb.append("\"isAi\":").append(session.aiByName.containsKey(e.getKey()));
			if (session.aiByName.containsKey(e.getKey())) {
				sb.append(",\"aiDifficulty\":\"").append(escape(session.aiByName.get(e.getKey()))).append("\"");
			}
			sb.append("}");
		}
		sb.append("]");
		sb.append("}");

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
		headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
		headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
		headers.set("Access-Control-Max-Age", "86400");
		exchange.sendResponseHeaders(status, bytes.length);
		try (OutputStream os = exchange.getResponseBody()) {
			os.write(bytes);
		}
	}

	private static boolean handleCorsPreflight(HttpExchange exchange) throws IOException {
		if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
			Headers headers = exchange.getResponseHeaders();
			headers.set("Access-Control-Allow-Origin", "*");
			headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
			headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
			headers.set("Access-Control-Max-Age", "86400");
			exchange.sendResponseHeaders(204, -1);
			return true;
		}
		return false;
	}
}

