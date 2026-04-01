function resolveApiBase() {
  const fromGlobal =
    typeof window !== "undefined" && typeof window.__SPLENDOR_API_BASE__ === "string"
      ? window.__SPLENDOR_API_BASE__
      : "";
  const raw = String(fromGlobal || "").trim();
  if (!raw) {
    // Empty base keeps local dev behavior (same-origin /api/*)
    return "";
  }
  return raw.replace(/\/+$/, "");
}

const API_BASE = resolveApiBase();

(function splendorApplyMediaSpriteCss() {
  if (typeof document === "undefined" || typeof window === "undefined") {
    return;
  }
  /* Default: point sprite vars at web/media. Set __SPLENDOR_USE_CSS_GEMS__ = true to skip (stylesheet defaults only). */
  if (window.__SPLENDOR_USE_CSS_GEMS__ === true) {
    return;
  }
  try {
    const base =
      window.__SPLENDOR_MEDIA_BASE__ != null ? String(window.__SPLENDOR_MEDIA_BASE__).trim().replace(/\/+$/, "") : "";
    const p = base ? `${base}/` : "";
    document.documentElement.style.setProperty("--splendor-chip-sprite", `url("${p}media/chips.jpg")`);
    document.documentElement.style.setProperty("--splendor-gem-sprite", `url("${p}media/gems.png")`);
  } catch (_) {
    /* ignore */
  }
})();

const API_STATE = `${API_BASE}/api/state`;
const API_ACTION = `${API_BASE}/api/action`;
const API_NEW_GAME = `${API_BASE}/api/newgame`;
const API_QUIT = `${API_BASE}/api/quit`;
const API_ROOM_CREATE = `${API_BASE}/api/room/create`;
const API_ROOM_JOIN = `${API_BASE}/api/room/join`;
const API_ROOM_READY = `${API_BASE}/api/room/ready`;
const API_ROOM_START = `${API_BASE}/api/room/start`;
const API_ROOM_ADDAI = `${API_BASE}/api/room/addai`;
const API_ROOM_KICK = `${API_BASE}/api/room/kick`;
const API_ROOM_AFK_AI = `${API_BASE}/api/room/afkai`;
let currentRoom = "Room A";
const LAST_ROOM_KEY = "splendor.lastRoom";
const LAST_NAME_KEY = "splendor.playerName";
let playerViewOffset = 0;
let currentPlayerName = "";
let currentReady = false;
let currentOwner = "";

async function readErrorMessage(res, fallback) {
  let text = "";
  try {
    text = (await res.text()) || "";
  } catch (_) {
    return fallback;
  }
  if (!text.trim()) {
    return fallback;
  }
  try {
    const parsed = JSON.parse(text);
    if (parsed && parsed.message) {
      return String(parsed.message);
    }
  } catch (_) {
    // Non-JSON response; return raw text below.
  }
  return text.trim();
}

async function fetchState() {
  const roomParam = encodeURIComponent(currentRoom || "Room A");
  const nameParam = encodeURIComponent(currentPlayerName || "");
  const res = await fetch(`${API_STATE}?room=${roomParam}&name=${nameParam}`);
  if (!res.ok) {
    throw new Error("Failed to load state");
  }
  return res.json();
}

let tutorialFlags = {
  active: false,
  tookGemsOnce: false,
  boughtLevel1Once: false,
};

const uiState = {
  gemUiInitialized: false,
  actionUiBound: false,
};

let toastTimer = null;
let latestState = null;
let lastSeenTurnNumber = null;
let lastSeenActionCount = 0;
let suppressRealtimeToasts = true;
let lastAutoRejoinAt = 0;
let lastRecoveryToastAt = 0;

const KNOWN_GEMS = new Set(["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX", "GOLD"]);
const GEM_ABBR_TO_NAME = {
  R: "RUBY",
  E: "EMERALD",
  S: "SAPPHIRE",
  D: "DIAMOND",
  O: "ONYX",
  G: "GOLD",
};

/**
 * CSS gem tokens (flat shapes; not spheres). If __SPLENDOR_USE_IMAGE_SPRITES__ is true, use bitmap sprites via gemSpriteLegacyMarkup.
 */
function gemDotMarkup(gem) {
  const raw = gem && String(gem);
  const g = KNOWN_GEMS.has(raw) ? raw : "DIAMOND";
  return `<span class="gem-dot gem-dot--${g}" role="img" aria-hidden="true"></span>`;
}

function gemSpriteLegacyMarkup(gem, kind) {
  const raw = gem && String(gem);
  const g = KNOWN_GEMS.has(raw) ? raw : "DIAMOND";
  const useChip = kind !== "stone" || g === "GOLD";
  const type = useChip ? "chip" : "stone";
  return `<span class="gem-sprite gem-sprite--${type} gem-sprite--${g}" role="img" aria-hidden="true"></span>`;
}

function gemSpriteMarkup(gem, kind) {
  if (typeof window !== "undefined" && window.__SPLENDOR_USE_IMAGE_SPRITES__ === true) {
    return gemSpriteLegacyMarkup(gem, kind);
  }
  return gemDotMarkup(gem);
}

function gemPillMarkup(gem, text, kind = "chip") {
  return `<span class="gem-pill-content">${gemSpriteMarkup(gem, kind)}<span class="gem-pill-text">${text}</span></span>`;
}

/** Splendor-style cost column order (white → black). */
const DEV_CARD_COST_ORDER = ["DIAMOND", "SAPPHIRE", "EMERALD", "RUBY", "ONYX"];

function gemDisplayName(gemEnum) {
  switch (gemEnum) {
    case "RUBY":
      return "Ruby";
    case "EMERALD":
      return "Emerald";
    case "SAPPHIRE":
      return "Sapphire";
    case "DIAMOND":
      return "Diamond";
    case "ONYX":
      return "Onyx";
    case "GOLD":
      return "Gold";
    default:
      return gemEnum || "";
  }
}

function isGenericSeatName(name) {
  return typeof name === "string" && /^Player\s+\d+$/i.test(name.trim());
}

function samePlayerName(a, b) {
  const x = String(a || "").trim().toLowerCase();
  const y = String(b || "").trim().toLowerCase();
  return x !== "" && y !== "" && x === y;
}

function resolveCanonicalLobbyName(state, rawName) {
  const target = String(rawName || "").trim();
  if (!target || !state || !state.lobby || !Array.isArray(state.lobby.players)) {
    return target;
  }
  const match = state.lobby.players.find((p) => samePlayerName(p && p.name, target));
  return match && match.name ? String(match.name) : target;
}

function devCardCostEntries(cost) {
  const c = cost || {};
  const ordered = DEV_CARD_COST_ORDER.filter((g) => Number(c[g]) > 0).map((g) => [g, Number(c[g])]);
  const extras = Object.entries(c)
    .filter(([g, n]) => Number(n) > 0 && !DEV_CARD_COST_ORDER.includes(g))
    .map(([g, n]) => [g, Number(n)]);
  return ordered.concat(extras);
}

/**
 * Filename pattern used in hexanome-04/splendor development-cards (01.jpg … 09.jpg, 010.jpg … 099.jpg, 100.jpg).
 * See https://github.com/hexanome-04/splendor/tree/d1797acf5d43c6bc512b57ef3c1d990006a49a5c/client/public/images/development-cards
 */
function splendorHexanomeDevCardFilename(globalIndex) {
  const x = Math.max(1, Math.min(999, Math.floor(Number(globalIndex)) || 1));
  if (x < 10) {
    return `0${x}.jpg`;
  }
  if (x < 100) {
    return `0${String(x).padStart(2, "0")}.jpg`;
  }
  return `${x}.jpg`;
}

/**
 * When set, maps a global art index to a filename under {@link window.__SPLENDOR_DEV_CARD_ART_BASE__}.
 * Example: cycle 12 custom faces — {@code (i) => `splendor-${((i - 1) % 12) + 1}.jpg`}.
 */
function resolveDevCardArtFilename(globalIdx) {
  if (typeof window !== "undefined" && typeof window.__SPLENDOR_DEV_CARD_ART_FILENAME__ === "function") {
    try {
      const name = window.__SPLENDOR_DEV_CARD_ART_FILENAME__(globalIdx);
      if (name != null && String(name).trim() !== "") {
        return String(name).trim().replace(/^\/+/, "");
      }
    } catch (_) {
      /* use default */
    }
  }
  return splendorHexanomeDevCardFilename(globalIdx);
}

/**
 * Maps our loader ids (level*1000 + rowInLevel) to a global art index 1–90 like standard Splendor (40 + 30 + 20).
 * Override with window.__SPLENDOR_DEV_CARD_ART_INDEX__(level, cardId, seqInLevel) if your CSV order differs.
 */
function splendorDefaultDevCardGlobalIndex(level, cardId) {
  const cid = Number(cardId);
  const seq = Number.isFinite(cid) && cid > 0 ? cid % 1000 : 1;
  if (level === 1) {
    return seq;
  }
  if (level === 2) {
    return 40 + seq;
  }
  return 70 + seq;
}

function resolveDevCardArtImageUrl(level, card) {
  if (typeof window === "undefined") {
    return null;
  }
  const rawBase = window.__SPLENDOR_DEV_CARD_ART_BASE__;
  const base = typeof rawBase === "string" ? rawBase.trim().replace(/\/+$/, "") : "";
  if (!base) {
    return null;
  }
  const byBonusRaw = window.__SPLENDOR_DEV_CARD_ART_BY_BONUS__;
  if (byBonusRaw && typeof byBonusRaw === "object" && card && card.bonusGem) {
    const bonus = String(card.bonusGem).toUpperCase();
    const candidatesRaw = byBonusRaw[bonus];
    const candidates = Array.isArray(candidatesRaw)
      ? candidatesRaw
          .map((x) => (x == null ? "" : String(x).trim().replace(/^\/+/, "")))
          .filter((x) => x.length > 0)
      : [];
    if (candidates.length > 0) {
      const cardId = card && card.id != null ? Number(card.id) : NaN;
      const seed = Number.isFinite(cardId) && cardId > 0 ? cardId : 1;
      const idx = Math.abs(seed) % candidates.length;
      return `${base}/${candidates[idx]}`;
    }
  }
  const cardId = card && card.id != null ? Number(card.id) : NaN;
  const seqInLevel = Number.isFinite(cardId) && cardId > 0 ? cardId % 1000 : 1;
  let globalIdx;
  if (typeof window.__SPLENDOR_DEV_CARD_ART_INDEX__ === "function") {
    globalIdx = window.__SPLENDOR_DEV_CARD_ART_INDEX__(level, cardId, seqInLevel);
  } else {
    globalIdx = splendorDefaultDevCardGlobalIndex(level, cardId);
  }
  if (!Number.isFinite(globalIdx) || globalIdx < 1) {
    globalIdx = 1;
  }
  const file = resolveDevCardArtFilename(globalIdx);
  return `${base}/${file}`;
}

