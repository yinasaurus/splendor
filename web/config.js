// Set this to your deployed backend URL for production frontend hosting (e.g. Vercel).

// Leave as empty string for local same-origin use (http://localhost:8080).

window.__SPLENDOR_API_BASE__ = "https://splendor-api-hlrf.onrender.com";



// Optional: per-level face backgrounds for development cards (URLs or site-relative paths).

// Example: window.__SPLENDOR_DEV_CARD_BG__ = { 1: "media/dev-l1.jpg", 2: "media/dev-l2.jpg", 3: "media/dev-l3.jpg" };

// window.__SPLENDOR_DEV_CARD_BG__ = {};



// Optional: URL prefix for chip/gem sprites (default: same-origin web/media/chips.jpg + gems.png).

// window.__SPLENDOR_MEDIA_BASE__ = "";



// Optional: skip wiring chip/gem sprite URLs — window.__SPLENDOR_USE_CSS_GEMS__ = true;


// Use image sprites for gems/chips instead of flat CSS gem dots.
window.__SPLENDOR_USE_IMAGE_SPRITES__ = true;



// Development card face images: files in web/media/ (served as /media/...).

// Your repo media/splendor-1.jpg … splendor-6.jpg, splendor-7.png … splendor-12.png — copy or sync into web/media/.

window.__SPLENDOR_DEV_CARD_ART_BASE__ = "media";

// Optional: assign face art by bonus gem color (deterministic by card id; not random).
window.__SPLENDOR_DEV_CARD_ART_BY_BONUS__ = {
  RUBY: ["splendor-1.jpg", "splendor-7.png"],
  SAPPHIRE: ["splendor-3.jpg", "splendor-8.png"],
  DIAMOND: ["splendor-11.png", "splendor-6.jpg"],
  EMERALD: ["splendor-12.png", "splendor-4.jpg"],
  ONYX: ["splendor-2.jpg", "splendor-5.jpg"],
};

window.__SPLENDOR_DEV_CARD_ART_FILENAME__ = function (globalIdx) {

  const i = Math.floor(Number(globalIdx)) || 1;

  const n = ((i - 1) % 12) + 1;

  return n <= 6 ? `splendor-${n}.jpg` : `splendor-${n}.png`;

};



// Full official-style set (90 files): run scripts/fetch-hexanome-dev-cards.ps1, then set:

// window.__SPLENDOR_DEV_CARD_ART_BASE__ = "media/development-cards";

// and delete or comment out __SPLENDOR_DEV_CARD_ART_FILENAME__ above.

// Noble portraits (optional): set a base folder + either id map/name map, or a filename function.
// Example files: media/noble1.jpg, media/noble2.jpg, ...
window.__SPLENDOR_NOBLE_ART_BASE__ = "media";
window.__SPLENDOR_NOBLE_ART_FILENAME__ = function (noble) {
  const id = Math.floor(Number(noble && noble.id));
  if (!Number.isFinite(id) || id <= 0) {
    return null;
  }
  return `noble${id}.jpg`;
};
// Optional explicit mapping (overrides filename function when present):
// window.__SPLENDOR_NOBLE_ART_BY_ID__ = { 1: "media/noble1.jpg", 2: "media/noble2.jpg" };
// window.__SPLENDOR_NOBLE_ART_BY_NAME__ = { "Solimano": "media/noble-solimano.jpg" };


