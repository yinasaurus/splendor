// Set this to your deployed backend URL for production frontend hosting (e.g. Vercel).
// Leave as empty string for local same-origin use (http://localhost:8080).
window.__SPLENDOR_API_BASE__ = "https://splendor-api-hlrf.onrender.com";

// Optional: per-level face backgrounds for development cards (URLs or site-relative paths).
// Example: window.__SPLENDOR_DEV_CARD_BG__ = { 1: "media/dev-l1.jpg", 2: "media/dev-l2.jpg", 3: "media/dev-l3.jpg" };
// window.__SPLENDOR_DEV_CARD_BG__ = {};

// Optional: URL prefix for chip/gem sprites (default loads web/media/chips.jpg and gems.png).
// window.__SPLENDOR_MEDIA_BASE__ = "";

// Optional: development card face art folder (local after running scripts/fetch-hexanome-dev-cards.ps1, or GitHub raw).
// window.__SPLENDOR_DEV_CARD_ART_BASE__ = "media/development-cards";
window.__SPLENDOR_DEV_CARD_ART_BASE__ =
  "https://raw.githubusercontent.com/hexanome-04/splendor/d1797acf5d43c6bc512b57ef3c1d990006a49a5c/client/public/images/development-cards";