function showToast(message, type = "ok") {
  const toast = document.getElementById("action-toast");
  if (!toast) {
    return;
  }
  toast.textContent = message;
  toast.className = `toast ${type} show`;
  if (toastTimer) {
    clearTimeout(toastTimer);
  }
  toastTimer = setTimeout(() => {
    toast.classList.remove("show");
  }, 3200);
}

function clearTransientUi() {
  const msg = document.getElementById("action-message");
  if (msg) {
    msg.textContent = "";
    msg.className = "message";
  }
  const toast = document.getElementById("action-toast");
  if (toast) {
    toast.classList.remove("show");
    toast.textContent = "";
  }
  if (toastTimer) {
    clearTimeout(toastTimer);
    toastTimer = null;
  }
}

function rememberRoom(room) {
  const safeRoom = (room || "Room A").trim() || "Room A";
  currentRoom = safeRoom;
  try {
    window.localStorage.setItem(LAST_ROOM_KEY, safeRoom);
  } catch (_) {
    // ignore storage errors
  }
}

function getRememberedRoom() {
  try {
    const saved = window.localStorage.getItem(LAST_ROOM_KEY);
    return saved && saved.trim() ? saved.trim() : null;
  } catch (_) {
    return null;
  }
}

function rememberPlayerName(name) {
  const safeName = (name || "").trim();
  if (!safeName) {
    return;
  }
  try {
    window.localStorage.setItem(LAST_NAME_KEY, safeName);
  } catch (_) {
    // ignore storage errors
  }
}

function getRememberedPlayerName() {
  try {
    const saved = window.localStorage.getItem(LAST_NAME_KEY);
    return saved && saved.trim() ? saved.trim() : "";
  } catch (_) {
    return "";
  }
}

function generateFallbackName(prefix = "Guest") {
  const n = Math.floor(1000 + Math.random() * 9000);
  return `${prefix}-${n}`;
}

/** Pathnames that must not be treated as a room code (static assets, etc.). */
const URL_PATH_RESERVED = new Set(
  ["app.js", "styles.css", "config.js", "index.html", "favicon.ico", "robots.txt", "sitemap.xml", "media"].map(
    (s) => s.toLowerCase()
  )
);

/**
 * Single-path segment after the domain, e.g. https://splendor-coral.vercel.app/SP-A28047
 */
function roomFromUrlPath() {
  if (typeof window === "undefined") {
    return null;
  }
  let path = window.location.pathname || "";
  path = path.replace(/^\/+|\/+$/g, "");
  if (!path || path.includes("/")) {
    return null;
  }
  let seg;
  try {
    seg = decodeURIComponent(path);
  } catch (_) {
    return null;
  }
  const lower = seg.toLowerCase();
  if (URL_PATH_RESERVED.has(lower)) {
    return null;
  }
  const t = seg.trim();
  return t || null;
}

function syncRoomToBrowserUrl(room) {
  if (typeof window === "undefined" || !window.history || !window.history.replaceState) {
    return;
  }
  const r = String(room || "").trim();
  if (!r) {
    return;
  }
  const enc = encodeURIComponent(r);
  const next = `/${enc}`;
  if (window.location.pathname === next) {
    return;
  }
  window.history.replaceState({ room: r }, "", next);
}

function clearRoomFromBrowserUrl() {
  if (typeof window === "undefined" || !window.history || !window.history.replaceState) {
    return;
  }
  const p = window.location.pathname || "/";
  if (p === "/" || p === "") {
    return;
  }
  window.history.replaceState({}, "", "/");
}

function randomizePlayerView(state) {
  const n = Math.max(1, (state.players || []).length);
  playerViewOffset = Math.floor(Math.random() * n);
}

async function copyRoomCodeToClipboard() {
  const text = String(currentRoom || "").trim();
  if (!text) {
    showToast("No game code to copy.", "error");
    return false;
  }
  try {
    if (navigator.clipboard && typeof navigator.clipboard.writeText === "function") {
      await navigator.clipboard.writeText(text);
      showToast(`Copied game code: ${text}`);
      return true;
    }
  } catch (_) {
    // Fall through to legacy copy method.
  }

  try {
    const ta = document.createElement("textarea");
    ta.value = text;
    ta.setAttribute("readonly", "");
    ta.style.position = "fixed";
    ta.style.opacity = "0";
    ta.style.pointerEvents = "none";
    document.body.appendChild(ta);
    ta.focus();
    ta.select();
    const ok = document.execCommand("copy");
    document.body.removeChild(ta);
    if (ok) {
      showToast(`Copied game code: ${text}`);
      return true;
    }
  } catch (_) {
    // ignore and show failure toast below
  }
  showToast("Copy failed. Please copy the code manually.", "error");
  return false;
}

async function postAction(payload) {
  const withRoom = { ...payload, room: currentRoom, name: currentPlayerName };
  const res = await fetch(API_ACTION, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(withRoom),
  });
  if (!res.ok) {
    throw new Error("Action failed");
  }
  const result = await res.json();

  // Very simple tutorial tracking: record when the player has
  // successfully taken gems or bought any card.
  if (tutorialFlags.active && result && result.success) {
    if (payload.type === "takeGems") {
      tutorialFlags.tookGemsOnce = true;
    } else if (
      payload.type === "purchaseVisible" ||
      payload.type === "purchaseReserved"
    ) {
      tutorialFlags.boughtLevel1Once = true;
    }
  }

  return result;
}

async function postNewGame(payload) {
  const withRoom = { ...payload, room: currentRoom };
  const res = await fetch(API_NEW_GAME, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(withRoom),
  });
  if (!res.ok) {
    throw new Error("New game failed");
  }
  return res.json();
}

async function postQuit() {
  const res = await fetch(API_QUIT, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ room: currentRoom }),
  });
  if (!res.ok) {
    throw new Error("Quit failed");
  }
  return res.json();
}

async function postCreateRoom(payload) {
  const res = await fetch(API_ROOM_CREATE, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Create room failed"));
  }
  return res.json();
}

async function postJoinRoom(payload) {
  const res = await fetch(API_ROOM_JOIN, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Join room failed"));
  }
  return res.json();
}

async function postReady(payload) {
  const res = await fetch(API_ROOM_READY, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Ready failed"));
  }
  return res.json();
}

async function postStartRoom(payload) {
  const res = await fetch(API_ROOM_START, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Start room failed"));
  }
  return res.json();
}

async function postAddAi(payload) {
  const res = await fetch(API_ROOM_ADDAI, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Add AI failed"));
  }
  return res.json();
}

async function postKick(payload) {
  const res = await fetch(API_ROOM_KICK, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "Kick failed"));
  }
  return res.json();
}

async function postAfkAi(payload) {
  const res = await fetch(API_ROOM_AFK_AI, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error(await readErrorMessage(res, "AFK AI update failed"));
  }
  return res.json();
}

const guidedSteps = [
  "Welcome!\n\nYou play as Player 1 against an AI. This walkthrough explains the main screen and the three core actions: take gems, reserve a card, and buy a card.",

  "Status bar (top)\n\nHere you see whose turn it is and whether the game has ended. When it is your turn, the Actions section below is yours to use.",

  "Board Gems\n\nThese chips are the supply. On your turn you can take gems from here (rules: 3 different colors, or 2 of the same if enough remain). You cannot take gold directly by \"taking gems\"—gold usually comes from reserving.",

  "Take Gems\n\nOpen the Take Gems row, pick a legal combination, then press \"Confirm Take Gems\". Try it once so you have tokens to spend later.\n\nTip: you cannot hold more than 10 gems total.",

  "Reserve a card\n\nUse a card's \"Reserve\" button (under each visible card).\n\n• Max 3 reserved cards.\n• Gain 1 gold if available.\n• Buy it later with \"Buy reserved\" on your player panel.\n\nReserve helps you lock a card before others take it.",

  "Development cards — Level 1\n\nEach card shows its cost and a bonus gem color. After you buy a card, that color becomes a permanent discount on future purchases.",

  "Purchase a visible card\n\nUse a card's \"Buy\" button (or click the card) when it is affordable. You pay with gems in hand plus permanent discounts from your bonuses. Try to buy one Level 1 card early.",

  "Players panel\n\nWatch bonuses (permanent discounts), gems in hand, reserved cards, and prestige. Bonuses stack and make expensive cards easier over time.",

  "Nobles\n\nNobles award free prestige if your bonuses match their requirements—they count bonuses, not loose gems. Plan your buys towards a noble when it fits your strategy.",

  "Winning the game\n\nWhen someone reaches 15 prestige (default), the round finishes so everyone gets the same number of turns, then the highest score wins. Ties go to the player who bought fewer development cards.",

  "You're set!\n\nEnd the tutorial anytime with \"End Tutorial\", or tap \"Finish\" to return to the start screen. Have fun!"
];

