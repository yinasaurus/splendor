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

window.__SPLENDOR_DEV_CARD_ART_FILENAME__ = function (globalIdx) {

  const i = Math.floor(Number(globalIdx)) || 1;

  const n = ((i - 1) % 12) + 1;

  return n <= 6 ? `splendor-${n}.jpg` : `splendor-${n}.png`;

};



// Full official-style set (90 files): run scripts/fetch-hexanome-dev-cards.ps1, then set:

// window.__SPLENDOR_DEV_CARD_ART_BASE__ = "media/development-cards";

// and delete or comment out __SPLENDOR_DEV_CARD_ART_FILENAME__ above.


