# Map polish v2 — backlog & next steps

> **Start here next session.** The personal finds map (Feature A) is merged to `main` (PR #4, merge-commit `39a5ca28`) and device-verified green on SM-S918B 2026-06-07. This doc captures the polish work to do *before the next AAB*. You are on branch **`feat/map-polish-v2`** (off the merged `main`).
>
> Background: spec `docs/superpowers/specs/2026-06-05-personal-finds-map-design.md`, plan `docs/superpowers/plans/2026-06-05-personal-finds-map.md`. Auto-memory `project_personal_finds_map.md`, `project_map_polish_v2.md`.

## STATUS 2026-06-08 — items 1 + 2 DONE (device-verified); item 4 is NEXT

Branch `feat/map-polish-v2` is pushed (HEAD `efb89282`), **NOT yet merged**. All CI gates green (ktlintCheck, detekt, assembleDebug, composeApp tests). Device-verified green on SM-S918B 2026-06-08.

- **Item 1 — HiDPI tiles + speed: ✅ DONE** (commit `ccec35c2`). 256px → 512px `@2x` (the `/512/` path form **404s**; `@2x` is the working HiDPI form). `tileDownloadThreads 2→8`. Cache LEFT at osmdroid's **600/500 MB defaults** — the "200/160 MB" note in item 1 below would have SHRUNK it, ignore that line. (Base style later changed to toner-v2 by item 2.)
- **Item 2 — Field Journal styling + wax-seal pins: ✅ DONE + device-verified.** Spec `docs/superpowers/specs/2026-06-07-map-field-journal-styling-design.md`, plan `docs/superpowers/plans/2026-06-08-map-field-journal-styling.md`. Commits `4a6887cf`/`c06b94a1`/`b3df430a`/`ac3c49b4` (+ `efb89282` = pre-existing MainActivity detekt fix). **Look (locked via browser brainstorm):** toner-v2 grayscale-ink tiles + a runtime duotone `ColorMatrix` (white→paper `#EFE7D6`, black→sepia ink `#2E2417`) — NOT a custom MapTiler style. **Pins:** cream disc + copper ring + navy Birdy bird (`hero_bird.png`, PorterDuff SRC_IN) + downward point, anchored at the tip. No on-device tuning needed.
- **Item 3 — clustering / thumbnail pins: still future** (osmbonuspack = JitPack dep — weigh). Not started; out of scope so far.
- **Item 4 — location for non-live captures: ✅ DONE + device-verified (2026-06-08).** Commit `459e6038`. Gallery + in-app take-photo finds now attach the **current device location** (gated by the opt-in toggle), like live scans. **Key device-verify discovery:** the Android photo picker **strips GPS EXIF** from returned images unless the app holds `ACCESS_MEDIA_LOCATION` (a dangerous permission + data-safety disclosure). So the originally-designed "read the photo's EXIF GPS" path (built first, returned null on device) was reverted; per product decision (Albin) uploads use current-location instead — **no new permission**. Net code change: `shouldAttachLocation` returns true for every capture + removal of the now-dead `live` field. Device-verified on SM-S918B: a gallery save now records the current coordinates (lat/lng present, was NULL). Spec `docs/superpowers/specs/2026-06-08-map-location-non-live-captures-design.md` (see REVISED banner), plan `docs/superpowers/plans/2026-06-08-map-location-non-live-captures.md` (superseded).

### RECOMMENDED NEXT-SESSION PLAN (agreed with Albin 2026-06-08)
1. ~~**Brainstorm + build item 4**~~ ✅ DONE (commit `459e6038`, device-verified) — shipped with current-location for uploads (EXIF blocked by photo-picker redaction; see item 4 status above).
2. **Merge** all of map-polish-v2 (items 1 + 2 + 4) to `main` via PR (like personal-finds-map PR #4). ← **NEXT**
3. **Version bump** (versionCode + versionName) + fold ALL unreleased post-vC122 changes into "What's new" + update store-listing (CLAUDE.md follow-ups #8/#9), then build the AAB. Billing-verify (`PREMIUM_OPEN_FOR_LAUNCH=false`) is a **production-GA/monetization** gate, NOT closed testing — separate track.

## ⚠️ Before ANY device-verify
The map's device-verify needs ADB-driving + screencaps. **Ask Albin to silence notifications / enable Do-Not-Disturb first** — during the last verify a private chat notification surfaced mid-screencap (handled: deleted + HOME, nothing sent). The phone (SM-S918B) is Albin's daily device. Also: a real `MAPTILER_API_KEY` must be in local `gradle.properties` (read into `se.birdy.app.BuildConfig`) or tiles render blank. Debug package is `se.birdy.android.debug`.

To get a pin on the map for verifying (no live bird needed): inject a test observation into the debug DB — `sqlite3` is ABSENT on the device. **ADB-on-Windows gotchas learned 2026-06-08 (see `reference_adb_windows_msys_pathconv`):**
1. **`export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'`** at the top of the bash block — otherwise Git-Bash/MSYS mangles device paths like `/data/local/tmp/...` into `C:/Program Files/Git/data/...` and `adb push` fails with `secure_mkdirs() failed`.
2. Pull: `adb exec-out run-as se.birdy.android.debug cat databases/birdy-observations.db > obs.db` (byte-safe). Edit with host `py` (sqlite3): insert rows with `latitude`/`longitude` (e.g. 59.3293/18.0686) + real `species_id` + required NOT-NULL cols (`captured_at_ms`,`saved_at_ms`,`photo_path`,`confidence`,`stamp_number`,`source`).
3. Write back: `adb push obs.db /data/local/tmp/obs.db` then **`adb shell "run-as se.birdy.android.debug sh -c 'cat /data/local/tmp/obs.db > databases/birdy-observations.db && rm -f databases/...-wal databases/...-shm'"`** — the on-device `cat >` redirect is byte-safe (stays on device, no host CRLF). **Do NOT** use `run-as ... cp` (fails "not directory") and **do NOT** use `exec-out ... 'cat > db' < file` (stdin EOF never closes → hangs AND truncates the db).
4. Clean up: `adb shell pm clear se.birdy.android.debug`. Schema: `observation(id,species_id,captured_at_ms,saved_at_ms,photo_path,note,confidence,latitude,longitude,location_label,stamp_number,audio_path,source)`.

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

## 2–4. Broader polish

2. **Field-Journal map styling. ✅ DONE** (see STATUS above) — toner-v2 + runtime duotone ColorMatrix + wax-seal pins.
3. **Pin design + clustering. (future, not started)** Seal pins done in item 2; what remains is **clustering** for dense areas (osmbonuspack `RadiusMarkerClusterer` — adds a **JitPack** dependency, weigh against the app's minimal-deps/privacy ethos) + optional info-window on tap. Brainstorm before building.
4. **Location for non-live captures. NEXT — `superpowers:brainstorming` then build.** Real-world bug 2026-06-08: gallery finds don't geotag (see STATUS). Refined design from the diagnosis:
   - **(4a) In-app take-photo → CURRENT device location.** The capture is here-and-now, so `LocationProvider.current()` is correct. Today take-photo is `live=false` (PhotoAnalyzeScreen) so it's skipped — attach location for it (gated by the opt-in toggle).
   - **(4b) Gallery photo → the photo's EXIF GPS, NOT current location.** A gallery image may be from another place/time, so current location is wrong; read EXIF lat/lng (androidx `ExifInterface` on the picked URI's input stream). No EXIF GPS → no location (can't know). **This is the exact case Albin hit.**
   - Both gated by `UserPreferences.locationCaptureEnabled` (opt-in); live "Look" + "Listen" already geotag. **Plumbing sketch:** `ScanSource.Image` likely carries an optional pre-resolved `LatLng` (from EXIF) so `MatchResultViewModel`/`SaveObservationUseCase` attach it without calling the live provider; extend `shouldAttachLocation` / the save path accordingly. Key files: `PhotoAnalyzeScreen.kt`/`PhotoAnalyzeHost.android.kt` (where `live=false` is set + the picked URI is available for EXIF), `ScanSource.kt`, `MatchResultViewModel.kt` (`shouldAttachLocation`, lines ~28/232/288), `SaveObservationUseCase.kt`.
   - Privacy: all on-device; EXIF coords never leave the phone (same promise as live capture).
   - **Deferred sub-items (still future, separate brainstorms):** manual pin placement/adjust; offline region download ("excursion mode").

---

## Release reminders (tracked in auto-memory `project_post_vc122_unreleased_changes`)
The merged map feature must, at the next AAB:
- versionCode/versionName bump.
- **Play Console Data Safety form** updated (location = on-device/not-collected; MapTiler tile-fetch disclosure). Repo docs already updated; Console is the manual step.
- New permissions declared: `ACCESS_FINE/COARSE_LOCATION`, `INTERNET`, `ACCESS_NETWORK_STATE`.
- A valid `MAPTILER_API_KEY` available to the release build (CI secret / local).
- "What's new" + store-listing mention the map (premium) + free opt-in on-device location.