function renderState(state) {
  latestState = state;
  if (state && state.room != null && String(state.room).trim() !== "") {
    const fromServer = String(state.room).trim();
    if (fromServer !== currentRoom) {
      rememberRoom(fromServer);
    }
    syncRoomToBrowserUrl(currentRoom);
  }
  const turnInfo = document.getElementById("turn-info");
  const roomCodeDisplay = document.getElementById("room-code-display");
  const gameOverWrap = document.getElementById("game-over-wrap");
  const gameOverBanner = document.getElementById("game-over");
  const gameOverHint = document.getElementById("game-over-hint");
  const leaderboardEl = document.getElementById("leaderboard");
  const boardGems = document.getElementById("board-gems");
  const nobles = document.getElementById("nobles");
  const players = document.getElementById("players");
  const recommendedSteps = document.getElementById("recommended-steps");
  const activityLog = document.getElementById("activity-log");
  const lobbyNames = state && state.lobby && Array.isArray(state.lobby.players)
    ? state.lobby.players.map((p) => String(p.name || "").trim()).filter((n) => n.length > 0)
    : [];
  const displayPlayers = (state.players || []).map((p, idx) => {
    const raw = p && p.name != null ? String(p.name) : "";
    const mapped = isGenericSeatName(raw) && idx < lobbyNames.length ? lobbyNames[idx] : raw;
    return { ...p, name: mapped || raw };
  });
  const currentRawName = state && state.currentPlayer != null ? String(state.currentPlayer) : "";
  const currentIdx = (state.players || []).findIndex((p) => String((p && p.name) || "") === currentRawName);
  const currentDisplayName =
    currentIdx >= 0 && currentIdx < displayPlayers.length
      ? String(displayPlayers[currentIdx].name || currentRawName)
      : currentRawName;
  const currentPlayerData = displayPlayers.find((p) => p.name === currentDisplayName) || null;
  const isMyTurn = !!(state.isMyTurn != null ? state.isMyTurn : state.isHumanTurn);
  const isAiTurn = state.isHumanTurn === false;
  const currentReservedCount = currentPlayerData ? (currentPlayerData.reservedCount || 0) : 0;
  const turnNo = Number.isFinite(state.turnNumber) ? state.turnNumber : 1;
  const roundNo = Number.isFinite(state.roundNumber) ? state.roundNumber : 1;
  const actions = Array.isArray(state.recentActions) ? state.recentActions : [];

  if (state.gameOver && gameOverWrap && gameOverBanner && leaderboardEl) {
    gameOverWrap.classList.remove("hidden");
    const winnerName = state.winner || "Unknown";
    gameOverBanner.textContent = `Game over — ${winnerName} wins`;
    if (gameOverHint) {
      gameOverHint.textContent = "Ties: higher prestige wins; if tied, fewer purchased development cards wins.";
    }
    leaderboardEl.innerHTML = "";
    const playersRank = Array.isArray(state.players) ? [...state.players] : [];
    playersRank.sort((a, b) => {
      const pa = Number(a.prestige) || 0;
      const pb = Number(b.prestige) || 0;
      if (pb !== pa) {
        return pb - pa;
      }
      const ca = Number(a.purchasedCards);
      const cb = Number(b.purchasedCards);
      const ac = Number.isFinite(ca) ? ca : 999;
      const bc = Number.isFinite(cb) ? cb : 999;
      return ac - bc;
    });
    playersRank.forEach((p, idx) => {
      const li = document.createElement("li");
      li.className = "leaderboard__row";
      if (p.name === state.winner) {
        li.classList.add("leaderboard__row--winner");
      }
      const pts = Number(p.prestige) || 0;
      const cards = Number.isFinite(Number(p.purchasedCards)) ? Number(p.purchasedCards) : "—";
      li.textContent = `${idx + 1}. ${p.name} — ${pts} prestige · ${cards} cards`;
      leaderboardEl.appendChild(li);
    });
  } else if (gameOverWrap) {
    gameOverWrap.classList.add("hidden");
  }

  let turnLine = `Round ${roundNo} · Turn ${turnNo} · Current Player: ${currentDisplayName}`;
  if (isAiTurn) {
    turnLine += " (AI turn)";
  } else if (isMyTurn) {
    turnLine += " (Your turn)";
  } else {
    turnLine += " (Other player's turn)";
  }
  if (state.endgameFinalRound) {
    turnLine += " · Final round: each player takes one more turn";
  }
  turnInfo.textContent = turnLine;
  if (roomCodeDisplay) {
    roomCodeDisplay.textContent = currentRoom;
  }

  // Gems on board
  boardGems.innerHTML = "";
  if (state.gems) {
    Object.entries(state.gems).forEach(([gem, count]) => {
      const pill = document.createElement("div");
      pill.className = `pill gem-${gem}`;
      pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`, "stone");
      pill.title = `${gem}: ${count}`;
      boardGems.appendChild(pill);
    });
  }
  // Mirror board supply counts into the Take Gems controls.
  document.querySelectorAll(".take-gem-option").forEach((row) => {
    const gem = row.getAttribute("data-gem");
    if (!gem) {
      return;
    }
    const supplyEl = row.querySelector(".take-gem-option__supply");
    if (!supplyEl) {
      return;
    }
    const count = state.gems && Number.isFinite(Number(state.gems[gem])) ? Number(state.gems[gem]) : 0;
    supplyEl.textContent = `Board: ${count}`;
  });

  // Nobles
  nobles.innerHTML = "";
  if (state.nobles) {
    state.nobles.forEach((n) => {
      const card = document.createElement("div");
      card.className = "noble-card";
      const req = n.requirements || {};

      const portrait = document.createElement("div");
      portrait.className = "noble-card__portrait";
      portrait.setAttribute("aria-hidden", "true");

      const body = document.createElement("div");
      body.className = "noble-card__body";

      const head = document.createElement("div");
      head.className = "noble-card__head";
      head.textContent = `${n.name} · ${n.points} prestige`;

      const row = document.createElement("div");
      row.className = "pill-row noble-card__reqs";
      Object.entries(req).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`, "stone");
        pill.title = `${gem}: ${count}`;
        row.appendChild(pill);
      });

      body.appendChild(head);
      body.appendChild(row);
      card.appendChild(portrait);
      card.appendChild(body);
      nobles.appendChild(card);
    });
  }

  // Levels
  [1, 2, 3].forEach((lvl) => {
    const ul = document.getElementById(`level-${lvl}`);
    ul.innerHTML = "";
    const cards = state.levels?.[lvl] || [];
    const levelCol = ul.closest(".level-col");
    const levelHeading = levelCol ? levelCol.querySelector("h3") : null;
    if (levelHeading) {
      const baseLabel = `LEVEL ${lvl}`;
      const deckLeft =
        state &&
        state.deckRemaining &&
        Number.isFinite(Number(state.deckRemaining[lvl]))
          ? Number(state.deckRemaining[lvl])
          : cards.length;
      levelHeading.textContent = `${baseLabel} · ${deckLeft} left`;
    }
    cards.forEach((card, index) => {
      const li = document.createElement("li");
      li.className = `dev-card dev-card--level-${lvl}`;
      li.style.cursor = "pointer";

      const cost = card.cost || {};
      const bonusGem = card.bonusGem || "";
      const bonusName = gemDisplayName(bonusGem);
      const pts = Number(card.points) || 0;
      const costDesc = devCardCostEntries(cost)
        .map(([g, n]) => `${n} ${gemDisplayName(g)}`)
        .join(", ");
      li.setAttribute(
        "aria-label",
        `Level ${lvl} development card, ${pts} prestige, ${bonusName} bonus. Cost: ${costDesc || "none"}.`
      );

      const face = document.createElement("div");
      face.className = "dev-card__face";

      const bg = document.createElement("div");
      bg.className = "dev-card__bg";
      const perCardArt = resolveDevCardArtImageUrl(lvl, card);
      const bgMap =
        typeof window !== "undefined" && window.__SPLENDOR_DEV_CARD_BG__ && typeof window.__SPLENDOR_DEV_CARD_BG__ === "object"
          ? window.__SPLENDOR_DEV_CARD_BG__
          : null;
      bg.style.removeProperty("--dev-card-art");
      if (perCardArt) {
        bg.style.setProperty("--dev-card-art", `url(${JSON.stringify(perCardArt)})`);
        li.classList.add("dev-card--per-card-art");
      } else if (bgMap && bgMap[lvl]) {
        bg.style.setProperty("--dev-card-art", `url(${JSON.stringify(String(bgMap[lvl]))})`);
        li.classList.add("dev-card--per-card-art");
      }

      const vignette = document.createElement("div");
      vignette.className = "dev-card__vignette";
      vignette.setAttribute("aria-hidden", "true");

      const prestige = document.createElement("div");
      prestige.className = "dev-card__prestige";
      prestige.textContent = pts > 0 ? String(pts) : "";
      prestige.setAttribute("aria-hidden", "true");

      const bonusWrap = document.createElement("div");
      bonusWrap.className = "dev-card__bonus";
      const bonusSprite = document.createElement("span");
      bonusSprite.className = "dev-card__bonus-sprite";
      bonusSprite.innerHTML = gemSpriteMarkup(bonusGem || "DIAMOND", "stone");
      bonusSprite.setAttribute("title", `${bonusName} bonus`);
      bonusWrap.appendChild(bonusSprite);

      const costsCol = document.createElement("div");
      costsCol.className = "dev-card__costs";
      devCardCostEntries(cost).forEach(([gem, count]) => {
        const chip = document.createElement("div");
        chip.className = `dev-card__cost dev-card__cost--${gem}`;
        chip.innerHTML = `${gemSpriteMarkup(gem, "stone")}<span class="dev-card__cost-num">${count}</span>`;
        chip.title = `${count} ${gemDisplayName(gem)}`;
        costsCol.appendChild(chip);
      });

      face.appendChild(bg);
      face.appendChild(vignette);
      face.appendChild(prestige);
      face.appendChild(bonusWrap);
      face.appendChild(costsCol);

      const chrome = document.createElement("div");
      chrome.className = "dev-card__chrome";

      const cardActions = document.createElement("div");
      cardActions.className = "dev-card__actions";
      const buyBtn = document.createElement("button");
      buyBtn.type = "button";
      buyBtn.className = "card-buy-btn";
      buyBtn.textContent = "Buy";
      buyBtn.disabled = !isMyTurn || !card.affordable;
      const canBuy = isMyTurn && !!card.affordable;
      buyBtn.title = canBuy ? "Buy this card" : !isMyTurn ? "Not your turn" : "Not affordable yet";
      if (isMyTurn && card.affordable) {
        buyBtn.classList.add("card-buy-btn--affordable");
      }
      const reserveBtn = document.createElement("button");
      reserveBtn.type = "button";
      reserveBtn.className = "secondary";
      reserveBtn.textContent = "Reserve";
      reserveBtn.disabled = !isMyTurn || currentReservedCount >= 3;
      const canReserve = isMyTurn && currentReservedCount < 3;
      reserveBtn.title = canReserve ? "Reserve this card" : !isMyTurn ? "Not your turn" : "Reserve full (3/3)";
      if (!canBuy && !canReserve) {
        li.classList.add("dev-card--locked");
        li.title = !isMyTurn ? "This card is locked: not your turn." : "This card is locked: cannot buy or reserve right now.";
      }

      buyBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        try {
          const result = await postAction({ type: "purchaseVisible", level: lvl, index });
          const msg = document.getElementById("action-message");
          msg.textContent = result.message || (result.success ? "Purchased." : "Purchase failed.");
          msg.className = "message " + (result.success ? "ok" : "error");
          if (result.success) {
            showToast("Card purchased.");
          }
          const newState = await fetchState();
          renderState(newState);
        } catch (e2) {
          const msg = document.getElementById("action-message");
          msg.textContent = "Error purchasing card.";
          msg.className = "message error";
        }
      });

      reserveBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        try {
          const result = await postAction({ type: "reserve", level: lvl, index });
          const msg = document.getElementById("action-message");
          msg.textContent = result.message || (result.success ? "Reserved." : "Reserve failed.");
          msg.className = "message " + (result.success ? "ok" : "error");
          if (result.success) {
            showToast("Card reserved successfully.");
          }
          const newState = await fetchState();
          renderState(newState);
        } catch (e2) {
          const msg = document.getElementById("action-message");
          msg.textContent = "Error reserving card.";
          msg.className = "message error";
        }
      });

      cardActions.appendChild(buyBtn);
      cardActions.appendChild(reserveBtn);
      chrome.appendChild(cardActions);
      li.appendChild(face);
      li.appendChild(chrome);

      li.addEventListener("click", async () => {
        if (!isMyTurn) {
          return;
        }
        try {
          const result = await postAction({ type: "purchaseVisible", level: lvl, index });
          const msg = document.getElementById("action-message");
          msg.textContent = result.message || (result.success ? "Purchased." : "Purchase failed.");
          msg.className = "message " + (result.success ? "ok" : "error");
          if (result.success) {
            showToast("Card purchased.");
          }
          const newState = await fetchState();
          renderState(newState);
        } catch (e) {
          const msg = document.getElementById("action-message");
          msg.textContent = "Error purchasing card.";
          msg.className = "message error";
        }
      });

      ul.appendChild(li);
    });
  });

  // Players
  players.innerHTML = "";
  if (state.players) {
    const orderedPlayers = [];
    for (let i = 0; i < state.players.length; i++) {
      orderedPlayers.push(state.players[(i + playerViewOffset) % state.players.length]);
    }
    orderedPlayers.forEach((p) => {
      const card = document.createElement("div");
      card.className = "player-card";
      if (p.name === currentDisplayName) {
        card.classList.add("current");
      }
      const name = document.createElement("div");
      name.className = "player-name";
      name.textContent = p.name;
      const meta = document.createElement("div");
      meta.className = "player-meta";
      const totalCoins = Object.values(p.gems || {}).reduce((sum, n) => sum + Number(n || 0), 0);
      meta.textContent = `${p.human ? "Human" : "AI"} · ${p.prestige} pts · Coins: ${totalCoins}`;
      const afkMeta = document.createElement("div");
      afkMeta.className = "player-afk-meta";
      const afkSec = Number.isFinite(Number(p.afkSeconds)) ? Number(p.afkSeconds) : 0;
      const forcedAi = !!p.forcedAi;
      if (p.human && forcedAi) {
        afkMeta.textContent = "AI takeover active";
      } else if (p.human && afkSec >= 15) {
        afkMeta.textContent = `AFK: ${afkSec}s`;
      } else {
        afkMeta.textContent = "";
      }
      const gemsRow = document.createElement("div");
      gemsRow.className = "pill-row";
      ["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX", "GOLD"].forEach((gem) => {
        const count = Number((p.gems || {})[gem] || 0);
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        if (count <= 0) {
          pill.classList.add("pill--zero");
        }
        pill.innerHTML = gemPillMarkup(gem, `${count}`, "stone");
        gemsRow.appendChild(pill);
      });
      const bonusesRow = document.createElement("div");
      bonusesRow.className = "pill-row";
      ["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX"].forEach((gem) => {
        const count = Number((p.bonuses || {})[gem] || 0);
        if (count <= 0) {
          return;
        }
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `+${count}`, "stone");
        bonusesRow.appendChild(pill);
      });
      const extra = document.createElement("div");
      extra.style.fontSize = "0.78rem";
      extra.style.marginTop = "3px";
      const nobleText = p.noble ? `Noble: ${p.noble}` : "No noble yet";
      const boughtCount = Number.isFinite(Number(p.purchasedCards))
        ? Number(p.purchasedCards)
        : Array.isArray(p.boughtCards)
          ? p.boughtCards.length
          : Object.values(p.bonuses || {}).reduce((sum, n) => sum + Number(n || 0), 0);
      extra.textContent = `${nobleText} · Reserved: ${p.reservedCount} · Bought: ${boughtCount} cards`;

      card.appendChild(name);
      card.appendChild(meta);
      if (afkMeta.textContent) {
        card.appendChild(afkMeta);
      }
      const canManageAfk =
        currentPlayerName === currentOwner &&
        !!p.human &&
        p.name !== currentOwner &&
        Number.isFinite(Number(state.afkAiThresholdSeconds)) &&
        Number(p.afkSeconds) >= Number(state.afkAiThresholdSeconds) &&
        !forcedAi;
      if (canManageAfk) {
        const afkBtn = document.createElement("button");
        afkBtn.type = "button";
        afkBtn.className = "secondary small-btn player-afk-btn";
        afkBtn.textContent = "Switch AFK to AI";
        afkBtn.addEventListener("click", async () => {
          const ok = window.confirm(
            `${p.name} has been AFK for ${afkSec}s. Switch to AI takeover? They can resume control when they return.`
          );
          if (!ok) {
            return;
          }
          try {
            const resp = await postAfkAi({
              room: currentRoom,
              ownerName: currentPlayerName,
              targetName: p.name,
              enable: true,
              difficulty: "medium",
            });
            if (!resp.success) {
              showToast(resp.message || "Could not enable AI takeover.", "error");
              return;
            }
            showToast(`${p.name} switched to AI takeover.`);
            const next = await fetchState();
            renderState(next);
          } catch (e) {
            showToast(e && e.message ? e.message : "Could not enable AI takeover.", "error");
          }
        });
        card.appendChild(afkBtn);
      }
      card.appendChild(gemsRow);
      if (bonusesRow.childElementCount > 0) {
        card.appendChild(bonusesRow);
      }

      const boughtList = Array.isArray(p.boughtCards) ? p.boughtCards : [];
      if (boughtList.length > 0) {
        const boughtWrap = document.createElement("div");
        boughtWrap.className = "player-bought-row";
        const boughtHeader = document.createElement("div");
        boughtHeader.className = "player-bought-header";
        const boughtLabel = document.createElement("div");
        boughtLabel.className = "player-bought-label";
        boughtLabel.textContent = `Bought cards (${boughtList.length})`;
        const boughtToggle = document.createElement("button");
        boughtToggle.type = "button";
        boughtToggle.className = "secondary small-btn player-bought-toggle";
        boughtToggle.textContent = "Show";
        boughtHeader.appendChild(boughtLabel);
        boughtHeader.appendChild(boughtToggle);
        boughtWrap.appendChild(boughtHeader);
        const boughtSlots = document.createElement("div");
        boughtSlots.className = "player-bought-slots";
        boughtSlots.classList.add("hidden");
        boughtToggle.addEventListener("click", () => {
          const hidden = boughtSlots.classList.toggle("hidden");
          boughtToggle.textContent = hidden ? "Show" : "Hide";
        });
        boughtList.forEach((bc) => {
          const mini = document.createElement("div");
          mini.className = "purchased-mini";
          mini.title = `Level ${bc.level} · +${bc.bonusAbbr || ""} bonus${Number(bc.points) > 0 ? ` · ${bc.points} prestige` : ""}`;
          const face = document.createElement("div");
          face.className = "purchased-mini__face";
          const bgB = document.createElement("div");
          bgB.className = "purchased-mini__bg";
          const artUrlB = resolveDevCardArtImageUrl(Number(bc.level) || 1, bc);
          if (artUrlB) {
            bgB.style.backgroundImage = `url(${JSON.stringify(artUrlB)})`;
            face.classList.add("purchased-mini__face--art");
          } else {
            bgB.classList.add(`purchased-mini__bg--level-${Number(bc.level) || 1}`);
          }
          const ptsB = document.createElement("div");
          ptsB.className = "purchased-mini__pts";
          ptsB.textContent = Number(bc.points) > 0 ? String(bc.points) : "";
          const bonusB = document.createElement("div");
          bonusB.className = "purchased-mini__bonus";
          bonusB.innerHTML = gemSpriteMarkup(bc.bonusGem || "DIAMOND", "stone");
          face.appendChild(bgB);
          face.appendChild(ptsB);
          face.appendChild(bonusB);
          mini.appendChild(face);
          const capB = document.createElement("div");
          capB.className = "purchased-mini__cap";
          capB.textContent = `L${bc.level} · ${bc.bonusAbbr || ""}`;
          mini.appendChild(capB);
          boughtSlots.appendChild(mini);
        });
        boughtWrap.appendChild(boughtSlots);
        card.appendChild(boughtWrap);
      }

      const reservedList = Array.isArray(p.reserved) ? p.reserved : [];
      if (reservedList.length > 0) {
        const resWrap = document.createElement("div");
        resWrap.className = "player-reserved-row";
        const resLabel = document.createElement("div");
        resLabel.className = "player-reserved-label";
        resLabel.textContent = "Reserved cards";
        resWrap.appendChild(resLabel);
        const resSlots = document.createElement("div");
        resSlots.className = "player-reserved-slots";
        reservedList.forEach((rc, rIdx) => {
          const mini = document.createElement("div");
          mini.className = "reserved-mini";
          const face = document.createElement("div");
          face.className = "reserved-mini__face";
          const bgR = document.createElement("div");
          bgR.className = "reserved-mini__bg";
          const artUrl = resolveDevCardArtImageUrl(rc.level, rc);
          if (artUrl) {
            bgR.style.backgroundImage = `url(${JSON.stringify(artUrl)})`;
            face.classList.add("reserved-mini__face--art");
          } else {
            bgR.classList.add(`reserved-mini__bg--level-${Number(rc.level) || 1}`);
          }
          const ptsEl = document.createElement("div");
          ptsEl.className = "reserved-mini__pts";
          ptsEl.textContent = Number(rc.points) > 0 ? String(rc.points) : "";
          const bonusEl = document.createElement("div");
          bonusEl.className = "reserved-mini__bonus";
          bonusEl.innerHTML = gemSpriteMarkup(rc.bonusGem || "DIAMOND", "stone");
          face.appendChild(bgR);
          face.appendChild(ptsEl);
          face.appendChild(bonusEl);
          mini.appendChild(face);
          const cap = document.createElement("div");
          cap.className = "reserved-mini__cap";
          cap.textContent = `L${rc.level} · ${rc.bonusAbbr || ""}`;
          mini.appendChild(cap);
          const costBadges = document.createElement("div");
          costBadges.className = "pill-row reserved-mini__costs";
          devCardCostEntries(rc.cost || {}).forEach(([gem, count]) => {
            const pill = document.createElement("div");
            pill.className = `pill gem-${gem}`;
            pill.innerHTML = gemPillMarkup(gem, `${count}`, "stone");
            pill.title = `${count} ${gemDisplayName(gem)}`;
            costBadges.appendChild(pill);
          });
          mini.appendChild(costBadges);
          const buyR = document.createElement("button");
          buyR.type = "button";
          buyR.className = "reserved-mini__buy card-buy-btn";
          buyR.textContent = "Buy reserved";
          const isYou = p.name === currentDisplayName;
          const canBuyReserved = isMyTurn && isYou && p.human && !!rc.affordable;
          buyR.disabled = !canBuyReserved;
          buyR.title = canBuyReserved
            ? "Buy this reserved card"
            : isYou
              ? "Not affordable yet"
              : "Other player’s reserve";
          buyR.addEventListener("click", async (e) => {
            e.stopPropagation();
            if (buyR.disabled) {
              return;
            }
            await postPurchaseReserved(rIdx);
          });
          mini.appendChild(buyR);
          resSlots.appendChild(mini);
        });
        resWrap.appendChild(resSlots);
        card.appendChild(resWrap);
      }

      card.appendChild(extra);
      players.appendChild(card);
    });
  }

  // Recommended next steps for the current human player.
  if (recommendedSteps) {
    recommendedSteps.innerHTML = "";
    const suggestions = [];
    const affordableVisible = Object.values(state.levels || {})
      .flat()
      .some((c) => !!c.affordable);
    if (!isMyTurn) {
      suggestions.push(isAiTurn ? "Wait for AI turns to finish." : "Wait for the other player to finish their turn.");
    } else {
      if (currentReservedCount >= 3) {
        suggestions.push("Reserve is full (3/3). Purchase a reserved card to free a slot.");
      }
      if (affordableVisible) {
        suggestions.push("Buy an affordable visible card to gain points and a permanent bonus.");
      } else {
        suggestions.push("Take gems to prepare your next purchase.");
      }
      if (currentReservedCount > 0) {
        suggestions.push("Check 'Purchase Reserved' if one of your reserved cards is now affordable.");
      }
      if (Array.isArray(state.claimableNobles) && state.claimableNobles.length > 0) {
        suggestions.push("You qualify for a noble now; it will visit automatically at end of your turn.");
      }
      suggestions.push("Plan toward nobles by building bonus colors, not loose gems.");
    }
    suggestions.slice(0, 4).forEach((text) => {
      const li = document.createElement("li");
      li.textContent = text;
      recommendedSteps.appendChild(li);
    });
  }

  // Activity feed from backend (human + AI moves).
  if (activityLog) {
    activityLog.innerHTML = "";
    if (actions.length === 0) {
      const li = document.createElement("li");
      li.textContent = "No actions yet.";
      activityLog.appendChild(li);
    } else {
      actions.slice(-6).reverse().forEach((entry) => {
        const li = document.createElement("li");
        li.textContent = entry;
        activityLog.appendChild(li);
      });
    }
  }

  // Only show available actions for the active local player's turn.
  const takeBtn = document.getElementById("take-gems-btn");
  if (takeBtn) {
    takeBtn.disabled = !isMyTurn;
  }
  document.querySelectorAll("#take-gems-options button").forEach((btn) => {
    btn.disabled = !isMyTurn;
  });
  // Realtime popups: new action and turn changes.
  if (!suppressRealtimeToasts) {
    if (actions.length > lastSeenActionCount) {
      const latestAction = actions[actions.length - 1];
      if (latestAction) {
        showToast(latestAction);
      }
    }
    if (lastSeenTurnNumber !== null && turnNo !== lastSeenTurnNumber) {
      showToast(`Turn ${turnNo}: ${currentDisplayName}'s turn`);
    }
  }
  lastSeenActionCount = actions.length;
  lastSeenTurnNumber = turnNo;
  suppressRealtimeToasts = false;
}

