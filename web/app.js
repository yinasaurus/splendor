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
const API_STATE = `${API_BASE}/api/state`;
const API_ACTION = `${API_BASE}/api/action`;
const API_NEW_GAME = `${API_BASE}/api/newgame`;
const API_QUIT = `${API_BASE}/api/quit`;
let currentRoom = "Room A";
const LAST_ROOM_KEY = "splendor.lastRoom";
let playerViewOffset = 0;

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

const GEM_ICON_URI = {
  RUBY:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%23ef5350' stroke='%23b71c1c' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23ffcdd2' opacity='0.35'/></svg>"),
  EMERALD:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%2366bb6a' stroke='%231b5e20' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23e8f5e9' opacity='0.35'/></svg>"),
  SAPPHIRE:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%2342a5f5' stroke='%230d47a1' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23e3f2fd' opacity='0.35'/></svg>"),
  DIAMOND:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%23eceff1' stroke='%2390a4ae' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23ffffff' opacity='0.55'/></svg>"),
  ONYX:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%23263238' stroke='%23000000' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23b0bec5' opacity='0.2'/></svg>"),
  GOLD:
    "data:image/svg+xml;utf8," +
    encodeURIComponent("<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 24 24'><polygon points='12,2 20,9 16,21 8,21 4,9' fill='%23ffd54f' stroke='%23f57f17' stroke-width='1.8'/><polygon points='12,4.8 17.2,9.4 14.7,18.7 9.3,18.7 6.8,9.4' fill='%23fff8e1' opacity='0.4'/></svg>"),
};

