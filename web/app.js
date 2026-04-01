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
  if (typeof document === "undefined") {
    return;
  }
  try {
    const base =
      typeof window !== "undefined" && window.__SPLENDOR_MEDIA_BASE__ != null
        ? String(window.__SPLENDOR_MEDIA_BASE__).trim().replace(/\/+$/, "")
        : "";
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
let currentRoom = "Room A";
const LAST_ROOM_KEY = "splendor.lastRoom";
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
  const res = await fetch(`${API_STATE}?room=${roomParam}`);
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

const KNOWN_GEMS = new Set(["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX", "GOLD"]);

/**
 * Sprite sheets (see web/media): chips.jpg = 6 tokens ONYX…GOLD; gems.png = 5 faceted gems ONYX…RUBY.
 * kind "chip" = table tokens (board, hand, take gems). kind "stone" = faceted gems (card bonus, costs, noble reqs). GOLD always uses chip art.
 */
function gemSpriteMarkup(gem, kind) {
  const raw = gem && String(gem);
  const g = KNOWN_GEMS.has(raw) ? raw : "DIAMOND";
  const useChip = kind !== "stone" || g === "GOLD";
  const type = useChip ? "chip" : "stone";
  return `<span class="gem-sprite gem-sprite--${type} gem-sprite--${g}" role="img" aria-hidden="true"></span>`;
}

function gemPillMarkup(gem, text, kind = "chip") {
  return `<span class="gem-pill-content">${gemSpriteMarkup(gem, kind)}<span>${text}</span></span>`;
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
  const file = splendorHexanomeDevCardFilename(globalIdx);
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
  const withRoom = { ...payload, room: currentRoom };
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
  const turnInfo = document.getElementById("turn-info");
  const roomCodeDisplay = document.getElementById("room-code-display");
  const gameOver = document.getElementById("game-over");
  const boardGems = document.getElementById("board-gems");
  const nobles = document.getElementById("nobles");
  const players = document.getElementById("players");
  const recommendedSteps = document.getElementById("recommended-steps");
  const activityLog = document.getElementById("activity-log");
  const currentPlayerData = (state.players || []).find((p) => p.name === state.currentPlayer) || null;
  const isHumanTurn = !!state.isHumanTurn;
  const currentReservedCount = currentPlayerData ? (currentPlayerData.reservedCount || 0) : 0;
  const turnNo = Number.isFinite(state.turnNumber) ? state.turnNumber : 1;
  const actions = Array.isArray(state.recentActions) ? state.recentActions : [];

  if (state.gameOver) {
    gameOver.classList.remove("hidden");
    const winnerName = state.winner || "Unknown";
    gameOver.textContent = `Game Over – Winner: ${winnerName}`;
  } else {
    gameOver.classList.add("hidden");
  }

  turnInfo.textContent = `Turn ${turnNo} · Current Player: ${state.currentPlayer}${isHumanTurn ? " (Your turn)" : " (AI turn)"}`;
  if (roomCodeDisplay) {
    roomCodeDisplay.textContent = `Game Code: ${currentRoom}`;
  }

  // Gems on board
  boardGems.innerHTML = "";
  if (state.gems) {
    Object.entries(state.gems).forEach(([gem, count]) => {
      const pill = document.createElement("div");
      pill.className = `pill gem-${gem}`;
      pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`);
      pill.title = `${gem}: ${count}`;
      boardGems.appendChild(pill);
    });
  }

  // Nobles
  nobles.innerHTML = "";
  if (state.nobles) {
    state.nobles.forEach((n) => {
      const card = document.createElement("div");
      card.className = "noble-card";
      const req = n.requirements || {};

      const head = document.createElement("div");
      head.className = "noble-card__head";
      head.textContent = `${n.name} · ${n.points} pts`;

      const row = document.createElement("div");
      row.className = "pill-row noble-card__reqs";
      Object.entries(req).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`, "stone");
        pill.title = `${gem}: ${count}`;
        row.appendChild(pill);
      });

      card.appendChild(head);
      card.appendChild(row);
      nobles.appendChild(card);
    });
  }

  // Levels
  [1, 2, 3].forEach((lvl) => {
    const ul = document.getElementById(`level-${lvl}`);
    ul.innerHTML = "";
    const cards = state.levels?.[lvl] || [];
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
      if (perCardArt) {
        bg.style.backgroundImage = `url(${JSON.stringify(perCardArt)})`;
        li.classList.add("dev-card--per-card-art");
      } else if (bgMap && bgMap[lvl]) {
        bg.style.backgroundImage = `url(${JSON.stringify(String(bgMap[lvl]))})`;
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

      const slotHint = document.createElement("span");
      slotHint.className = "dev-card__slot";
      slotHint.textContent = String(index);

      face.appendChild(bg);
      face.appendChild(vignette);
      face.appendChild(prestige);
      face.appendChild(bonusWrap);
      face.appendChild(costsCol);
      face.appendChild(slotHint);

      const chrome = document.createElement("div");
      chrome.className = "dev-card__chrome";

      const cardActions = document.createElement("div");
      cardActions.className = "dev-card__actions";
      const buyBtn = document.createElement("button");
      buyBtn.type = "button";
      buyBtn.className = "card-buy-btn";
      buyBtn.textContent = "Buy";
      buyBtn.disabled = !isHumanTurn || !card.affordable;
      const reserveBtn = document.createElement("button");
      reserveBtn.type = "button";
      reserveBtn.className = "secondary";
      reserveBtn.textContent = "Reserve";
      reserveBtn.disabled = !isHumanTurn || currentReservedCount >= 3;

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
        if (!isHumanTurn) {
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
      if (p.name === state.currentPlayer) {
        card.classList.add("current");
      }
      const name = document.createElement("div");
      name.className = "player-name";
      name.textContent = p.name;
      const meta = document.createElement("div");
      meta.className = "player-meta";
      const totalCoins = Object.values(p.gems || {}).reduce((sum, n) => sum + Number(n || 0), 0);
      meta.textContent = `${p.human ? "Human" : "AI"} · ${p.prestige} pts · Coins: ${totalCoins}`;
      const gemsRow = document.createElement("div");
      gemsRow.className = "pill-row";
      Object.entries(p.gems || {}).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `${gem}: ${count}`);
        gemsRow.appendChild(pill);
      });
      const bonusesRow = document.createElement("div");
      bonusesRow.className = "pill-row";
      Object.entries(p.bonuses || {}).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `+${count}`, "stone");
        bonusesRow.appendChild(pill);
      });
      const extra = document.createElement("div");
      extra.style.fontSize = "0.78rem";
      extra.style.marginTop = "3px";
      const nobleText = p.noble ? `Noble: ${p.noble}` : "No noble yet";
      const boughtCount = Object.values(p.bonuses || {}).reduce((sum, n) => sum + Number(n || 0), 0);
      extra.textContent = `${nobleText} · Reserved: ${p.reservedCount} · Bought: ${boughtCount}`;

      card.appendChild(name);
      card.appendChild(meta);
      card.appendChild(gemsRow);
      card.appendChild(bonusesRow);

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
          const buyR = document.createElement("button");
          buyR.type = "button";
          buyR.className = "reserved-mini__buy card-buy-btn";
          buyR.textContent = "Buy reserved";
          const isYou = p.name === state.currentPlayer;
          const canBuyReserved = isHumanTurn && isYou && p.human && !!rc.affordable;
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
    if (!isHumanTurn) {
      suggestions.push("Wait for AI turns to finish.");
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

  // Only show available actions for the active human turn.
  const takeBtn = document.getElementById("take-gems-btn");
  if (takeBtn) {
    takeBtn.disabled = !isHumanTurn;
  }
  document.querySelectorAll("#take-gems-options button").forEach((btn) => {
    btn.disabled = !isHumanTurn;
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
      showToast(`Turn ${turnNo}: ${state.currentPlayer}'s turn`);
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
    roomCodeDisplay.textContent = `Game Code: ${currentRoom}`;
  }
  if (waitingRoomCode) {
    waitingRoomCode.textContent = `Game Code: ${currentRoom}`;
  }
  const me = (lobby.players || []).find((p) => p.name === currentPlayerName);
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

  const gemTypes = ["RUBY", "EMERALD", "SAPPHIRE", "DIAMOND", "ONYX"];
  gemTypes.forEach((gem) => {
    const btn = document.createElement("button");
    btn.type = "button";
    btn.className = `pill gem-${gem} take-gem-option`;
    btn.setAttribute("aria-label", `Toggle ${gem} for taking gems`);
    btn.innerHTML = gemPillMarkup(gem, gem, "chip");
    btn.addEventListener("click", () => {
      const current = selected.get(gem) || 0;
      if (current === 0) {
        selected.set(gem, 1);
        btn.style.outline = "2px solid #fff";
      } else if (current === 1) {
        selected.set(gem, 2);
        btn.style.outline = "2px solid #ff9800";
      } else {
        selected.delete(gem);
        btn.style.outline = "none";
      }
    });
    container.appendChild(btn);
  });

  const btnConfirm = document.getElementById("take-gems-btn");
  const messageEl = document.getElementById("action-message");

  btnConfirm.addEventListener("click", async () => {
    const gems = [];
    selected.forEach((count, gem) => {
      for (let i = 0; i < count; i++) {
        gems.push(gem[0]); // use abbreviation's first letter
      }
    });
    if (gems.length === 0) {
      messageEl.textContent = "Select some gems first.";
      messageEl.className = "message error";
      return;
    }
    try {
      const result = await postAction({ type: "takeGems", gems });
      messageEl.textContent = result.message || (result.success ? "Action done." : "Action failed.");
      messageEl.className = "message " + (result.success ? "ok" : "error");
      const state = await fetchState();
      renderState(state);
      // Clear previous selection to avoid accidental repeated submits.
      selected.clear();
      container.querySelectorAll("button").forEach((b) => {
        b.style.outline = "none";
      });
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
  const rejoinRoomBtn = document.getElementById("rejoin-room-btn");
  const copyRoomCodeBtn = document.getElementById("copy-room-code-btn");
  const copyRoomCodeLobbyBtn = document.getElementById("copy-room-code-btn-lobby");
  const startGuidedBtn = document.getElementById("start-guided-btn");
  const numPlayersSelect = document.getElementById("start-num-players");
  const p1Type = document.getElementById("player1-type");
  const p2Type = document.getElementById("player2-type");
  const p3Type = document.getElementById("player3-type");
  const p4Type = document.getElementById("player4-type");
  const roomNameInput = document.getElementById("room-name");
  const playerNameInput = document.getElementById("player-name");
  const waitingPlayerNameInput = document.getElementById("waiting-player-name");
  const waitingRoomCode = document.getElementById("waiting-room-code");
  const p1Row = p1Type.closest(".player-type-row");
  const p2Row = p2Type.closest(".player-type-row");
  const p3Row = p3Type.closest(".player-type-row");
  const p4Row = p4Type.closest(".player-type-row");
  const tutorialPanel = document.getElementById("tutorial-panel");
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

  let guidedIndex = 0;
  let guidedSize = "md";
  let dragState = null;

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

  function updatePlayerRows() {
    const n = parseInt(numPlayersSelect.value, 10);
    p1Row.classList.toggle("hidden", n < 1);
    p2Row.classList.toggle("hidden", n < 2);
    p3Row.classList.toggle("hidden", n < 3);
    p4Row.classList.toggle("hidden", n < 4);
    updateLobbyReadiness();
  }

  function updateLobbyReadiness() {
    const roomSet = !roomNameInput || roomNameInput.value.trim().length > 0;
    startBtn.disabled = true;
    startGuidedBtn.disabled = false;
    if (createRoomBtn) {
      createRoomBtn.disabled = false;
    }
    if (readyBtn) {
      readyBtn.disabled = !currentRoom;
    }
    if (joinRoomBtn) {
      joinRoomBtn.disabled = !roomSet;
    }
    if (rejoinRoomBtn) {
      rejoinRoomBtn.disabled = !roomSet;
    }
  }

  function showWaitingRoom() {
    startScreen.classList.add("hidden");
    waitingRoom.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.add("hidden");
    if (waitingRoomCode) {
      waitingRoomCode.textContent = `Game Code: ${currentRoom}`;
    }
    if (waitingPlayerNameInput) {
      waitingPlayerNameInput.value = currentPlayerName || "Guest";
    }
  }

  function showHome() {
    waitingRoom.classList.add("hidden");
    startScreen.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.remove("hidden");
  }

  function getEnteredName() {
    if (!playerNameInput) {
      return "";
    }
    return (playerNameInput.value || "").trim();
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
    tutorialPanel.classList.add("hidden");
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
    tutorialPanel.classList.add("hidden");
    showHome();
    // Reset backend state in background; UI should not wait on network.
    postQuit().catch(() => {
      /* user is already back at menu; ignore API quit failure */
    });
  }

  if (createRoomBtn) {
    createRoomBtn.addEventListener("click", async () => {
      try {
        const enteredName = getEnteredName();
        if (!enteredName) {
          showToast("Please enter your name first.", "error");
          return;
        }
        currentPlayerName = enteredName;
        const numPlayers = parseInt(numPlayersSelect.value, 10);
        const roomInput = "";
        const created = await postCreateRoom({
          room: roomInput,
          ownerName: currentPlayerName,
          numPlayers,
          p1Type: p1Type.value,
          p2Type: p2Type.value,
          p3Type: p3Type.value,
          p4Type: p4Type.value,
        });
        rememberRoom(created.room || roomInput || "Room A");
        if (roomNameInput) {
          roomNameInput.value = currentRoom;
        }
        showWaitingRoom();
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
      rememberRoom(roomNameInput && roomNameInput.value.trim() ? roomNameInput.value.trim() : "Room A");
      clearTransientUi();
      closeGuided();
      tutorialPanel.classList.add("hidden");

      // Guided tutorial uses Player 1 as human and Player 2 as AI.
      // If Player 2 dropdown is set to Human, default to AI (Medium).
      let p2 = p2Type.value;
      if (!p2 || p2 === "human") {
        p2 = "medium";
      }
      await postNewGame({
        numPlayers: 2,
        p1Type: "human",
        p2Type: p2,
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
    joinRoomBtn.addEventListener("click", async () => {
      try {
        const enteredName = getEnteredName();
        if (!enteredName) {
          showToast("Please enter your name first.", "error");
          return;
        }
        currentPlayerName = enteredName;
        rememberRoom(roomNameInput && roomNameInput.value.trim() ? roomNameInput.value.trim() : "Room A");
        const joined = await postJoinRoom({ room: currentRoom, name: currentPlayerName });
        if (!joined.success) {
          alert(joined.message || "Could not join room.");
          return;
        }
        clearTransientUi();
        closeGuided();
        tutorialPanel.classList.add("hidden");
        showWaitingRoom();
        const state = await fetchState();
        renderLobbyStatus(state);
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
    });
  }

  if (readyBtn) {
    readyBtn.addEventListener("click", async () => {
      try {
        if (!currentPlayerName) {
          const enteredName = getEnteredName();
          if (!enteredName) {
            showToast("Please enter your name first.", "error");
            return;
          }
          currentPlayerName = enteredName;
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
      tutorialPanel.classList.add("hidden");
      const state = await fetchState();
      suppressRealtimeToasts = true;
      lastSeenActionCount = 0;
      lastSeenTurnNumber = null;
      randomizePlayerView(state);
      startScreen.classList.add("hidden");
      waitingRoom.classList.add("hidden");
      gameUi.classList.remove("hidden");
      if (videoPanel) videoPanel.classList.add("hidden");
      setupGemSelection();
      setupOtherActions();
      renderState(state);
      showToast("Match started.");
    } catch (_) {
      alert("Could not start match.");
    }
  });

  if (rejoinRoomBtn) {
    rejoinRoomBtn.addEventListener("click", async () => {
      const remembered = getRememberedRoom();
      if (!remembered) {
        return;
      }
      rememberRoom(remembered);
      if (roomNameInput) {
        roomNameInput.value = remembered;
      }
      try {
        const state = await fetchState();
        showWaitingRoom();
        renderLobbyStatus(state);
        showToast(`Rejoined game code: ${currentRoom}`);
      } catch (_) {
        alert("Could not rejoin room.");
      }
    });
  }

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

  openTutorialBtn.addEventListener("click", () => {
    tutorialPanel.classList.remove("hidden");
  });

  if (openRulebookBtn) {
    openRulebookBtn.addEventListener("click", () => {
      tutorialPanel.classList.remove("hidden");
      tutorialPanel.scrollIntoView({ behavior: "smooth", block: "start" });
    });
  }

  closeTutorialBtn.addEventListener("click", () => {
    tutorialPanel.classList.add("hidden");
  });

  numPlayersSelect.addEventListener("change", updatePlayerRows);
  if (roomNameInput) {
    roomNameInput.addEventListener("input", updateLobbyReadiness);
  }
  if (playerNameInput) {
    playerNameInput.addEventListener("input", updateLobbyReadiness);
  }

  // Initial state
  const remembered = getRememberedRoom();
  if (remembered && roomNameInput) {
    roomNameInput.value = remembered;
  }
  if (rejoinRoomBtn) {
    rejoinRoomBtn.classList.toggle("hidden", !remembered);
  }
  updatePlayerRows();
  updateLobbyReadiness();
  try {
    const state = await fetchState();
    renderLobbyStatus(state);
  } catch (_) {
    // ignore initial fetch failure
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

