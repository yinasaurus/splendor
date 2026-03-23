const API_STATE = "/api/state";
const API_ACTION = "/api/action";
const API_NEW_GAME = "/api/newgame";

async function fetchState() {
  const res = await fetch(API_STATE);
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

async function postAction(payload) {
  const res = await fetch(API_ACTION, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
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
  const res = await fetch(API_NEW_GAME, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  });
  if (!res.ok) {
    throw new Error("New game failed");
  }
  return res.json();
}

const guidedSteps = [
  "Welcome! In this tutorial you will play as Player 1 against an AI opponent and learn the basic flow of Splendor.",
  "First, look at the top-left status bar: it shows whose turn it is and whether the game is over.",
  "Check the 'Board Gems' panel. These are the tokens you can take on your turn.",
  "Now, try the 'Take Gems' action: click a few gem buttons, then 'Confirm Take Gems'.",
  "After you have some gems, look at the Level 1 cards. Each card has a cost (what you pay) and a bonus (a permanent discount color).",
  "Try purchasing a card you can afford using 'Purchase Visible' (or by clicking a card). When you buy it, you permanently gain that card’s bonus color as a discount.",
  "Watch the 'Players' panel: your 'bonuses' are your permanent discounts, and they reduce future card costs of that color.",
  "Nobles are free points if your bonuses match their requirements. Plan your cards towards a noble.",
  "When any player reaches at least 15 prestige points (the default winning score), the game will finish the round and declare a winner.",
  "That's it! You can now exit the tutorial and play a full game, with or without AI."
];

function renderState(state) {
  const turnInfo = document.getElementById("turn-info");
  const gameOver = document.getElementById("game-over");
  const boardGems = document.getElementById("board-gems");
  const nobles = document.getElementById("nobles");
  const players = document.getElementById("players");

  if (state.gameOver) {
    gameOver.classList.remove("hidden");
    const winnerName = state.winner || "Unknown";
    gameOver.textContent = `Game Over – Winner: ${winnerName}`;
  } else {
    gameOver.classList.add("hidden");
  }

  turnInfo.textContent = `Current Player: ${state.currentPlayer}`;

  // Gems on board
  boardGems.innerHTML = "";
  if (state.gems) {
    Object.entries(state.gems).forEach(([gem, count]) => {
      const pill = document.createElement("div");
      pill.className = `pill gem-${gem}`;
      pill.textContent = `${gem}: ${count}`;
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
        pill.textContent = `${gem[0]}:${count}`;
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
          <span class="card-id">ID:${card.id}</span>
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
      bonusPill.textContent = `Bonus: ${bonusAbbr} (${gemName(bonusGem)} discount)`;
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
        pill.textContent = `${gem[0]}:${count}`;
        costRow.appendChild(pill);
      });

      li.appendChild(bonusRow);
      li.appendChild(costRow);
      li.appendChild(statusRow);

      const cardActions = document.createElement("div");
      cardActions.className = "card-actions";
      const buyBtn = document.createElement("button");
      buyBtn.type = "button";
      buyBtn.textContent = "Buy";
      buyBtn.disabled = !card.affordable;
      const reserveBtn = document.createElement("button");
      reserveBtn.type = "button";
      reserveBtn.className = "secondary";
      reserveBtn.textContent = "Reserve";

      buyBtn.addEventListener("click", async (e) => {
        e.stopPropagation();
        try {
          const result = await postAction({ type: "purchaseVisible", level: lvl, index });
          const msg = document.getElementById("action-message");
          msg.textContent = result.message || (result.success ? "Purchased." : "Purchase failed.");
          msg.className = "message " + (result.success ? "ok" : "error");
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
        try {
          const result = await postAction({ type: "purchaseVisible", level: lvl, index });
          const msg = document.getElementById("action-message");
          msg.textContent = result.message || (result.success ? "Purchased." : "Purchase failed.");
          msg.className = "message " + (result.success ? "ok" : "error");
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
    state.players.forEach((p) => {
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
      meta.textContent = `${p.human ? "Human" : "AI"} · ${p.prestige} pts`;
      const gemsRow = document.createElement("div");
      gemsRow.className = "pill-row";
      Object.entries(p.gems || {}).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.textContent = `${gem}: ${count}`;
        gemsRow.appendChild(pill);
      });
      const bonusesRow = document.createElement("div");
      bonusesRow.className = "pill-row";
      Object.entries(p.bonuses || {}).forEach(([gem, count]) => {
        const pill = document.createElement("div");
        pill.className = `pill gem-${gem}`;
        pill.textContent = `+${count} ${gem}`;
        bonusesRow.appendChild(pill);
      });
      const extra = document.createElement("div");
      extra.style.fontSize = "0.78rem";
      extra.style.marginTop = "3px";
      const nobleText = p.noble ? `Noble: ${p.noble}` : "No noble yet";
      extra.textContent = `${nobleText} · Reserved: ${p.reservedCount}`;

      card.appendChild(name);
      card.appendChild(meta);
      card.appendChild(gemsRow);
      card.appendChild(bonusesRow);
      card.appendChild(extra);
      players.appendChild(card);
    });
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

  document.getElementById("reserve-btn").addEventListener("click", async () => {
    const level = parseInt(document.getElementById("reserve-level").value, 10);
    const index = parseInt(document.getElementById("reserve-index").value, 10) || 0;
    try {
      const result = await postAction({ type: "reserve", level, index });
      msg.textContent = result.message || (result.success ? "Reserved." : "Reserve failed.");
      msg.className = "message " + (result.success ? "ok" : "error");
      const state = await fetchState();
      renderState(state);
    } catch (e) {
      msg.textContent = "Error reserving card.";
      msg.className = "message error";
    }
  });

  document.getElementById("purchase-btn").addEventListener("click", async () => {
    const level = parseInt(document.getElementById("purchase-level").value, 10);
    const index = parseInt(document.getElementById("purchase-index").value, 10) || 0;
    try {
      const result = await postAction({ type: "purchaseVisible", level, index });
      msg.textContent = result.message || (result.success ? "Purchased." : "Purchase failed.");
      msg.className = "message " + (result.success ? "ok" : "error");
      const state = await fetchState();
      renderState(state);
    } catch (e) {
      msg.textContent = "Error purchasing card.";
      msg.className = "message error";
    }
  });

  document.getElementById("purchase-reserved-btn").addEventListener("click", async () => {
    const index = parseInt(document.getElementById("purchase-reserved-index").value, 10) || 0;
    try {
      const result = await postAction({ type: "purchaseReserved", index });
      msg.textContent =
        result.message || (result.success ? "Reserved card purchased." : "Purchase reserved failed.");
      msg.className = "message " + (result.success ? "ok" : "error");
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
  const startGuidedBtn = document.getElementById("start-guided-btn");
  const numPlayersSelect = document.getElementById("start-num-players");
  const p1Type = document.getElementById("player1-type");
  const p2Type = document.getElementById("player2-type");
  const p3Type = document.getElementById("player3-type");
  const p4Type = document.getElementById("player4-type");
  const p1Row = p1Type.closest(".player-type-row");
  const p2Row = p2Type.closest(".player-type-row");
  const p3Row = p3Type.closest(".player-type-row");
  const p4Row = p4Type.closest(".player-type-row");
  const tutorialPanel = document.getElementById("tutorial-panel");
  const openTutorialBtn = document.getElementById("open-tutorial-btn");
  const closeTutorialBtn = document.getElementById("close-tutorial-btn");
  const guidedOverlay = document.getElementById("guided-overlay");
  const guidedStepLabel = document.getElementById("guided-step-label");
  const guidedStepText = document.getElementById("guided-step-text");
  const guidedPrevBtn = document.getElementById("guided-prev-btn");
  const guidedNextBtn = document.getElementById("guided-next-btn");
  const guidedExitBtn = document.getElementById("guided-exit-btn");

  let guidedIndex = 0;

  function updatePlayerRows() {
    const n = parseInt(numPlayersSelect.value, 10);
    p1Row.classList.toggle("hidden", n < 1);
    p2Row.classList.toggle("hidden", n < 2);
    p3Row.classList.toggle("hidden", n < 3);
    p4Row.classList.toggle("hidden", n < 4);
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
      // Status bar (current player)
      const status = document.querySelector(".status");
      if (status) status.classList.add("guided-highlight");
    } else if (guidedIndex === 2 || guidedIndex === 3) {
      // Board gems + take gems
      const gemsPanel = document.querySelector(".board .panel");
      if (gemsPanel) gemsPanel.classList.add("guided-highlight");
      const takeGemsBlock = document.getElementById("take-gems-options")?.parentElement;
      if (takeGemsBlock) takeGemsBlock.classList.add("guided-highlight");
    } else if (guidedIndex === 4 || guidedIndex === 5) {
      // Level 1 cards area
      const level1 = document.getElementById("level-1");
      if (level1) level1.classList.add("guided-highlight");
    } else if (guidedIndex === 6) {
      // Players panel
      const playersPanel = document.querySelector(".players.panel");
      if (playersPanel) playersPanel.classList.add("guided-highlight");
    } else if (guidedIndex === 7) {
      // Nobles panel
      const noblesPanel = document.querySelector(".board .panel:nth-child(2)");
      if (noblesPanel) noblesPanel.classList.add("guided-highlight");
    }
  }

  function openGuided() {
    guidedIndex = 0;
    tutorialFlags.active = true;
    tutorialFlags.tookGemsOnce = false;
    tutorialFlags.boughtLevel1Once = false;
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
      renderState(state);
    } catch (e) {
      alert("Failed to start game. Is the server running?");
    }
  });

  startGuidedBtn.addEventListener("click", async () => {
    try {
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
      renderState(state);
      openGuided();
    } catch (e) {
      alert("Failed to start tutorial. Is the server running?");
    }
  });

  openTutorialBtn.addEventListener("click", () => {
    tutorialPanel.classList.remove("hidden");
  });

  closeTutorialBtn.addEventListener("click", () => {
    tutorialPanel.classList.add("hidden");
  });

  numPlayersSelect.addEventListener("change", updatePlayerRows);

  // Initial state
  updatePlayerRows();

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
    if (guidedIndex === 5 && !tutorialFlags.boughtLevel1Once) {
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
      // "Finish" behaves like End Tutorial: exit to start screen
      closeGuided();
      startScreen.classList.remove("hidden");
      gameUi.classList.add("hidden");
      if (videoPanel) videoPanel.classList.remove("hidden");
    }
  });

  guidedExitBtn.addEventListener("click", () => {
    // Close overlay and return to start screen so the player
    // clearly exits the tutorial context.
    closeGuided();
    tutorialFlags.active = false;
    startScreen.classList.remove("hidden");
    gameUi.classList.add("hidden");
    if (videoPanel) videoPanel.classList.remove("hidden");
  });
}

window.addEventListener("DOMContentLoaded", init);