function gemPillMarkup(gem, text) {
  const icon = GEM_ICON_URI[gem] || GEM_ICON_URI.DIAMOND;
  return `<span class="gem-pill-content"><img class="gem-pill-icon" src="${icon}" alt="${gem} gem" /><span>${text}</span></span>`;
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

const guidedSteps = [
  "Welcome!\n\nYou play as Player 1 against an AI. This walkthrough explains the main screen and the three core actions: take gems, reserve a card, and buy a card.",

  "Status bar (top)\n\nHere you see whose turn it is and whether the game has ended. When it is your turn, the Actions section below is yours to use.",

  "Board Gems\n\nThese chips are the supply. On your turn you can take gems from here (rules: 3 different colors, or 2 of the same if enough remain). You cannot take gold directly by \"taking gems\"—gold usually comes from reserving.",

  "Take Gems\n\nOpen the Take Gems row, pick a legal combination, then press \"Confirm Take Gems\". Try it once so you have tokens to spend later.\n\nTip: you cannot hold more than 10 gems total.",

  "Reserve a card\n\nUse a card's \"Reserve\" button (under each visible card).\n\n• Max 3 reserved cards.\n• Gain 1 gold if available.\n• Buy it later with \"Purchase Reserved\".\n\nReserve helps you lock a card before others take it.",

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

  // Gems on board
  boardGems.innerHTML = "";
  if (state.gems) {
    Object.entries(state.gems).forEach(([gem, count]) => {
      const pill = document.createElement("div");
      pill.className = `pill gem-${gem}`;
      pill.innerHTML = gemPillMarkup(gem, `${gem}: ${count}`);
      boardGems.appendChild(pill);
    });
  }

  // Nobles
  nobles.innerHTML = "";
  if (state.nobles) {
    state.nobles.forEach((n) => {
      const card = document.createElement("div");
      card.className = "card-item";
      const req = n.requirements || {};

      // Title line
      card.textContent = `${n.name} · ${n.points} pts`;

      // Requirement pills
      const row = document.createElement("div");
      row.className = "pill-row";
      Object.entries(req).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`);
        row.appendChild(pill);
      });

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
      li.className = "card-item";
      li.style.cursor = "pointer";

      const cost = card.cost || {};
      const bonusAbbr = card.bonusAbbr || "";
      const bonusGem = card.bonusGem || "";

      function gemName(gemEnum) {
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
          default:
            return gemEnum || "";
        }
      }

      li.innerHTML = `
        <div class="card-header">
          <span class="card-index">[${index}]</span>
          <span>Level ${lvl}</span>
          <span class="card-points">+${card.points} pts</span>
        </div>
      `;

      const statusRow = document.createElement("div");
      statusRow.className = "pill-row";
      const affordability = document.createElement("div");
      affordability.className = "pill " + (card.affordable ? "affordable" : "not-affordable");
      affordability.textContent = card.affordable ? "Affordable now" : "Need more gems";
      statusRow.appendChild(affordability);

      // Bonus pill
      const bonusRow = document.createElement("div");
      bonusRow.className = "pill-row card-bonus-row";
      const bonusPill = document.createElement("div");
      bonusPill.className = `pill gem-${bonusGem}`;
      bonusPill.innerHTML = gemPillMarkup(bonusGem, `Bonus: ${bonusAbbr} (${gemName(bonusGem)} discount)`);
      bonusRow.appendChild(bonusPill);

      // Cost pills
      const costRow = document.createElement("div");
      costRow.className = "pill-row card-cost-row";
      const costLabel = document.createElement("div");
      costLabel.className = "card-label";
      costLabel.textContent = "Cost:";
      costRow.appendChild(costLabel);
      Object.entries(cost).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.innerHTML = gemPillMarkup(gem, `${gem[0]}:${count}`);
        costRow.appendChild(pill);
      });

      li.appendChild(bonusRow);
      li.appendChild(costRow);
      li.appendChild(statusRow);

      const cardActions = document.createElement("div");
      cardActions.className = "card-actions";
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
      li.appendChild(cardActions);

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
        pill.innerHTML = gemPillMarkup(gem, `+${count} ${gem}`);
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
  const reservedBtn = document.getElementById("purchase-reserved-btn");
  if (reservedBtn) {
    reservedBtn.disabled = !isHumanTurn || currentReservedCount <= 0;
  }

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
    btn.className = `pill gem-${gem}`;
    btn.textContent = gem;
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

function setupOtherActions() {
  if (uiState.actionUiBound) {
    return;
  }
  const msg = document.getElementById("action-message");

  document.getElementById("purchase-reserved-btn").addEventListener("click", async () => {
    const index = parseInt(document.getElementById("purchase-reserved-index").value, 10) || 0;
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
  });

  uiState.actionUiBound = true;
}

async function init() {
  const startScreen = document.getElementById("start-screen");
  const gameUi = document.getElementById("game-ui");
  const videoPanel = document.querySelector(".video-panel");
  const startBtn = document.getElementById("start-game-btn");
  const joinRoomBtn = document.getElementById("join-room-btn");
  const rejoinRoomBtn = document.getElementById("rejoin-room-btn");
  const startGuidedBtn = document.getElementById("start-guided-btn");
  const numPlayersSelect = document.getElementById("start-num-players");
  const p1Type = document.getElementById("player1-type");
  const p2Type = document.getElementById("player2-type");
  const p3Type = document.getElementById("player3-type");
  const p4Type = document.getElementById("player4-type");
  const roomNameInput = document.getElementById("room-name");
  const p1Row = p1Type.closest(".player-type-row");
  const p2Row = p2Type.closest(".player-type-row");
  const p3Row = p3Type.closest(".player-type-row");
  const p4Row = p4Type.closest(".player-type-row");
  const readyChecks = [
    document.getElementById("player1-ready"),
    document.getElementById("player2-ready"),
    document.getElementById("player3-ready"),
    document.getElementById("player4-ready"),
  ];
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
    const n = parseInt(numPlayersSelect.value, 10);
    const allActiveReady = readyChecks.slice(0, n).every((cb) => cb && cb.checked);
    const roomSet = !roomNameInput || roomNameInput.value.trim().length > 0;
    const canStart = allActiveReady && roomSet;
    startBtn.disabled = !canStart;
    startGuidedBtn.disabled = !canStart;
    if (joinRoomBtn) {
      joinRoomBtn.disabled = !roomSet;
    }
    if (rejoinRoomBtn) {
      rejoinRoomBtn.disabled = !roomSet;
    }
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
    startScreen.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.remove("hidden");
    // Reset backend state in background; UI should not wait on network.
    postQuit().catch(() => {
      /* user is already back at menu; ignore API quit failure */
    });
  }

  startBtn.addEventListener("click", async () => {
    const numPlayers = parseInt(numPlayersSelect.value, 10);
    const body = {
      numPlayers,
      p1Type: p1Type.value,
      p2Type: p2Type.value,
      p3Type: p3Type.value,
      p4Type: p4Type.value,
    };
    try {
      rememberRoom(roomNameInput && roomNameInput.value.trim() ? roomNameInput.value.trim() : "Room A");
      clearTransientUi();
      // Ensure any previous guided/How-to-play state is fully cleared
      closeGuided();
      tutorialPanel.classList.add("hidden");

      await postNewGame(body);
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
    } catch (e) {
      alert("Failed to start game. Is the server running?");
    }
  });

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
      rememberRoom(roomNameInput && roomNameInput.value.trim() ? roomNameInput.value.trim() : "Room A");
      try {
        clearTransientUi();
        closeGuided();
        tutorialPanel.classList.add("hidden");
        const state = await fetchState();
        suppressRealtimeToasts = true;
        lastSeenActionCount = 0;
        lastSeenTurnNumber = null;
        randomizePlayerView(state);
        startScreen.classList.add("hidden");
        gameUi.classList.remove("hidden");
        if (videoPanel) videoPanel.classList.add("hidden");
        setupGemSelection();
        setupOtherActions();
        renderState(state);
        showToast(`Joined ${currentRoom}`);
      } catch (e) {
        alert("Could not join room. Is the server running?");
      }
    });
  }

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
        clearTransientUi();
        closeGuided();
        tutorialPanel.classList.add("hidden");
        const state = await fetchState();
        suppressRealtimeToasts = true;
        lastSeenActionCount = 0;
        lastSeenTurnNumber = null;
        randomizePlayerView(state);
        startScreen.classList.add("hidden");
        gameUi.classList.remove("hidden");
        if (videoPanel) videoPanel.classList.add("hidden");
        setupGemSelection();
        setupOtherActions();
        renderState(state);
        showToast(`Rejoined ${currentRoom}`);
      } catch (_) {
        alert("Could not rejoin room.");
      }
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
  readyChecks.forEach((cb) => cb && cb.addEventListener("change", updateLobbyReadiness));
  if (roomNameInput) {
    roomNameInput.addEventListener("input", updateLobbyReadiness);
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

