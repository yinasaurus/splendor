# Splendor (CS102)

Java Splendor engine with **console UI** and optional **web UI** (same rules on the server).

## How to run

**Needs:** JDK 8+.

| Step | Windows | Linux / Mac / WSL |
|------|---------|-------------------|
| Compile | `compile.bat` | `chmod +x compile.sh run.sh run_web.sh` then `./compile.sh` |
| Console game | `run.bat` | `./run.sh` |
| Web server | `run_web.bat` | `./run_web.sh` → open [http://localhost:8080](http://localhost:8080) |

Recompile after changing any `.java` file. If port `8080` is in use, free it or adjust the server port in code.

## Where things live

| Area | Location |
|------|----------|
| Java sources | `src/splendor/` (packages: `model`, `rules`, `controller`, `ai`, `data`, `config`, `ui`, `web`, …) |
| Compiled classes | `classes/` (produced by compile scripts; keep empty in submission zips per course spec) |
| Game data | `data/*.csv`, `data/web_lobbies.properties` |
| Settings | `config.properties` |
| Static assets | `media/` (images; also served at `/media/...` when using the web server) |
| Web client | `web/` (`index.html`, `app.js`, `styles.css`, `config.js`) |

Optional JARs go in `lib/` (classpath is wired in the compile/run scripts).

## AI

Uses the **Strategy** pattern: `AIStrategy` / `AIPlayer`, with `EasyAIStrategy`, `MediumAIStrategy`, and `HardAIStrategy` in `src/splendor/ai/`. The server advances AI turns the same way after human moves in both console and web modes.

- **Easy** — Random affordable purchase when possible; otherwise a simple gem take or pass.  
- **Medium** — Buys the best-scored affordable card, else may reserve or take gems toward visible cards.  
- **Hard** — Tries to close out wins, biases buys toward nobles and high prestige, then reserve/gem heuristics.

Shared helpers (e.g. card scoring and afford checks) live on `AIStrategy`.

## Web / config notes

- Local play: in `web/config.js`, keep `window.__SPLENDOR_API_BASE__ = ""` so the browser talks to the same host as `run_web`.
- To point a hosted static `web/` build at a remote Java backend, set `__SPLENDOR_API_BASE__` to that server’s URL.

## More detail

See `PROJECT_SUMMARY.md` for architecture and `CS102_SPLENDOR_PROJECT_SUBMISSION_GUIDE.txt` for submission and presentation notes.