function renderLobbyStatus(state) {
  const ownerEl = document.getElementById("lobby-owner");
  const playersEl = document.getElementById("lobby-players");
  const readyBtn = document.getElementById("ready-btn");
  const startBtn = document.getElementById("start-game-btn");
  const waitingPlayerNameInput = document.getElementById("waiting-player-name");
  const hostAiControls = document.getElementById("host-ai-controls");
  const roomCodeDisplay = document.getElementById("room-code-display");
  const waitingRoomCode = document.getElementById("waiting-room-code");
  const lobby = state && state.lobby ? state.lobby : null;
  if (!lobby) {
    return;
  }
  currentOwner = lobby.owner || "";
  if (ownerEl) {
    ownerEl.textContent = `Owner: ${currentOwner}`;
  }
  if (playersEl) {
    playersEl.innerHTML = "";
    const isOwner = currentPlayerName === currentOwner;
    (lobby.players || []).forEach((p) => {
      const li = document.createElement("li");
      const typeText = p.isAi ? `AI (${p.aiDifficulty || "easy"})` : "Human";
      li.textContent = `${p.name} - ${typeText} - ${p.ready ? "✅ Ready" : "⏳ Not ready"}`;
      if (isOwner && p.name !== currentOwner) {
        const kickBtn = document.createElement("button");
        kickBtn.type = "button";
        kickBtn.className = "secondary small-btn";
        kickBtn.textContent = "Kick";
        kickBtn.addEventListener("click", async () => {
          try {
            const resp = await postKick({
              room: currentRoom,
              ownerName: currentPlayerName,
              targetName: p.name,
            });
            if (!resp.success) {
              showToast(resp.message || "Kick failed.", "error");
              return;
            }
            const nextState = await fetchState();
            renderLobbyStatus(nextState);
          } catch (e) {
            showToast(e && e.message ? e.message : "Kick failed.", "error");
          }
        });
        li.appendChild(document.createTextNode(" "));
        li.appendChild(kickBtn);
      }
      playersEl.appendChild(li);
    });
  }
  if (roomCodeDisplay) {
    roomCodeDisplay.textContent = currentRoom;
  }
  if (waitingRoomCode) {
    waitingRoomCode.textContent = currentRoom;
  }
  const me = (lobby.players || []).find((p) => samePlayerName(p && p.name, currentPlayerName));
  if (me && me.name && me.name !== currentPlayerName) {
    currentPlayerName = String(me.name);
    rememberPlayerName(currentPlayerName);
  }
  currentReady = !!(me && me.ready);
  if (waitingPlayerNameInput) {
    waitingPlayerNameInput.value = currentPlayerName || "";
  }
  if (hostAiControls) {
    hostAiControls.classList.toggle("hidden", currentPlayerName !== currentOwner);
  }
  if (readyBtn) {
    readyBtn.textContent = currentReady ? "Unready" : "Ready";
  }
  if (startBtn) {
    const isOwner = currentPlayerName === currentOwner;
    startBtn.disabled = !isOwner || !lobby.canStart;
  }
}

