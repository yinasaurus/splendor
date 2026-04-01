package splendor.web;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Properties;

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
	private static final int ACTION_LOG_LIMIT = 12;
	private static final int AFK_AI_THRESHOLD_SECONDS = 90;
	private static final Map<String, GameSession> sessions = new HashMap<>();
	private static final Path LOBBY_SNAPSHOT_FILE = Paths.get("data", "web_lobbies.properties");

	private static class LobbySnapshot {
		String ownerName = "Host";
		int lobbyNumPlayers = 2;
		boolean gameStarted = false;
		LinkedHashMap<String, Boolean> readyByPlayer = new LinkedHashMap<>();
		LinkedHashMap<String, String> aiByName = new LinkedHashMap<>();
	}

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
		Map<String, String> seatTokenByPlayer = new HashMap<>();
		Map<String, Long> lastSeenByPlayer = new HashMap<>();
		Set<String> forcedAiByName = new HashSet<>();
		Map<String, String> forcedAiDifficultyByName = new HashMap<>();
	}

	private static void addActionLog(GameSession session, String text) {
		if (text == null || text.trim().isEmpty()) {
			return;
		}
		String normalized = text.trim();
		// Skip immediate duplicates to keep the feed readable.
		if (!session.actionLog.isEmpty()) {
			String last = session.actionLog.get(session.actionLog.size() - 1);
			if (normalized.equals(last)) {
				return;
			}
		}
		session.actionLog.add(normalized);
		// Keep the feed short and readable.
		if (session.actionLog.size() > ACTION_LOG_LIMIT) {
			session.actionLog = new ArrayList<>(
				session.actionLog.subList(session.actionLog.size() - ACTION_LOG_LIMIT, session.actionLog.size()));
		}
	}

	private static void markPlayerSeen(GameSession session, String rawName) {
		if (session == null || rawName == null) {
			return;
		}
		String name = rawName.trim();
		if (name.isEmpty()) {
			return;
		}
		session.lastSeenByPlayer.put(name, System.currentTimeMillis());
		if (session.forcedAiByName.contains(name)) {
			session.forcedAiByName.remove(name);
			session.forcedAiDifficultyByName.remove(name);
			addActionLog(session, name + " returned and resumed control.");
		}
	}

	private static String enc(String text) {
		if (text == null) {
			return "";
		}
		try {
			return java.net.URLEncoder.encode(text, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return text;
		}
	}

	private static String dec(String text) {
		if (text == null) {
			return "";
		}
		try {
			return java.net.URLDecoder.decode(text, StandardCharsets.UTF_8.name());
		} catch (Exception e) {
			return text;
		}
	}

	private static String encodeSnapshot(LobbySnapshot snap) {
		StringBuilder players = new StringBuilder();
		boolean first = true;
		for (Map.Entry<String, Boolean> e : snap.readyByPlayer.entrySet()) {
			if (!first) players.append(",");
			first = false;
			players.append(enc(e.getKey())).append("~").append(Boolean.TRUE.equals(e.getValue()) ? "1" : "0");
		}
		StringBuilder ai = new StringBuilder();
		first = true;
		for (Map.Entry<String, String> e : snap.aiByName.entrySet()) {
			if (!first) ai.append(",");
			first = false;
			ai.append(enc(e.getKey())).append("~").append(enc(e.getValue()));
		}
		return enc(snap.ownerName) + "|" + snap.lobbyNumPlayers + "|" + (snap.gameStarted ? "1" : "0") + "|" + players + "|" + ai;
	}

	private static LobbySnapshot decodeSnapshot(String raw) {
		if (raw == null || raw.trim().isEmpty()) {
			return null;
		}
		String[] parts = raw.split("\\|", -1);
		if (parts.length < 5) {
			return null;
		}
		LobbySnapshot snap = new LobbySnapshot();
		snap.ownerName = dec(parts[0]);
		try {
			snap.lobbyNumPlayers = Math.max(2, Math.min(4, Integer.parseInt(parts[1])));
		} catch (NumberFormatException e) {
			snap.lobbyNumPlayers = 2;
		}
		snap.gameStarted = "1".equals(parts[2]);
		if (!parts[3].isEmpty()) {
			for (String item : parts[3].split(",")) {
				if (item == null || item.isEmpty()) continue;
				String[] kv = item.split("~", -1);
				if (kv.length < 2) continue;
				String name = dec(kv[0]).trim();
				if (name.isEmpty()) continue;
				snap.readyByPlayer.put(name, "1".equals(kv[1]));
			}
		}
		if (!parts[4].isEmpty()) {
			for (String item : parts[4].split(",")) {
				if (item == null || item.isEmpty()) continue;
				String[] kv = item.split("~", -1);
				if (kv.length < 2) continue;
				String name = dec(kv[0]).trim();
				String diff = dec(kv[1]).trim().toLowerCase();
				if (name.isEmpty() || diff.isEmpty()) continue;
				snap.aiByName.put(name, diff);
			}
		}
		return snap;
	}

	private static LobbySnapshot snapshotFromSession(GameSession session) {
		LobbySnapshot snap = new LobbySnapshot();
		snap.ownerName = session.ownerName;
		snap.lobbyNumPlayers = session.lobbyNumPlayers;
		snap.gameStarted = session.gameStarted;
		snap.readyByPlayer.putAll(session.readyByPlayer);
		snap.aiByName.putAll(session.aiByName);
		return snap;
	}

	private static Map<String, LobbySnapshot> loadSnapshots() {
		Map<String, LobbySnapshot> out = new HashMap<>();
		try {
			if (!Files.exists(LOBBY_SNAPSHOT_FILE)) {
				return out;
			}
			Properties p = new Properties();
			try (java.io.InputStream in = Files.newInputStream(LOBBY_SNAPSHOT_FILE)) {
				p.load(in);
			}
			for (String room : p.stringPropertyNames()) {
				LobbySnapshot snap = decodeSnapshot(p.getProperty(room));
				if (snap != null) {
					out.put(cleanRoomName(room), snap);
				}
			}
		} catch (Exception ignored) {
		}
		return out;
	}

	private static void saveSnapshots() {
		try {
			Files.createDirectories(LOBBY_SNAPSHOT_FILE.getParent());
			Properties p = new Properties();
			for (Map.Entry<String, GameSession> e : sessions.entrySet()) {
				String room = cleanRoomName(e.getKey());
				if (room.isEmpty()) continue;
				LobbySnapshot snap = snapshotFromSession(e.getValue());
				p.setProperty(room, encodeSnapshot(snap));
			}
			try (java.io.OutputStream out = Files.newOutputStream(LOBBY_SNAPSHOT_FILE)) {
				p.store(out, "Splendor web lobby snapshots");
			}
		} catch (Exception ignored) {
		}
	}

	private static String canonicalizeLobbyName(GameSession session, String rawName) {
		if (rawName == null) {
			return "";
		}
		String name = rawName.trim();
		if (name.isEmpty() || session == null) {
			return name;
		}
		for (String existing : session.readyByPlayer.keySet()) {
			if (existing != null && existing.equalsIgnoreCase(name)) {
				return existing;
			}
		}
		for (String existingAi : session.aiByName.keySet()) {
			if (existingAi != null && existingAi.equalsIgnoreCase(name)) {
				return existingAi;
			}
		}
		return name;
	}

	private static String issueSeatToken(GameSession session, String playerName) {
		if (session == null || playerName == null) {
			return "";
		}
		String name = playerName.trim();
		if (name.isEmpty()) {
			return "";
		}
		String existing = session.seatTokenByPlayer.get(name);
		if (existing != null && !existing.trim().isEmpty()) {
			return existing;
		}
		String token = UUID.randomUUID().toString();
		session.seatTokenByPlayer.put(name, token);
		return token;
	}

	private static boolean hasValidSeatToken(GameSession session, String playerName, String token) {
		if (session == null || playerName == null) {
			return false;
		}
		String name = playerName.trim();
		if (name.isEmpty()) {
			return false;
		}
		String expected = session.seatTokenByPlayer.get(name);
		if (expected == null || expected.trim().isEmpty()) {
			// Backward-compatible fallback: if no token was issued yet, allow.
			return true;
		}
		return expected.equals(token == null ? "" : token.trim());
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
		String room = getQueryParam(exchange, "room");
		return room == null ? DEFAULT_ROOM : cleanRoomName(room);
	}

	private static String getQueryParam(HttpExchange exchange, String keyToFind) {
		URI uri = exchange.getRequestURI();
		if (uri == null || uri.getQuery() == null) {
			return null;
		}
		String query = uri.getQuery();
		for (String kv : query.split("&")) {
			int eq = kv.indexOf('=');
			if (eq <= 0) {
				continue;
			}
			String key = kv.substring(0, eq);
			String value = kv.substring(eq + 1);
			if (keyToFind.equalsIgnoreCase(key)) {
				try {
					return java.net.URLDecoder.decode(value, StandardCharsets.UTF_8.name());
				} catch (Exception e) {
					return value;
				}
			}
		}
		return null;
	}

	private static GameSession getOrCreateSession(String roomName) {
		String room = cleanRoomName(roomName);
		GameSession existing = sessions.get(room);
		if (existing != null) {
			return existing;
		}
		GameSession created = new GameSession();
		Map<String, LobbySnapshot> snapshots = loadSnapshots();
		LobbySnapshot snap = snapshots.get(room);
		if (snap != null) {
			initializeLobby(created, snap.ownerName, snap.lobbyNumPlayers, new String[] { "human", "human", "human", "human" });
			created.readyByPlayer.clear();
			created.readyByPlayer.putAll(snap.readyByPlayer);
			created.aiByName.clear();
			created.aiByName.putAll(snap.aiByName);
			ensureLobbyOwner(created, snap.ownerName);
			if (snap.gameStarted && created.readyByPlayer.size() >= 2) {
				List<String> lobbyOrder = new ArrayList<>(created.readyByPlayer.keySet());
				int actualPlayers = Math.min(created.lobbyNumPlayers, lobbyOrder.size());
				String[] types = new String[] { "human", "human", "human", "human" };
				for (int i = 0; i < actualPlayers; i++) {
					String name = lobbyOrder.get(i);
					String diff = created.aiByName.get(name);
					types[i] = (diff == null || diff.trim().isEmpty()) ? "human" : diff.trim().toLowerCase();
				}
				startNewGame(created, actualPlayers, types, lobbyOrder, false);
				created.gameStarted = true;
				addActionLog(created, "Room recovered after server restart.");
			} else {
				startNewGame(created, Math.max(2, Math.min(4, created.readyByPlayer.size())),
					new String[] { "human", "human", "human", "human" }, new ArrayList<>(created.readyByPlayer.keySet()), false);
				created.gameStarted = false;
			}
		} else {
			startNewGame(created, 2, new String[] { "human", "human", "human", "human" });
		}
		sessions.put(room, created);
		saveSnapshots();
		return created;
	}

	private static GameSession getExistingSession(String roomName) {
		String room = cleanRoomName(roomName);
		return sessions.get(room);
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
		session.seatTokenByPlayer.clear();
		issueSeatToken(session, session.ownerName);
		session.lastSeenByPlayer.clear();
		session.lastSeenByPlayer.put(session.ownerName, System.currentTimeMillis());
		session.aiByName.clear();
		session.forcedAiByName.clear();
		session.forcedAiDifficultyByName.clear();
		session.gameStarted = false;
		session.actionLog.clear();
		addActionLog(session, "Lobby created by " + session.ownerName + ".");
		saveSnapshots();
	}

	private static boolean canStartLobby(GameSession session) {
		// Everyone currently in the lobby (humans + AI) must be ready,
		// but we no longer require that the room be exactly full. This
		// allows the host to start with fewer players than originally
		// selected as long as there are at least two seats filled.
		if (session.readyByPlayer.isEmpty()) {
			return false;
		}
		int joined = session.readyByPlayer.size();
		if (joined < 2) {
			return false;
		}
		if (joined > session.lobbyNumPlayers) {
			return false;
		}
		for (Boolean ready : session.readyByPlayer.values()) {
			if (!Boolean.TRUE.equals(ready)) {
				return false;
			}
		}
		return true;
	}

	private static boolean isGenericSeatName(String name) {
		if (name == null) {
			return false;
		}
		return name.trim().matches("Player\\s+\\d+");
	}

	private static List<String> lobbySeatNames(GameSession session, int limit) {
		List<String> out = new ArrayList<>();
		if (session == null || limit <= 0) {
			return out;
		}
		for (String name : session.readyByPlayer.keySet()) {
			if (out.size() >= limit) {
				break;
			}
			out.add(name);
		}
		return out;
	}

	private static String displayNameForSeat(GameSession session, int seatIndex, Player p, List<String> lobbyNames) {
		if (p == null) {
			return "";
		}
		String original = p.getName();
		if (!isGenericSeatName(original)) {
			return original;
		}
		if (seatIndex >= 0 && seatIndex < lobbyNames.size()) {
			String mapped = lobbyNames.get(seatIndex);
			if (mapped != null && !mapped.trim().isEmpty()) {
				return mapped.trim();
			}
		}
		return original;
	}

	private static void ensureLobbyOwner(GameSession session, String preferredName) {
		if (session == null) {
			return;
		}
		if (session.ownerName != null && session.readyByPlayer.containsKey(session.ownerName)) {
			return;
		}
		String preferred = preferredName == null ? "" : preferredName.trim();
		if (!preferred.isEmpty() && session.readyByPlayer.containsKey(preferred)) {
			session.ownerName = preferred;
			return;
		}
		// Prefer a human player as owner when recovering from stale owner state.
		for (String name : session.readyByPlayer.keySet()) {
			if (!session.aiByName.containsKey(name)) {
				session.ownerName = name;
				return;
			}
		}
		for (String name : session.readyByPlayer.keySet()) {
			session.ownerName = name;
			return;
		}
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
		server.createContext("/config.js", new StaticFileHandler("web/config.js", "application/javascript; charset=utf-8"));
		server.createContext("/media/", new MediaDirectoryHandler());

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
		server.createContext("/api/room/afkai", new RoomAfkAiHandler());

		server.setExecutor(null);
		server.start();
	}

	/**
	 * Initializes a new game with the given number of players and AI settings.
	 *
	 * @param numPlayers number of players (2-4)
	 */
	private static void startNewGame(GameSession session, int numPlayers, String[] types) {
		startNewGame(session, numPlayers, types, null, true);
	}

	private static void startNewGame(GameSession session, int numPlayers, String[] types, List<String> customNames) {
		startNewGame(session, numPlayers, types, customNames, true);
	}

	private static void startNewGame(GameSession session, int numPlayers, String[] types, List<String> customNames,
			boolean logStartMessage) {
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
			String fallback = "Player " + (i + 1);
			String custom = (customNames != null && i < customNames.size()) ? customNames.get(i) : null;
			String resolved = (custom == null || custom.trim().isEmpty()) ? fallback : custom.trim();
			playerNames.add(resolved);
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
		if (logStartMessage) {
			addActionLog(session, "New game started.");
		}
		session.turnNumber = 1;
		session.lastSeenByPlayer.clear();
		session.forcedAiByName.clear();
		session.forcedAiDifficultyByName.clear();
		for (String playerName : playerNames) {
			session.lastSeenByPlayer.put(playerName, System.currentTimeMillis());
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

			Path path = Paths.get(filePath);
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
	 * Serves files under {@code web/media/} at {@code /media/...} (chips/gems sprites, card art, etc.).
	 */
	private static class MediaDirectoryHandler implements HttpHandler {
		private static final Path MEDIA_ROOT = Paths.get("web", "media").toAbsolutePath().normalize();

		@Override
		public void handle(HttpExchange exchange) throws IOException {
			if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
				sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=utf-8");
				return;
			}
			String rawPath = exchange.getRequestURI().getPath();
			if (rawPath == null || !rawPath.startsWith("/media/")) {
				sendResponse(exchange, 404, "Not Found", "text/plain; charset=utf-8");
				return;
			}
			String relative = rawPath.substring("/media/".length()).replace('\\', '/');
			if (relative.isEmpty() || relative.startsWith("/")) {
				sendResponse(exchange, 403, "Forbidden", "text/plain; charset=utf-8");
				return;
			}
			Path file = MEDIA_ROOT;
			for (String segment : relative.split("/")) {
				if (segment.isEmpty() || ".".equals(segment)) {
					continue;
				}
				if ("..".equals(segment)) {
					sendResponse(exchange, 403, "Forbidden", "text/plain; charset=utf-8");
					return;
				}
				file = file.resolve(segment);
			}
			file = file.normalize();
			if (!file.startsWith(MEDIA_ROOT) || !Files.isRegularFile(file)) {
				sendResponse(exchange, 404, "Not Found", "text/plain; charset=utf-8");
				return;
			}
			byte[] bytes = Files.readAllBytes(file);
			Headers headers = exchange.getResponseHeaders();
			headers.set("Content-Type", contentTypeForMediaFile(relative));
			exchange.sendResponseHeaders(200, bytes.length);
			try (OutputStream os = exchange.getResponseBody()) {
				os.write(bytes);
			}
		}

		private static String contentTypeForMediaFile(String name) {
			String lower = name.toLowerCase();
			if (lower.endsWith(".png")) {
				return "image/png";
			}
			if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
				return "image/jpeg";
			}
			if (lower.endsWith(".gif")) {
				return "image/gif";
			}
			if (lower.endsWith(".webp")) {
				return "image/webp";
			}
			if (lower.endsWith(".svg")) {
				return "image/svg+xml";
			}
			return "application/octet-stream";
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
			saveSnapshots();

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
			GameSession session = getExistingSession(room);
			if (session == null) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("sessionMissing", true);
				resp.put("message", "Session expired. Please rejoin or create a new room.");
				resp.put("room", room);
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			String viewerName = canonicalizeLobbyName(session, getQueryParam(exchange, "name"));
			String sessionToken = getQueryParam(exchange, "sessionToken");
			if (viewerName != null && !viewerName.trim().isEmpty() && session.readyByPlayer.containsKey(viewerName)
					&& !hasValidSeatToken(session, viewerName, sessionToken)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "This player seat is already active in another browser.");
				resp.put("room", room);
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			markPlayerSeen(session, viewerName);
			String json = buildGameStateJson(session, room, viewerName);
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
			GameSession session = getExistingSession(room);
			if (session == null) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("sessionMissing", true);
				resp.put("message", "Session expired. Please rejoin or create a new room.");
				resp.put("room", cleanRoomName(room));
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
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
			boolean forceReset = extractBooleanField(body, "forceReset", false);
			String[] types = new String[4];
			types[0] = extractStringField(body, "p1Type", "human");
			types[1] = extractStringField(body, "p2Type", "human");
			types[2] = extractStringField(body, "p3Type", "human");
			types[3] = extractStringField(body, "p4Type", "human");

			GameSession session = sessions.get(room);
			boolean hasActiveMatch = session != null
				&& session.gameStarted
				&& session.controller != null
				&& !session.controller.isGameOver();
			if (hasActiveMatch && !forceReset) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("room", room);
				resp.put("requiresForceReset", true);
				resp.put("message", "An active match already exists in this room. Confirm reset to replace it.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (session == null) {
				session = getOrCreateSession(room);
			}
			if (hasActiveMatch && forceReset) {
				addActionLog(session, owner + " force-reset the room and started a new lobby.");
			}
			initializeLobby(session, owner, numPlayers, types);
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			resp.put("owner", session.ownerName);
			resp.put("sessionToken", issueSeatToken(session, session.ownerName));
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			if (name == null || name.trim().isEmpty()) {
				name = "Guest";
			}

			GameSession session = getOrCreateSession(room);
			name = canonicalizeLobbyName(session, name);
			if (!session.readyByPlayer.containsKey(name) && session.readyByPlayer.size() >= session.lobbyNumPlayers) {
				// Room-code join should not be blocked by the original selected count.
				// Expand capacity up to the game maximum (4 seats).
				if (session.lobbyNumPlayers < 4) {
					session.lobbyNumPlayers = 4;
				}
			}
			if (!session.readyByPlayer.containsKey(name) && session.readyByPlayer.size() >= session.lobbyNumPlayers) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Room is full.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			boolean conflictsWithAi = false;
			for (String aiName : session.aiByName.keySet()) {
				if (aiName != null && aiName.equalsIgnoreCase(name)) {
					conflictsWithAi = true;
					break;
				}
			}
			if (conflictsWithAi) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Name conflicts with AI bot.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			boolean exists = session.readyByPlayer.containsKey(name);
			String existingToken = session.seatTokenByPlayer.get(name);
			if (exists && existingToken != null && !existingToken.trim().isEmpty()
					&& !existingToken.equals(sessionToken == null ? "" : sessionToken.trim())) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "This seat is already in use on another browser.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			session.readyByPlayer.putIfAbsent(name, false);
			markPlayerSeen(session, name);
			ensureLobbyOwner(session, name);
			addActionLog(session, name + " joined lobby.");
			saveSnapshots();
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			resp.put("name", name);
			resp.put("owner", session.ownerName);
			resp.put("sessionToken", issueSeatToken(session, name));
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			boolean ready = extractBooleanField(body, "ready", false);
			GameSession session = getOrCreateSession(room);
			name = canonicalizeLobbyName(session, name);
			if (!hasValidSeatToken(session, name, sessionToken)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Seat locked by another browser session.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (!session.readyByPlayer.containsKey(name)) {
				session.readyByPlayer.put(name, false);
			}
			session.readyByPlayer.put(name, ready);
			markPlayerSeen(session, name);
			addActionLog(session, name + (ready ? " is ready." : " is not ready."));
			saveSnapshots();
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			GameSession session = getOrCreateSession(room);
			owner = canonicalizeLobbyName(session, owner);
			if (!hasValidSeatToken(session, owner, sessionToken)) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Owner seat locked by another browser session.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			ensureLobbyOwner(session, owner);
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
			List<String> lobbyOrder = new ArrayList<>(session.readyByPlayer.keySet());
			if (lobbyOrder.size() > session.lobbyNumPlayers) {
				lobbyOrder = new ArrayList<>(lobbyOrder.subList(0, session.lobbyNumPlayers));
			}
			if (lobbyOrder.size() < 2) {
				Map<String, Object> resp = new HashMap<>();
				resp.put("success", false);
				resp.put("message", "Need at least two players to start.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}

			int actualPlayers = Math.min(session.lobbyNumPlayers, lobbyOrder.size());
			String[] types = new String[] { "human", "human", "human", "human" };
			for (int i = 0; i < actualPlayers; i++) {
				String name = lobbyOrder.get(i);
				String aiDifficulty = session.aiByName.get(name);
				types[i] = (aiDifficulty == null || aiDifficulty.trim().isEmpty()) ? "human" : aiDifficulty.trim().toLowerCase();
			}

			startNewGame(session, actualPlayers, types, lobbyOrder, false);
			session.lobbyNumPlayers = actualPlayers;
			session.gameStarted = true;
			addActionLog(session, "Match started by " + session.ownerName + ".");
			saveSnapshots();
			Map<String, Object> resp = new HashMap<>();
			resp.put("success", true);
			resp.put("room", room);
			sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
		}
	}

	private static class RoomAfkAiHandler implements HttpHandler {
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			String target = extractStringField(body, "targetName", "");
			boolean enable = extractBooleanField(body, "enable", true);
			String difficulty = extractStringField(body, "difficulty", "medium").toLowerCase();
			if (!difficulty.equals("easy") && !difficulty.equals("medium") && !difficulty.equals("hard")) {
				difficulty = "medium";
			}
			GameSession session = getOrCreateSession(room);
			owner = canonicalizeLobbyName(session, owner);
			ensureLobbyOwner(session, owner);
			Map<String, Object> resp = new HashMap<>();
			if (!hasValidSeatToken(session, owner, sessionToken)) {
				resp.put("success", false);
				resp.put("message", "Owner seat locked by another browser session.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (!session.ownerName.equals(owner)) {
				resp.put("success", false);
				resp.put("message", "Only owner can manage AFK AI takeover.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (target == null || target.trim().isEmpty()) {
				resp.put("success", false);
				resp.put("message", "Missing target player.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			target = target.trim();
			if (!session.readyByPlayer.containsKey(target)) {
				resp.put("success", false);
				resp.put("message", "Target player not found.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
			if (enable) {
				session.forcedAiByName.add(target);
				session.forcedAiDifficultyByName.put(target, difficulty);
				addActionLog(session, target + " switched to AI takeover (" + difficulty + ") due to AFK.");
			} else {
				session.forcedAiByName.remove(target);
				session.forcedAiDifficultyByName.remove(target);
				addActionLog(session, target + " AI takeover disabled.");
			}
			saveSnapshots();
			resp.put("success", true);
			resp.put("target", target);
			resp.put("enabled", enable);
			resp.put("difficulty", difficulty);
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			String difficulty = extractStringField(body, "difficulty", "easy").toLowerCase();
			if (!difficulty.equals("easy") && !difficulty.equals("medium") && !difficulty.equals("hard")) {
				difficulty = "easy";
			}
			GameSession session = getOrCreateSession(room);
			owner = canonicalizeLobbyName(session, owner);
			Map<String, Object> resp = new HashMap<>();
			if (!hasValidSeatToken(session, owner, sessionToken)) {
				resp.put("success", false);
				resp.put("message", "Owner seat locked by another browser session.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
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
			saveSnapshots();
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
			String sessionToken = extractStringField(body, "sessionToken", "");
			String target = extractStringField(body, "targetName", "");
			GameSession session = getOrCreateSession(room);
			owner = canonicalizeLobbyName(session, owner);
			Map<String, Object> resp = new HashMap<>();
			if (!hasValidSeatToken(session, owner, sessionToken)) {
				resp.put("success", false);
				resp.put("message", "Owner seat locked by another browser session.");
				sendResponse(exchange, 200, toJson(resp), "application/json; charset=utf-8");
				return;
			}
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
			session.seatTokenByPlayer.remove(target);
			addActionLog(session, target + " was removed from lobby.");
			saveSnapshots();
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
			String seatToken = session.seatTokenByPlayer.remove(oldName);
			if (seatToken != null && !seatToken.trim().isEmpty()) {
				session.seatTokenByPlayer.put(newName, seatToken);
			}
			if (session.ownerName.equals(oldName)) {
				session.ownerName = newName;
			}
			addActionLog(session, oldName + " is now " + newName + ".");
			saveSnapshots();
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
		String actionType = extractStringField(body, "type", "").toLowerCase();
		boolean success = false;
		String message = "Unknown action";
		Player currentPlayer = session.controller.getCurrentPlayer();
		String actor = currentPlayer.getName();
		String requesterName = extractStringField(body, "name", "");
		String sessionToken = extractStringField(body, "sessionToken", "");
		if (requesterName == null) {
			requesterName = "";
		}
		requesterName = canonicalizeLobbyName(session, requesterName);
		if (requesterName.isEmpty()) {
			response.put("success", false);
			response.put("message", "Missing player identity.");
			response.put("gameOver", session.controller.isGameOver());
			return response;
		}
		if (!hasValidSeatToken(session, requesterName, sessionToken)) {
			response.put("success", false);
			response.put("message", "Seat locked by another browser session.");
			response.put("gameOver", session.controller.isGameOver());
			return response;
		}
		markPlayerSeen(session, requesterName);
		if (!currentPlayer.isHuman()) {
			response.put("success", false);
			response.put("message", "Please wait for the AI turn to finish.");
			response.put("gameOver", session.controller.isGameOver());
			return response;
		}
		if (session.forcedAiByName.contains(requesterName)) {
			response.put("success", false);
			response.put("message", "You are currently under AI takeover. Refresh to resume control.");
			response.put("gameOver", session.controller.isGameOver());
			return response;
		}
		boolean genericSeatFallback = !actor.equals(requesterName)
			&& isGenericSeatName(actor)
			&& session.readyByPlayer.containsKey(requesterName);
		if (!actor.equals(requesterName) && !genericSeatFallback) {
			response.put("success", false);
			response.put("message", "Not your turn.");
			response.put("gameOver", session.controller.isGameOver());
			return response;
		}
		String actionSummary = null;
		Noble nobleBeforeAction = currentPlayer.getVisitedNoble();

		if ("takegems".equals(actionType)) {
			// Extract gems array e.g. "gems":["R","E","S"]
			Map<GemType, Integer> gemsToTake = new HashMap<>();
			Map<GemType, Integer> gemsToDiscard = new HashMap<>();
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
			int discardIdx = lower.indexOf("\"discard\"");
			if (discardIdx >= 0) {
				int dStart = body.indexOf("[", discardIdx);
				int dEnd = body.indexOf("]", dStart);
				if (dStart >= 0 && dEnd > dStart) {
					String inner = body.substring(dStart + 1, dEnd);
					String[] parts = inner.split(",");
					for (String part : parts) {
						String trimmed = part.replace("\"", "").trim().toUpperCase();
						if (!trimmed.isEmpty()) {
							GemType type = GemType.fromAbbreviation(trimmed);
							if (type != null) {
								gemsToDiscard.put(type, gemsToDiscard.getOrDefault(type, 0) + 1);
							}
						}
					}
				}
			}
			if (!gemsToTake.isEmpty()) {
				Map<GemType, Integer> validationPlayerGems = session.controller.getCurrentPlayer().getGems();
				int declaredDiscard = gemsToDiscard.values().stream().mapToInt(Integer::intValue).sum();
				if (declaredDiscard > 0) {
					int target = Math.max(0,
						session.controller.getCurrentPlayer().getTotalGemCount() - declaredDiscard);
					int running = validationPlayerGems.values().stream().mapToInt(Integer::intValue).sum();
					if (running > target) {
						for (GemType t : GemType.values()) {
							if (running <= target) {
								break;
							}
							int have = validationPlayerGems.getOrDefault(t, 0);
							if (have <= 0) {
								continue;
							}
							int cut = Math.min(have, running - target);
							validationPlayerGems.put(t, have - cut);
							running -= cut;
						}
					}
				}

				GameRules.ValidationResult vr = session.controller.getRules().validateTakeGems(
					gemsToTake,
					session.controller.getBoard().getAvailableGems(),
					validationPlayerGems
				);
				if (!vr.isValid()) {
					success = false;
					message = vr.getMessage();
				} else {
					success = session.controller.takeGems(gemsToTake);
					message = success ? "Gems taken." : "Could not take gems.";
					if (success) {
						Player p = session.controller.getCurrentPlayer();
						int maxGems = session.controller.getConfig().getMaxGemsPerPlayer();
						int totalAfterTake = p.getTotalGemCount();
						if (totalAfterTake > maxGems) {
							int needDiscard = totalAfterTake - maxGems;
							int discardCount = gemsToDiscard.values().stream().mapToInt(Integer::intValue).sum();
							if (discardCount != needDiscard) {
								success = false;
								message = "Must discard exactly " + needDiscard + " gem(s).";
							} else {
								Map<GemType, Integer> currentGems = p.getGems();
								boolean canDiscard = true;
								for (Map.Entry<GemType, Integer> e : gemsToDiscard.entrySet()) {
									if (e.getValue() <= 0) {
										continue;
									}
									if (currentGems.getOrDefault(e.getKey(), 0) < e.getValue()) {
										canDiscard = false;
										break;
									}
								}
								if (!canDiscard) {
									success = false;
									message = "Invalid discard selection.";
								} else {
									p.removeGems(gemsToDiscard);
									session.controller.getBoard().addGems(gemsToDiscard);
								}
							}
						}
					}
					if (success) {
						StringBuilder taken = new StringBuilder();
						for (Map.Entry<GemType, Integer> e : gemsToTake.entrySet()) {
							if (taken.length() > 0) {
								taken.append(", ");
							}
							taken.append(e.getKey().getAbbreviation()).append("x").append(e.getValue());
						}
						StringBuilder discarded = new StringBuilder();
						for (Map.Entry<GemType, Integer> e : gemsToDiscard.entrySet()) {
							if (e.getValue() <= 0) {
								continue;
							}
							if (discarded.length() > 0) {
								discarded.append(", ");
							}
							discarded.append(e.getKey().getAbbreviation()).append("x").append(e.getValue());
						}
						actionSummary = actor + " took gems: " + taken
							+ (discarded.length() > 0 ? " (discarded " + discarded + ")." : ".");
					}
				}
			} else {
				message = "No valid gems specified";
			}
		} else if ("reservetop".equals(actionType)) {
			int level = extractIntField(body, "level", 1);
			Card topCard = session.controller.getBoard().peekTopCard(level);
			GameRules.ValidationResult vr = session.controller.getRules().validateReserveCard(session.controller.getCurrentPlayer());
			if (!vr.isValid()) {
				success = false;
				message = vr.getMessage();
			} else if (topCard == null) {
				success = false;
				message = "No cards left in this deck.";
			} else {
				success = session.controller.reserveTopCard(level);
				message = success ? "Top card reserved." : "Cannot reserve from this deck.";
				if (success) {
					actionSummary = actor + " reserved the top card from Level " + topCard.getLevel()
						+ " (+" + topCard.getPrestigePoints() + " prestige, +" + topCard.getBonusGem().getAbbreviation() + " bonus).";
				}
			}
		} else if ("reserve".equals(actionType)) {
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
				Card card = visible.get(index);
				success = session.controller.reserveCard(level, index);
				message = success ? "Card reserved." : "Cannot reserve this card.";
				if (success) {
					actionSummary = actor + " reserved a Level " + card.getLevel()
						+ " card (+" + card.getPrestigePoints() + " prestige, +" + card.getBonusGem().getAbbreviation() + " bonus).";
				}
			}
		} else if ("purchasevisible".equals(actionType)) {
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
						actionSummary = actor + " bought a Level " + card.getLevel()
							+ " card (+" + card.getPrestigePoints() + " prestige, +" + card.getBonusGem().getAbbreviation() + " bonus).";
					}
				}
			}
		} else if ("purchasereserved".equals(actionType)) {
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
						actionSummary = actor + " bought a reserved Level " + card.getLevel()
							+ " card (+" + card.getPrestigePoints() + " prestige, +" + card.getBonusGem().getAbbreviation() + " bonus).";
					}
				}
			}
		}

		// Advance turns (including AI turns) if action succeeded
		if (success && !session.controller.isGameOver()) {
			if (actionSummary != null) {
				addActionLog(session, actionSummary);
			}
			Noble nobleAfterAction = currentPlayer.getVisitedNoble();
			if (nobleAfterAction != null && nobleAfterAction != nobleBeforeAction) {
				addActionLog(
					session,
					actor + " claimed Noble " + nobleAfterAction.getNobleId() + " ("
						+ nobleAfterAction.getName() + ") for +" + nobleAfterAction.getPrestigePoints() + " prestige."
				);
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
			if (ai == null && session.forcedAiByName.contains(current.getName())) {
				String diff = session.forcedAiDifficultyByName.getOrDefault(current.getName(), "medium");
				if ("easy".equals(diff)) {
					ai = new AIPlayer(new EasyAIStrategy());
				} else if ("hard".equals(diff)) {
					ai = new AIPlayer(new HardAIStrategy());
				} else {
					ai = new AIPlayer(new MediumAIStrategy());
				}
			}
			if (ai == null) {
				// next player is human, stop here
				break;
			}
			// Let AI take its move. AI strategies always choose legal moves.
			Noble nobleBeforeAi = current.getVisitedNoble();
			String aiAction = ai.makeMove(session.controller);
			addActionLog(session, current.getName() + ": " + (aiAction == null ? "took a turn." : aiAction));
			Noble nobleAfterAi = current.getVisitedNoble();
			if (nobleAfterAi != null && nobleAfterAi != nobleBeforeAi) {
				addActionLog(
					session,
					current.getName() + " claimed Noble " + nobleAfterAi.getNobleId() + " ("
						+ nobleAfterAi.getName() + ") for +" + nobleAfterAi.getPrestigePoints() + " prestige."
				);
			}
			if (session.controller.isGameOver()) {
				break;
			}
		}
	}

	private static String buildGameStateJson(GameSession session, String roomKey, String viewerName) {
		StringBuilder sb = new StringBuilder();
		GameBoard board = session.controller.getBoard();
		Player current = session.controller.getCurrentPlayer();
		ensureLobbyOwner(session, null);
		List<Player> players = session.controller.getPlayers();
		List<String> lobbyNames = lobbySeatNames(session, players.size());
		int currentIdx = players.indexOf(current);
		String currentDisplayName = displayNameForSeat(session, currentIdx, current, lobbyNames);
		String viewer = viewerName == null ? "" : viewerName.trim();
		boolean isMyTurn = current.isHuman()
			&& !viewer.isEmpty()
			&& (current.getName().equals(viewer)
				|| currentDisplayName.equals(viewer)
				|| (isGenericSeatName(current.getName()) && session.readyByPlayer.containsKey(viewer)));
		if (session.forcedAiByName.contains(current.getName()) && current.getName().equals(viewer)) {
			isMyTurn = false;
		}

		sb.append("{");
		sb.append("\"currentPlayer\":\"").append(escape(currentDisplayName)).append("\",");
		sb.append("\"turnNumber\":").append(session.turnNumber).append(",");
		int totalPlayers = session.controller.getPlayers().size();
		int roundNumber = Math.max(1, ((session.turnNumber - 1) / Math.max(1, totalPlayers)) + 1);
		sb.append("\"roundNumber\":").append(roundNumber).append(",");
		sb.append("\"isHumanTurn\":").append(current.isHuman()).append(",");
		sb.append("\"isMyTurn\":").append(isMyTurn).append(",");
		sb.append("\"afkAiThresholdSeconds\":").append(AFK_AI_THRESHOLD_SECONDS).append(",");
		sb.append("\"endgameFinalRound\":").append(session.controller.isEndgamePending()).append(",");
		sb.append("\"gameOver\":").append(session.controller.isGameOver()).append(",");
		if (session.controller.isGameOver() && session.controller.getWinner() != null) {
			sb.append("\"winner\":\"").append(escape(session.controller.getWinner().getName())).append("\",");
		}

		// Players
		sb.append("\"players\":[");
		for (int i = 0; i < players.size(); i++) {
			Player p = players.get(i);
			String displayName = displayNameForSeat(session, i, p, lobbyNames);
			if (i > 0) {
				sb.append(",");
			}
			sb.append("{");
			sb.append("\"name\":\"").append(escape(displayName)).append("\",");
			sb.append("\"human\":").append(p.isHuman()).append(",");
			long seenMs = session.lastSeenByPlayer.getOrDefault(displayName, System.currentTimeMillis());
			long afkSeconds = Math.max(0L, (System.currentTimeMillis() - seenMs) / 1000L);
			sb.append("\"afkSeconds\":").append(afkSeconds).append(",");
			sb.append("\"forcedAi\":").append(session.forcedAiByName.contains(displayName)).append(",");
			sb.append("\"prestige\":").append(p.getPrestigePoints()).append(",");
			sb.append("\"purchasedCards\":").append(p.getPurchasedCards().size()).append(",");
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
			sb.append("\"boughtCards\":[");
			List<Card> bought = p.getPurchasedCards();
			for (int bi = 0; bi < bought.size(); bi++) {
				if (bi > 0) {
					sb.append(",");
				}
				Card bc = bought.get(bi);
				sb.append("{");
				sb.append("\"id\":").append(bc.getCardId()).append(",");
				sb.append("\"level\":").append(bc.getLevel()).append(",");
				sb.append("\"points\":").append(bc.getPrestigePoints()).append(",");
				sb.append("\"bonusGem\":\"").append(bc.getBonusGem().name()).append("\",");
				sb.append("\"bonusAbbr\":\"").append(bc.getBonusGem().getAbbreviation()).append("\",");
				sb.append("\"cost\":{");
				boolean firstBc = true;
				for (Map.Entry<GemType, Integer> entry : bc.getCost().entrySet()) {
					int qty = entry.getValue() == null ? 0 : entry.getValue().intValue();
					if (qty <= 0) {
						continue;
					}
					if (!firstBc) {
						sb.append(",");
					}
					firstBc = false;
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
		sb.append("\"deckRemaining\":{");
		for (int level = 1; level <= 3; level++) {
			if (level > 1) {
				sb.append(",");
			}
			sb.append("\"").append(level).append("\":").append(board.getDeckSize(level));
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
		List<Noble> claimable = session.controller.getRules().getVisitableNobles(board.getAvailableNobles(), current);
		sb.append(",");
		sb.append("\"claimableNobles\":[");
		for (int i = 0; i < claimable.size(); i++) {
			if (i > 0) {
				sb.append(",");
			}
			sb.append("\"").append(escape(claimable.get(i).getName())).append("\"");
		}
		sb.append("]");
		sb.append(",");
		sb.append("\"room\":\"").append(escape(cleanRoomName(roomKey))).append("\",");
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

