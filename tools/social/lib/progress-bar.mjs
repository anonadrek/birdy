// The opt-in progress meter for the finished "See the song" video (Albin, 2026-10-10: "a small
// meter at the top of our clips where you can see how much of the clip is left"). Drawn by
// cover/title-card.mjs with --progress, over the whole finished video including the title card
// and the loop it adds, so the bar means "time left in this post", not "time left in the bird's
// song" or "time left in the title card".
//
// Two ffmpeg techniques, each verified by hand before writing this (ffmpeg 8.1.1, 2026-10-10):
//   - The static track is a single drawbox with a fixed width: thickness "fill" draws it fully
//     solid without needing a thickness number big enough to meet in the middle by itself.
//   - drawbox's own x/y/w/h expressions CANNOT grow over time: the "t" constant they expose is
//     drawbox's own thickness option, not playback time (confirmed by hand: w='10*t' with
//     thickness=7 renders a CONSTANT width of 70 on every frame, never growing), and "n" (frame
//     number) is not defined there at all. So the fill cannot be a second, time-varying drawbox.
//   - The fill is instead a plain colour source, resized with the scale filter's w expression
//     (eval=frame makes scale re-evaluate w every frame, and w can see t there), then overlaid
//     at a fixed x: only the resized clip's right edge moves, which reads as a bar filling in
//     from the left. Resizing has to happen before the overlay, not as the overlay's own x/y,
//     because moving a fixed-size rectangle cannot by itself make it look like it is growing.
// A quoted expression with a raw comma (e.g. scale=w='min(a,b)') does not need the comma
// backslash-escaped: confirmed by hand with the exact argv-array spawn tools/social uses (no
// shell involved), both with and without the backslash, and it behaves identically either way.

// Geometry, in the 1080 x 1920 frame every "See the song" video is rendered at.
export const BAR_MARGIN_X = 48; // left and right margin (px): about 4.4% of the width, within the 40 to 60 px the brief asks for
export const BAR_TOP = 40; // distance from the top (px), about 2.1% of 1920: clears a phone's status bar and the question text, which starts at y 60
export const BAR_HEIGHT = 7; // thin (px): within the 6 to 8 px the brief asks for

// Colours, from website/src/styles/tokens.css (the app's own palette, not the website's espresso one).
export const BAR_TRACK_COLOR = '0xFFF8EE'; // cream: the mostly empty groove
export const BAR_TRACK_OPACITY = 0.3; // low, so the track reads as a calm line, not a second bar
export const BAR_FILL_COLOR = '0x9A4526'; // rust: dark enough to read on the light flock cover, light enough to read on the dark video

/** "0xRRGGBB" -> [r, g, b], so a QA check can compare a sampled pixel against BAR_FILL_COLOR without a second, separately maintained constant. */
function hexToRgb(hex) {
  const n = parseInt(hex.replace(/^0x/, ''), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}
export const BAR_FILL_RGB = hexToRgb(BAR_FILL_COLOR); // for QA pixel checks; derived from BAR_FILL_COLOR so the two can never drift apart

/** The bar's own box for a video `videoWidth` px wide (the fill draws inside the same box). */
export function barGeometry(videoWidth) {
  return { x: BAR_MARGIN_X, y: BAR_TOP, w: videoWidth - 2 * BAR_MARGIN_X, h: BAR_HEIGHT };
}

/**
 * The fill's width at time `t` (seconds): 0 at t <= 0, exactly `trackWidth` at t >= lastFrameT,
 * linear in between. `lastFrameT` is the presentation time of the video's own last frame (not
 * its duration), so the fill reaches exactly full width on the last frame and the loop restarts
 * from empty on the next one (the first frame, t = 0).
 */
export function fillWidthAt(t, trackWidth, lastFrameT) {
  if (lastFrameT <= 0) return trackWidth;
  return trackWidth * Math.min(Math.max(t, 0) / lastFrameT, 1);
}

/** The ffmpeg expression for fillWidthAt, for the scale filter's `w` option (with eval=frame). */
export function fillWidthExpr(trackWidth, lastFrameT) {
  return `${trackWidth}*min(max(t,0)/${lastFrameT.toFixed(3)},1)`;
}

/**
 * The filter_complex fragment that draws the bar over `inLabel`, producing `outLabel`. Meant to
 * be appended last, after everything else (including the title card and its loop): the track and
 * the fill are both drawn on top of whatever `inLabel` already is.
 *   videoWidth  the finished video's width in px (1080 for every "See the song" video so far)
 *   lastFrameT  the presentation time (seconds) of the video's own last frame
 *   loopSec     a duration safely longer than the video, for the two generated colour sources
 *   fps         the colour sources' frame rate (should match the video's own, so frames line up)
 */
export function progressBarFilter({ videoWidth, lastFrameT, loopSec, fps, inLabel, outLabel }) {
  const { x, y, w, h } = barGeometry(videoWidth);
  const widthExpr = fillWidthExpr(w, lastFrameT);
  return (
    `color=c=${BAR_FILL_COLOR}:s=${w}x${h}:d=${loopSec}:r=${fps}[barfillsrc];` +
    `[barfillsrc]scale=w='${widthExpr}':h=${h}:eval=frame[barfill];` +
    `${inLabel}drawbox=x=${x}:y=${y}:w=${w}:h=${h}:color=${BAR_TRACK_COLOR}@${BAR_TRACK_OPACITY}:t=fill[bartrack];` +
    // eof_action=pass (same as the title card's own two overlays): the fill source is given a
    // generous, fixed duration (loopSec) so it never runs out before the real video does, but
    // without this it would be overlay's own default (repeat), which pads the WHOLE output out
    // to loopSec once the real video (bartrack, the main input) ends first (verified by hand:
    // the default stretched a 28 s video to a flat 31 s = LOOP_SEC).
    `[bartrack][barfill]overlay=x=${x}:y=${y}:eval=frame:eof_action=pass${outLabel}`
  );
}