function setupGemSelection() {
  if (uiState.gemUiInitialized) {
    return;
  }
  const container = document.getElementById("take-gems-options");
  const selected = new Map(); // gem -> count

  const gemTypes = ["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX", "GOLD"];
  const countByGem = new Map();

  function setGemCount(gem, next) {
    const value = Math.max(0, Math.min(2, Number(next) || 0));
    if (value === 0) {
      selected.delete(gem);
    } else {
      selected.set(gem, value);
    }
    const countEl = countByGem.get(gem);
    if (countEl) {
      countEl.textContent = String(value);
    }
  }

  gemTypes.forEach((gem) => {
    const row = document.createElement("div");
    row.className = `pill gem-${gem} take-gem-option`;
    row.setAttribute("data-gem", gem);

    const preview = document.createElement("div");
    preview.className = "take-gem-option__preview";
    preview.innerHTML = `${gemSpriteMarkup(gem, "stone")}<span class="take-gem-option__supply">Board: 0</span>`;

    const controls = document.createElement("div");
    controls.className = "take-gem-option__controls";

    if (gem === "GOLD") {
      row.classList.add("take-gem-option--readonly");
      controls.classList.add("hidden");
      row.appendChild(preview);
      row.appendChild(controls);
      container.appendChild(row);
      return;
    }

    const minusBtn = document.createElement("button");
    minusBtn.type = "button";
    minusBtn.className = "secondary small-btn take-gem-option__btn";
    minusBtn.textContent = "−";
    minusBtn.setAttribute("aria-label", `Decrease ${gem} gems`);
    minusBtn.addEventListener("click", () => {
      const current = selected.get(gem) || 0;
      setGemCount(gem, current - 1);
    });

    const count = document.createElement("span");
    count.className = "take-gem-option__count";
    count.textContent = "0";
    countByGem.set(gem, count);

    const plusBtn = document.createElement("button");
    plusBtn.type = "button";
    plusBtn.className = "secondary small-btn take-gem-option__btn";
    plusBtn.textContent = "+";
    plusBtn.setAttribute("aria-label", `Increase ${gem} gems`);
    plusBtn.addEventListener("click", () => {
      const current = selected.get(gem) || 0;
      setGemCount(gem, current + 1);
    });

    controls.appendChild(minusBtn);
    controls.appendChild(count);
    controls.appendChild(plusBtn);
    row.appendChild(preview);
    row.appendChild(controls);
    container.appendChild(row);
  });

  const btnConfirm = document.getElementById("take-gems-btn");
  const messageEl = document.getElementById("action-message");

  function parseDiscardInput(raw, needed) {
    const text = String(raw || "").toUpperCase();
    const tokens = text.split(/[^A-Z]+/).filter(Boolean);
    const out = [];
    for (const token of tokens) {
      const ch = token.charAt(0);
      if (GEM_ABBR_TO_NAME[ch]) {
        out.push(ch);
      }
    }
    return out.length === needed ? out : null;
  }

  function validateTakeGemSelection(selectedMap, state) {
    const picks = [];
    selectedMap.forEach((count, gem) => {
      if (gem === "GOLD") {
        return;
      }
      const n = Number(count) || 0;
      if (n > 0) {
        picks.push([gem, n]);
      }
    });
    const total = picks.reduce((sum, [, n]) => sum + n, 0);
    if (total === 0) {
      return { ok: false, message: "Select some gems first." };
    }
    // Legal Splendor take-gems moves:
    // 1) exactly 3 different colors (1 each), OR
    // 2) exactly 2 of one color (only if >=4 available on board).
    if (total === 3 && picks.length === 3 && picks.every(([, n]) => n === 1)) {
      return { ok: true, message: "" };
    }
    if (total === 2 && picks.length === 1 && picks[0][1] === 2) {
      const gem = picks[0][0];
      const boardCount = state && state.gems ? Number(state.gems[gem] || 0) : 0;
      if (boardCount >= 4) {
        return { ok: true, message: "" };
      }
      return {
        ok: false,
        message: `Cannot take 2 ${gemDisplayName(gem)} gems unless 4+ are on the board.`,
      };
    }
    return {
      ok: false,
      message: "Invalid gem pick. Choose exactly 3 different gems, or 2 of one color.",
    };
  }

  btnConfirm.addEventListener("click", async () => {
    const gems = [];
    selected.forEach((count, gem) => {
      for (let i = 0; i < count; i++) {
        gems.push(gem[0]); // use abbreviation's first letter
      }
    });
    const validation = validateTakeGemSelection(selected, latestState);
    if (!validation.ok) {
      messageEl.textContent = validation.message;
      messageEl.className = "message error";
      showToast(validation.message, "error");
      return;
    }
    try {
      const myPlayer =
        latestState && Array.isArray(latestState.players)
          ? latestState.players.find((p) => p.name === latestState.currentPlayer)
          : null;
      const currentTotal = myPlayer && myPlayer.gems ? Object.values(myPlayer.gems).reduce((s, n) => s + Number(n || 0), 0) : 0;
      const overflow = Math.max(0, currentTotal + gems.length - 10);
      let discard = [];
      if (overflow > 0) {
        const reply = window.prompt(
          `You must discard ${overflow} gem(s). Type letters separated by spaces/commas (R E S D O G).`,
          ""
        );
        if (reply == null) {
          messageEl.textContent = "Take gems cancelled.";
          messageEl.className = "message error";
          return;
        }
        const parsed = parseDiscardInput(reply, overflow);
        if (!parsed) {
          messageEl.textContent = `Invalid discard input. Enter exactly ${overflow} gem letter(s).`;
          messageEl.className = "message error";
          return;
        }
        discard = parsed;
      }
      const result = await postAction({ type: "takeGems", gems, discard });
      messageEl.textContent = result.message || (result.success ? "Action done." : "Action failed.");
      messageEl.className = "message " + (result.success ? "ok" : "error");
      const state = await fetchState();
      renderState(state);
      // Clear previous selection to avoid accidental repeated submits.
      gemTypes.forEach((gem) => setGemCount(gem, 0));
    } catch (e) {
      messageEl.textContent = "Error sending action.";
      messageEl.className = "message error";
    }
  });

  uiState.gemUiInitialized = true;
}

