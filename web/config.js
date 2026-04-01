// Set this to your deployed backend URL for production frontend hosting (e.g. Vercel).
// Leave as empty string for local same-origin use (http://localhost:8080).
window.__SPLENDOR_API_BASE__ = "https://splendor-api-hlrf.onrender.com";

// Optional: per-level face backgrounds for development cards (URLs or site-relative paths).
// Example: window.__SPLENDOR_DEV_CARD_BG__ = { 1: "media/dev-l1.jpg", 2: "media/dev-l2.jpg", 3: "media/dev-l3.jpg" };
// window.__SPLENDOR_DEV_CARD_BG__ = {};

// Optional: URL prefix for chip/gem sprites (default: same-origin web/media/chips.jpg + gems.png).
// window.__SPLENDOR_MEDIA_BASE__ = "";

// Optional: use CSS gem spheres instead of media sprites — window.__SPLENDOR_USE_CSS_GEMS__ = true;

// Optional: folder of jpg/png faces (e.g. "media/development-cards"). Leave unset to use built-in gradient card art only.
// window.__SPLENDOR_DEV_CARD_ART_BASE__ = "media/development-cards";
window.__SPLENDOR_DEV_CARD_ART_BASE__ = "";

