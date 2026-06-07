# Map polish v2 — backlog & next steps

> **Start here next session.** The personal finds map (Feature A) is merged to `main` (PR #4, merge-commit `39a5ca28`) and device-verified green on SM-S918B 2026-06-07. This doc captures the polish work to do *before the next AAB*. You are on branch **`feat/map-polish-v2`** (off the merged `main`).
>
> Background: spec `docs/superpowers/specs/2026-06-05-personal-finds-map-design.md`, plan `docs/superpowers/plans/2026-06-05-personal-finds-map.md`. Auto-memory `project_personal_finds_map.md`.

## ⚠️ Before ANY device-verify
The map's device-verify needs ADB-driving + screencaps. **Ask Albin to silence notifications / enable Do-Not-Disturb first** — during the last verify a private chat notification surfaced mid-screencap (handled: deleted + HOME, nothing sent). The phone (SM-S918B) is Albin's daily device. Also: a real `MAPTILER_API_KEY` must be in local `gradle.properties` (read into `se.birdy.app.BuildConfig`) or tiles render blank. Debug package is `se.birdy.android.debug`.

To get a pin on the map for verifying (no live bird needed): inject a test observation into the debug DB — `sqlite3` is ABSENT on the device, so: `adb shell am force-stop se.birdy.android.debug` → `adb exec-out run-as se.birdy.android.debug cat databases/birdy-observations.db > obs.db` (byte-safe redirect in bash, not PowerShell) → edit with host `py` (sqlite3) inserting a row with `latitude`/`longitude` (e.g. 59.3293/18.0686) + a real `species_id` (e.g. `Q25485`) → `adb push` to `/data/local/tmp` → `run-as ... cp` back → relaunch. Clean up after with `adb shell pm clear se.birdy.android.debug`.

---

## 1. Tile quality + speed — THE main fix (ready to implement, do first)

**Problem (observed on device):** the map is **blurry** and **slow to fill in**. Root cause: `MapScreenHost.android.kt` uses **256px standard-DPI tiles** (`https://api.maptiler.com/maps/outdoor-v2/256/{z}/{x}/{y}.png?key=`). On the ~500 dpi S23 Ultra, 256px tiles are upscaled → soft; and 256px means ~4× more tile requests to cover the screen → slow cold-cache fill.

**Fix (single file: `composeApp/src/androidMain/kotlin/se/birdy/app/ui/map/MapScreenHost.android.kt`):**

- **HiDPI tiles.** Switch to 512px / `@2x` MapTiler tiles and set the osmdroid `XYTileSource` tile size to **512**. MapTiler raster forms (verify the exact one that returns 200, not 403):
  - `@2x` form: `https://api.maptiler.com/maps/<style>/{z}/{x}/{y}@2x.png?key=<KEY>` (append `@2x` before `.png` in the `getTileURLString` override).
  - or `/512/` path form: `https://api.maptiler.com/maps/<style>/512/{z}/{x}/{y}.png?key=<KEY>`.
  - Update the `XYTileSource(name, min, max, **tileSize=512**, ".png"/"@2x.png", baseUrls, copyright)` constructor accordingly.
- **More download threads + bigger cache** via `Configuration.getInstance()` in the `remember{}` block (already sets `userAgentValue`/cache path there):
  - `tileDownloadThreads = 8` (default 2) and `tileDownloadMaxQueueSize` accordingly.
  - `tileFileSystemCacheMaxBytes` / `tileFileSystemCacheTrimBytes` bumped (e.g. 200 MB / 160 MB) so revisited areas stay cached.
- HiDPI also *reduces* request count (each 512 tile covers 4× the area), so quality and speed improve together.

**Verify:** rebuild `:androidApp:installDebug`, inject a test pin (see above), open Karta → tiles should be crisp on the device + fill faster. Compare against the blurry 256px baseline. Re-run `:composeApp:ktlintAndroidMainSourceSetCheck` + `:androidApp:assembleDebug`.

**Watch:** MapTiler free-tier tile-request quota — `@2x` tiles may count differently; confirm the chosen URL returns 200 with the real key (no 403).

---

## 2–4. Broader polish (brainstorm before building — not yet designed)

These were deferred / YAGNI in the v1 spec. Use `superpowers:brainstorming` to scope before implementing.

2. **Field-Journal map styling.** The generic `outdoor-v2` style clashes with the paper/Field-Journal aesthetic. Options: a MapTiler **custom vintage/paper style** (build one in MapTiler Cloud, reference its style id) or an osmdroid colour filter/overlay to sepia-tint standard tiles. Decide which reads best.
3. **Pin design + clustering.** Currently plain copper markers. Consider species-thumbnail pins (decode `photoPath` into the marker icon) + **clustering** for dense areas (osmbonuspack `RadiusMarkerClusterer` — note: adds a **JitPack** dependency, weigh against the app's minimal-deps/privacy ethos). Also an info-window on tap before navigating.
4. **Location-source extensions.** (a) **take-photo location** — in-app camera capture currently gets no location (`live=false`, intentional v1 default); decide whether to attach location for take-photo (it IS here-and-now). (b) **gallery EXIF** location for uploaded photos. (c) **manual pin placement/adjust**. (d) **offline region download** ("excursion mode"). Each is its own brainstorm.

---

## Release reminders (tracked in auto-memory `project_post_vc122_unreleased_changes`)
The merged map feature must, at the next AAB:
- versionCode/versionName bump.
- **Play Console Data Safety form** updated (location = on-device/not-collected; MapTiler tile-fetch disclosure). Repo docs already updated; Console is the manual step.
- New permissions declared: `ACCESS_FINE/COARSE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE`.
- A valid `MAPTILER_API_KEY` available to the release build (CI secret / local).
- "What's new" + store-listing mention the map (premium) + free opt-in on-device location.