async function postPurchaseReserved(index) {
  const msg = document.getElementById("action-message");
  try {
    const result = await postAction({ type: "purchaseReserved", index });
    msg.textContent =
      result.message || (result.success ? "Reserved card purchased." : "Purchase reserved failed.");
    msg.className = "message " + (result.success ? "ok" : "error");
    if (result.success) {
      showToast("Reserved card purchased.");
    }
    const state = await fetchState();
    renderState(state);
  } catch (e) {
    msg.textContent = "Error purchasing reserved card.";
    msg.className = "message error";
  }
}

function setupOtherActions() {
  if (uiState.actionUiBound) {
    return;
  }
  uiState.actionUiBound = true;
}

async function init() {
  const startScreen = document.getElementById("start-screen");
  const waitingRoom = document.getElementById("waiting-room");
  const gameUi = document.getElementById("game-ui");
  const videoPanel = document.querySelector(".video-panel");
  const startBtn = document.getElementById("start-game-btn");
  const addAiBtn = document.getElementById("add-ai-btn");
  const addAiDifficulty = document.getElementById("add-ai-difficulty");
  const backToHomeBtn = document.getElementById("back-to-home-btn");
  const createRoomBtn = document.getElementById("create-room-btn");
  const readyBtn = document.getElementById("ready-btn");
  const joinRoomBtn = document.getElementById("join-room-btn");
  const copyRoomCodeBtn = document.getElementById("copy-room-code-btn");
  const copyRoomCodeLobbyBtn = document.getElementById("copy-room-code-btn-lobby");
  const startGuidedBtn = document.getElementById("start-guided-btn");
  const numPlayersSelect = document.getElementById("start-num-players");
  const joinRoomDialog = document.getElementById("join-room-dialog");
  const joinRoomCodeInput = document.getElementById("join-room-code-input");
  const joinDialogCancel = document.getElementById("join-dialog-cancel");
  const joinDialogConfirm = document.getElementById("join-dialog-confirm");
  const playerNameInput = document.getElementById("player-name");
  const waitingPlayerNameInput = document.getElementById("waiting-player-name");
  const waitingRoomCode = document.getElementById("waiting-room-code");
  const rulebookOverlay = document.getElementById("rulebook-overlay");
  const rulebookBody = document.getElementById("rulebook-body");
  const openTutorialBtn = document.getElementById("open-tutorial-btn");
  const openRulebookBtn = document.getElementById("open-rulebook-btn");
  const closeTutorialBtn = document.getElementById("close-tutorial-btn");
  const guidedOverlay = document.getElementById("guided-overlay");
  const guidedCard = document.querySelector("#guided-overlay .guided-card");
  const guidedHeader = document.querySelector("#guided-overlay .guided-header");
  const guidedStepLabel = document.getElementById("guided-step-label");
  const guidedStepText = document.getElementById("guided-step-text");
  const guidedPrevBtn = document.getElementById("guided-prev-btn");
  const guidedNextBtn = document.getElementById("guided-next-btn");
  const guidedExitBtn = document.getElementById("guided-exit-btn");
  const guidedSizeSmBtn = document.getElementById("guided-size-sm");
  const guidedSizeLgBtn = document.getElementById("guided-size-lg");
  let liveSyncTimer = null;
  let liveSyncInFlight = false;

  let guidedIndex = 0;
  let guidedSize = "md";
  let dragState = null;

  function openRulebook() {
    if (!rulebookOverlay) {
      return;
    }
    rulebookOverlay.classList.remove("hidden");
    if (rulebookBody) {
      rulebookBody.scrollTop = 0;
    }
  }

  function closeRulebook() {
    rulebookOverlay?.classList.add("hidden");
  }

  if (rulebookOverlay) {
    rulebookOverlay.querySelectorAll(".rulebook-toc a").forEach((a) => {
      a.addEventListener("click", (e) => {
        const href = a.getAttribute("href");
        if (!href || href.charAt(0) !== "#") {
          return;
        }
        e.preventDefault();
        const el = document.getElementById(href.slice(1));
        if (el && rulebookBody) {
          el.scrollIntoView({ behavior: "smooth", block: "start" });
        }
      });
    });
    rulebookOverlay.addEventListener("click", (e) => {
      if (e.target === rulebookOverlay) {
        closeRulebook();
      }
    });
  }

  function applyGuidedSizeClass() {
    guidedOverlay.classList.remove("guided-size-sm", "guided-size-lg");
    if (guidedSize === "sm") {
      guidedOverlay.classList.add("guided-size-sm");
    } else if (guidedSize === "lg") {
      guidedOverlay.classList.add("guided-size-lg");
    }
  }

  function beginGuidedDrag(e) {
    // Allow size buttons to be clicked without starting drag
    if (e.target.closest(".guided-size-controls")) {
      return;
    }
    const rect = guidedOverlay.getBoundingClientRect();
    dragState = {
      startX: e.clientX,
      startY: e.clientY,
      left: rect.left,
      top: rect.top,
    };
    // Switch from bottom-centered transform mode to explicit position mode.
    guidedOverlay.style.left = `${rect.left}px`;
    guidedOverlay.style.top = `${rect.top}px`;
    guidedOverlay.style.bottom = "auto";
    guidedOverlay.style.transform = "none";
    document.body.style.userSelect = "none";
  }

  function onGuidedDrag(e) {
    if (!dragState) {
      return;
    }
    const dx = e.clientX - dragState.startX;
    const dy = e.clientY - dragState.startY;
    const overlayRect = guidedOverlay.getBoundingClientRect();
    const maxLeft = Math.max(8, window.innerWidth - overlayRect.width - 8);
    const maxTop = Math.max(8, window.innerHeight - overlayRect.height - 8);
    const nextLeft = Math.min(Math.max(8, dragState.left + dx), maxLeft);
    const nextTop = Math.min(Math.max(8, dragState.top + dy), maxTop);
    guidedOverlay.style.left = `${nextLeft}px`;
    guidedOverlay.style.top = `${nextTop}px`;
  }

  function endGuidedDrag() {
    if (!dragState) {
      return;
    }
    dragState = null;
    document.body.style.userSelect = "";
  }

  function updateLobbyReadiness() {
    const nameOk = playerNameInput && playerNameInput.value.trim().length > 0;
    startBtn.disabled = true;
    startGuidedBtn.disabled = false;
    if (createRoomBtn) {
      createRoomBtn.disabled = false;
    }
    if (readyBtn) {
      readyBtn.disabled = !currentRoom;
    }
    if (joinRoomBtn) {
      joinRoomBtn.disabled = !nameOk;
    }
  }

  function openJoinRoomDialog() {
    if (!joinRoomDialog || !joinRoomCodeInput) {
      return;
    }
    joinRoomCodeInput.value = getRememberedRoom() || "";
    joinRoomDialog.classList.remove("hidden");
    joinRoomCodeInput.focus();
    joinRoomCodeInput.select();
  }

  function closeJoinRoomDialog() {
    joinRoomDialog?.classList.add("hidden");
  }

  async function submitJoinFromDialog() {
    const code = joinRoomCodeInput ? String(joinRoomCodeInput.value || "").trim() : "";
    if (!code) {
      showToast("Enter the host’s game code.", "error");
      return;
    }
    try {
      rememberRoom(code);
      const joined = await postJoinRoom({ room: currentRoom, name: currentPlayerName });
      if (!joined.success) {
        alert(joined.message || "Could not join room.");
        return;
      }
      syncRoomToBrowserUrl(currentRoom);
      closeJoinRoomDialog();
      clearTransientUi();
      closeGuided();
      closeRulebook();
      startLiveSync();
      const state = await fetchState();
      if (state && state.lobby && state.lobby.gameStarted) {
        enterGameUiFromState(state, true);
      } else {
        showWaitingRoom();
        renderLobbyStatus(state);
      }
      suppressRealtimeToasts = true;
      lastSeenActionCount = 0;
      lastSeenTurnNumber = null;
      randomizePlayerView(state);
      showToast(`Joined game code: ${currentRoom}`);
    } catch (e) {
      const message = e && e.message ? e.message : "Could not join room.";
      showToast(message, "error");
      alert(message);
    }
  }

  function showWaitingRoom() {
    startScreen.classList.add("hidden");
    waitingRoom.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.add("hidden");
    if (waitingRoomCode) {
      waitingRoomCode.textContent = currentRoom;
    }
    if (waitingPlayerNameInput) {
      waitingPlayerNameInput.value = currentPlayerName || "Guest";
    }
  }

  function showGameUi() {
    startScreen.classList.add("hidden");
    waitingRoom.classList.add("hidden");
    gameUi.classList.remove("hidden");
    if (videoPanel) videoPanel.classList.add("hidden");
  }

  function enterGameUiFromState(state, resetPerspective) {
    setupGemSelection();
    setupOtherActions();
    if (resetPerspective) {
      suppressRealtimeToasts = true;
      lastSeenActionCount = 0;
      lastSeenTurnNumber = null;
      randomizePlayerView(state);
    }
    showGameUi();
    renderState(state);
  }

  async function runLiveSyncTick() {
    if (liveSyncInFlight) {
      return;
    }
    const inWaitingRoom = !waitingRoom.classList.contains("hidden");
    const inGame = !gameUi.classList.contains("hidden");
    const onHomeScreen =
      !startScreen.classList.contains("hidden") &&
      waitingRoom.classList.contains("hidden") &&
      gameUi.classList.contains("hidden");
    if (onHomeScreen) {
      return;
    }
    liveSyncInFlight = true;
    try {
      let state = await fetchState();
      const inLobbyList =
        !!(
          currentPlayerName &&
          state &&
          state.lobby &&
          Array.isArray(state.lobby.players) &&
          state.lobby.players.some((p) => samePlayerName(p && p.name, currentPlayerName))
        );
      const now = Date.now();
      if ((inWaitingRoom || inGame) && currentPlayerName && !inLobbyList && now - lastAutoRejoinAt > 7000) {
        lastAutoRejoinAt = now;
        try {
          const rejoin = await postJoinRoom({ room: currentRoom, name: currentPlayerName });
          if (rejoin && rejoin.success && now - lastRecoveryToastAt > 15000) {
            lastRecoveryToastAt = now;
            showToast("Session recovered after idle restart.");
          }
          state = await fetchState();
          currentPlayerName = resolveCanonicalLobbyName(state, currentPlayerName);
          rememberPlayerName(currentPlayerName);
        } catch (_) {
          // Ignore and retry later; prevents UI from being stuck after backend idles/restarts.
        }
      }
      if (inWaitingRoom) {
        renderLobbyStatus(state);
        if (state && state.lobby && state.lobby.gameStarted) {
          enterGameUiFromState(state, true);
          showToast("Match started.");
        }
      } else if (inGame) {
        renderState(state);
      }
    } catch (_) {
      // Ignore transient network errors; next tick will retry.
    } finally {
      liveSyncInFlight = false;
    }
  }

  function startLiveSync() {
    if (liveSyncTimer != null) {
      return;
    }
    liveSyncTimer = window.setInterval(runLiveSyncTick, 1500);
  }

  function stopLiveSync() {
    if (liveSyncTimer == null) {
      return;
    }
    window.clearInterval(liveSyncTimer);
    liveSyncTimer = null;
  }

  function showHome(resetLobbyRoom) {
    waitingRoom.classList.add("hidden");
    startScreen.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.remove("hidden");
    if (resetLobbyRoom) {
      stopLiveSync();
      clearRoomFromBrowserUrl();
      rememberRoom("Room A");
      updateLobbyReadiness();
    }
  }

  function getEnteredName() {
    if (!playerNameInput) {
      return "";
    }
    return (playerNameInput.value || "").trim();
  }

  function ensureCurrentPlayerName(preferredPrefix) {
    const enteredName = getEnteredName();
    if (enteredName) {
      currentPlayerName = enteredName;
      rememberPlayerName(currentPlayerName);
      return currentPlayerName;
    }
    const remembered = getRememberedPlayerName();
    if (remembered) {
      currentPlayerName = remembered;
      if (playerNameInput) {
        playerNameInput.value = remembered;
      }
      return currentPlayerName;
    }
    const fallback = generateFallbackName(preferredPrefix || "Guest");
    currentPlayerName = fallback;
    if (playerNameInput) {
      playerNameInput.value = fallback;
    }
    rememberPlayerName(fallback);
    showToast(`Using temporary name: ${fallback}`);
    return currentPlayerName;
  }

  function updateGuidedOverlay() {
    guidedStepLabel.textContent = `Step ${guidedIndex + 1} of ${guidedSteps.length}`;
    guidedStepText.textContent = guidedSteps[guidedIndex];
    guidedPrevBtn.disabled = guidedIndex === 0;
    guidedNextBtn.textContent = guidedIndex === guidedSteps.length - 1 ? "Finish" : "Next";

    // Clear previous highlights
    document.querySelectorAll(".guided-highlight").forEach((el) => {
      el.classList.remove("guided-highlight");
    });

    // Point to relevant part of the UI for the current step
    if (guidedIndex === 0 || guidedIndex === 1) {
      const status = document.querySelector(".status");
      if (status) status.classList.add("guided-highlight");
    } else if (guidedIndex === 2) {
      const gemsPanel = document.querySelector(".board .panel:first-child");
      if (gemsPanel) gemsPanel.classList.add("guided-highlight");
    } else if (guidedIndex === 3) {
      const takeGemsBlock = document.getElementById("take-gems-options")?.parentElement;
      if (takeGemsBlock) takeGemsBlock.classList.add("guided-highlight");
    } else if (guidedIndex === 4) {
      // Manual reserve block is hidden; highlight level 1 cards where Reserve buttons live.
      const level1 = document.getElementById("level-1");
      if (level1) level1.classList.add("guided-highlight");
    } else if (guidedIndex === 5 || guidedIndex === 6) {
      const level1 = document.getElementById("level-1");
      if (level1) level1.classList.add("guided-highlight");
    } else if (guidedIndex === 7) {
      const playersPanel = document.querySelector(".players.panel");
      if (playersPanel) playersPanel.classList.add("guided-highlight");
    } else if (guidedIndex === 8) {
      const noblesPanel = document.querySelector(".board .panel:nth-child(2)");
      if (noblesPanel) noblesPanel.classList.add("guided-highlight");
    }
  }

  function openGuided() {
    clearTransientUi();
    applyGuidedSizeClass();
    guidedIndex = 0;
    tutorialFlags.active = true;
    tutorialFlags.tookGemsOnce = false;
    tutorialFlags.boughtLevel1Once = false;
    closeRulebook();
    updateGuidedOverlay();
    guidedOverlay.classList.remove("hidden");
  }

  function closeGuided() {
    guidedOverlay.classList.add("hidden");
    tutorialFlags.active = false;
    // Remove any remaining highlights
    document.querySelectorAll(".guided-highlight").forEach((el) => {
      el.classList.remove("guided-highlight");
    });
  }

  async function returnToMenu() {
    clearTransientUi();
    closeGuided();
    tutorialFlags.active = false;
    closeRulebook();
    showHome(true);
    // Reset backend state in background; UI should not wait on network.
    postQuit().catch(() => {
      /* user is already back at menu; ignore API quit failure */
    });
  }

  if (createRoomBtn) {
    createRoomBtn.addEventListener("click", async () => {
      try {
        currentPlayerName = ensureCurrentPlayerName("Host");
        const numPlayers = parseInt(numPlayersSelect.value, 10);
        const roomInput = "";
        const created = await postCreateRoom({
          room: roomInput,
          ownerName: currentPlayerName,
          numPlayers,
          p1Type: "human",
          p2Type: "human",
          p3Type: "human",
          p4Type: "human",
        });
        rememberRoom(created.room || roomInput || "Room A");
        syncRoomToBrowserUrl(currentRoom);
        updateLobbyReadiness();
        showWaitingRoom();
        startLiveSync();
        showToast(`Room created: ${currentRoom}`);
        const state = await fetchState();
        renderLobbyStatus(state);
      } catch (e) {
        const message = e && e.message ? e.message : "Could not create room.";
        showToast(message, "error");
        alert(message);
      }
    });
  }

  startGuidedBtn.addEventListener("click", async () => {
    try {
      rememberRoom(getRememberedRoom() || "Room A");
      clearTransientUi();
      closeGuided();
      closeRulebook();

      await postNewGame({
        numPlayers: 2,
        p1Type: "human",
        p2Type: "medium",
        p3Type: "human",
        p4Type: "human",
      });
      startScreen.classList.add("hidden");
      gameUi.classList.remove("hidden");
      if (videoPanel) videoPanel.classList.add("hidden");

      setupGemSelection();
      setupOtherActions();

      const state = await fetchState();
      suppressRealtimeToasts = true;
      lastSeenActionCount = 0;
      lastSeenTurnNumber = null;
      randomizePlayerView(state);
      renderState(state);
      openGuided();
    } catch (e) {
      alert("Failed to start tutorial. Is the server running?");
    }
  });

  if (joinRoomBtn) {
    joinRoomBtn.addEventListener("click", () => {
      currentPlayerName = ensureCurrentPlayerName("Guest");
      openJoinRoomDialog();
    });
  }

  if (joinDialogCancel) {
    joinDialogCancel.addEventListener("click", () => closeJoinRoomDialog());
  }
  if (joinDialogConfirm) {
    joinDialogConfirm.addEventListener("click", () => submitJoinFromDialog());
  }
  if (joinRoomDialog) {
    joinRoomDialog.addEventListener("click", (e) => {
      if (e.target === joinRoomDialog) {
        closeJoinRoomDialog();
      }
    });
  }
  if (joinRoomCodeInput) {
    joinRoomCodeInput.addEventListener("keydown", (e) => {
      if (e.key === "Enter") {
        e.preventDefault();
        submitJoinFromDialog();
      }
    });
  }
  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape" && joinRoomDialog && !joinRoomDialog.classList.contains("hidden")) {
      closeJoinRoomDialog();
    }
  });

  if (readyBtn) {
    readyBtn.addEventListener("click", async () => {
      try {
        if (!currentPlayerName) {
          currentPlayerName = ensureCurrentPlayerName("Guest");
        }
        const nextReady = !currentReady;
        await postReady({ room: currentRoom, name: currentPlayerName, ready: nextReady });
        const state = await fetchState();
        renderLobbyStatus(state);
        showToast(nextReady ? "You are ready." : "You are not ready.");
      } catch (_) {
        alert("Could not update ready status.");
      }
    });
  }

  if (addAiBtn) {
    addAiBtn.addEventListener("click", async () => {
      try {
        const difficulty = addAiDifficulty && addAiDifficulty.value ? addAiDifficulty.value : "easy";
        const resp = await postAddAi({
          room: currentRoom,
          ownerName: currentPlayerName,
          difficulty,
        });
        if (!resp.success) {
          showToast(resp.message || "Could not add AI.", "error");
          return;
        }
        const state = await fetchState();
        renderLobbyStatus(state);
        showToast(`Added ${resp.name} (${difficulty}).`);
      } catch (e) {
        showToast(e && e.message ? e.message : "Could not add AI.", "error");
      }
    });
  }

  startBtn.addEventListener("click", async () => {
    try {
      const resp = await postStartRoom({ room: currentRoom, ownerName: currentPlayerName });
      if (!resp.success) {
        alert(resp.message || "Could not start match.");
        return;
      }
      clearTransientUi();
      closeGuided();
      closeRulebook();
      const state = await fetchState();
      enterGameUiFromState(state, true);
      showToast("Match started.");
    } catch (_) {
      alert("Could not start match.");
    }
  });

  if (copyRoomCodeBtn) {
    copyRoomCodeBtn.addEventListener("click", async () => {
      await copyRoomCodeToClipboard();
    });
  }
  if (copyRoomCodeLobbyBtn) {
    copyRoomCodeLobbyBtn.addEventListener("click", async () => {
      await copyRoomCodeToClipboard();
    });
  }
  if (backToHomeBtn) {
    backToHomeBtn.addEventListener("click", () => {
      showHome();
    });
  }

  if (openTutorialBtn) {
    openTutorialBtn.addEventListener("click", () => {
      openRulebook();
    });
  }

  if (openRulebookBtn) {
    openRulebookBtn.addEventListener("click", () => {
      openRulebook();
    });
  }

  if (closeTutorialBtn) {
    closeTutorialBtn.addEventListener("click", () => {
      closeRulebook();
    });
  }

  document.addEventListener("keydown", (e) => {
    if (e.key !== "Escape") {
      return;
    }
    if (rulebookOverlay && !rulebookOverlay.classList.contains("hidden")) {
      closeRulebook();
    }
  });

  numPlayersSelect.addEventListener("change", updateLobbyReadiness);
  if (playerNameInput) {
    const remembered = getRememberedPlayerName();
    if (remembered && !playerNameInput.value.trim()) {
      playerNameInput.value = remembered;
    }
    playerNameInput.addEventListener("input", () => {
      const v = (playerNameInput.value || "").trim();
      if (v) {
        rememberPlayerName(v);
      }
      updateLobbyReadiness();
    });
  }

  // Initial state
  updateLobbyReadiness();
  startLiveSync();
  const pathRoom = roomFromUrlPath();
  const rememberedRoom = getRememberedRoom();
  if (pathRoom) {
    rememberRoom(pathRoom);
  } else if (rememberedRoom) {
    // Refresh fallback: restore last known room when URL has no room segment.
    rememberRoom(rememberedRoom);
  }
  const rememberedName = getRememberedPlayerName();
  if (rememberedName) {
    currentPlayerName = rememberedName;
    if (playerNameInput && !playerNameInput.value.trim()) {
      playerNameInput.value = rememberedName;
    }
  }
  updateLobbyReadiness();
  try {
    const shouldTryAutoJoin = !!currentPlayerName && !!(pathRoom || rememberedRoom);
    if (shouldTryAutoJoin) {
      try {
        await postJoinRoom({ room: currentRoom, name: currentPlayerName });
      } catch (_) {
        // Ignore auto-join failure; fall back to lobby view with message below.
      }
    }
    const state = await fetchState();
    currentPlayerName = resolveCanonicalLobbyName(state, currentPlayerName);
    if (currentPlayerName) {
      rememberPlayerName(currentPlayerName);
    }
    renderLobbyStatus(state);
    const inRoom =
      currentPlayerName &&
      state &&
      state.lobby &&
      Array.isArray(state.lobby.players) &&
      state.lobby.players.some((p) => samePlayerName(p && p.name, currentPlayerName));
    if (inRoom) {
      if (state.lobby.gameStarted) {
        enterGameUiFromState(state, true);
      } else {
        showWaitingRoom();
      }
    } else if (pathRoom || rememberedRoom) {
      showHome(false);
      showToast(`Could not auto-rejoin ${currentRoom}. Enter your name, then Join Room.`, "error");
    } else {
      showHome(false);
    }
  } catch (_) {
    showHome(false);
  }

  guidedPrevBtn.addEventListener("click", () => {
    if (guidedIndex > 0) {
      guidedIndex -= 1;
      updateGuidedOverlay();
    }
  });

  guidedNextBtn.addEventListener("click", () => {
    // Basic validation for interactive feel
    if (guidedIndex === 3 && !tutorialFlags.tookGemsOnce) {
      const ok = confirm(
        "You haven't successfully taken gems yet. You can either keep trying, or continue the tutorial anyway."
      );
      if (!ok) {
        return;
      }
    }
    if (guidedIndex === 6 && !tutorialFlags.boughtLevel1Once) {
      const ok = confirm(
        "You haven't purchased a card yet (maybe you chose gems that don't match any card cost). Do you want to continue the tutorial anyway?"
      );
      if (!ok) {
        return;
      }
    }

    if (guidedIndex < guidedSteps.length - 1) {
      guidedIndex += 1;
      updateGuidedOverlay();
    } else {
      returnToMenu();
    }
  });

  guidedExitBtn.addEventListener("click", () => {
    returnToMenu();
  });

  if (guidedSizeSmBtn) {
    guidedSizeSmBtn.addEventListener("click", () => {
      guidedSize = guidedSize === "sm" ? "md" : "sm";
      applyGuidedSizeClass();
    });
  }

  if (guidedSizeLgBtn) {
    guidedSizeLgBtn.addEventListener("click", () => {
      guidedSize = guidedSize === "lg" ? "md" : "lg";
      applyGuidedSizeClass();
    });
  }

  if (guidedHeader) {
    guidedHeader.addEventListener("mousedown", (e) => {
      if (e.button !== 0) {
        return;
      }
      beginGuidedDrag(e);
    });
  }
  window.addEventListener("mousemove", onGuidedDrag);
  window.addEventListener("mouseup", endGuidedDrag);

  document.getElementById("quit-game-btn").addEventListener("click", () => {
    returnToMenu();
  });
}

window.addEventListener("DOMContentLoaded", init);

