// lib/progress-bar.mjs's pure geometry/expression builders, and cover/title-card.mjs's
// buildFilter(): without --progress it must still produce exactly the filter this script has
// always built (so re-running on an already-scheduled set is a no-op); with --progress, the bar
// fragment is appended last, after the existing title-card/loop chain.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import {
  barGeometry,
  fillWidthAt,
  fillWidthExpr,
  progressBarFilter,
  BAR_MARGIN_X,
  BAR_TOP,
  BAR_HEIGHT,
  BAR_FILL_COLOR,
  BAR_FILL_RGB,
} from '../lib/progress-bar.mjs';
import { buildFilter } from '../cover/title-card.mjs';

test('barGeometry: margins and height match the brief (40 to 60 px sides, 2 to 3% top, 6 to 8 px thin)', () => {
  const g = barGeometry(1080);
  assert.equal(g.x, BAR_MARGIN_X);
  assert.equal(g.y, BAR_TOP);
  assert.equal(g.w, 1080 - 2 * BAR_MARGIN_X);
  assert.equal(g.h, BAR_HEIGHT);
  assert.ok(BAR_MARGIN_X >= 40 && BAR_MARGIN_X <= 60, `side margin ${BAR_MARGIN_X}px`);
  assert.ok(BAR_TOP / 1920 >= 0.02 && BAR_TOP / 1920 <= 0.03, `top margin ${((BAR_TOP / 1920) * 100).toFixed(2)}%`);
  assert.ok(BAR_HEIGHT >= 6 && BAR_HEIGHT <= 8, `bar height ${BAR_HEIGHT}px`);
  // Stays clear of the question text, which starts at y 60 in stage/see-the-song.html (#q).
  assert.ok(BAR_TOP + BAR_HEIGHT < 60, `bar bottom edge ${BAR_TOP + BAR_HEIGHT} must clear the question text at y 60`);
});

test('barGeometry: scales with the video width (all "See the song" videos are 1080 wide today, but the margins are not hardcoded to that)', () => {
  const g = barGeometry(2160); // e.g. a hypothetical 2x render
  assert.equal(g.w, 2160 - 2 * BAR_MARGIN_X);
});

test('fillWidthAt: 0 at the start, exactly full at the last frame, linear in between, clamped beyond', () => {
  const w = 984;
  const lastFrameT = 15;
  assert.equal(fillWidthAt(0, w, lastFrameT), 0);
  assert.equal(fillWidthAt(lastFrameT, w, lastFrameT), w, 'exactly full at the last frame, so the loop restarts from empty');
  assert.equal(fillWidthAt(lastFrameT / 2, w, lastFrameT), w / 2);
  assert.equal(fillWidthAt(lastFrameT * 2, w, lastFrameT), w, 'clamped at full width past the last frame');
  assert.equal(fillWidthAt(-1, w, lastFrameT), 0, 'clamped at empty before t = 0 (defensive; t is never negative in practice)');
});

test('fillWidthExpr: the exact ffmpeg expression for the scale filter\'s w, matching fillWidthAt\'s formula', () => {
  // w * min(max(t, 0) / lastFrameT, 1): literally the same formula fillWidthAt computes in JS,
  // just as the text ffmpeg's own expression evaluator reads (confirmed by hand against real
  // ffmpeg output, see lib/progress-bar.mjs's header comment and the commit message).
  assert.equal(fillWidthExpr(984, 15), '984*min(max(t,0)/15.000,1)');
  assert.equal(fillWidthExpr(984, 14.9666), '984*min(max(t,0)/14.967,1)', 'lastFrameT rounded to milliseconds, plenty for a visual bar');
});

test('progressBarFilter: draws the track then the fill, ending at outLabel, geometry baked into the string', () => {
  const filter = progressBarFilter({ videoWidth: 1080, lastFrameT: 15, loopSec: 31, fps: 30, inLabel: '[stage2]', outLabel: '[outv]' });
  assert.match(filter, /^color=c=0x9A4526:s=984x7:d=31:r=30\[barfillsrc\];/, 'the fill colour source, sized to the track');
  assert.match(filter, /scale=w='984\*min\(max\(t,0\)\/15\.000,1\)':h=7:eval=frame\[barfill\]/, 'the fill resized with a per-frame width expression');
  assert.match(filter, /\[stage2\]drawbox=x=48:y=40:w=984:h=7:color=0xFFF8EE@0\.3:t=fill\[bartrack\]/, 'the static track, drawn over inLabel');
  assert.match(filter, /\[bartrack\]\[barfill\]overlay=x=48:y=40:eval=frame:eof_action=pass\[outv\]$/, 'the fill overlaid at a fixed x (eof_action=pass so loopSec never stretches the real video), ending at outLabel');
});

test('buildFilter: without --progress, the filter is byte-identical to the one this script has always built', () => {
  const result = buildFilter({ width: 1080, height: 1920, closeStartStr: '12.345', progress: false });
  const expected =
    "[1:v]scale=1080:1920:out_color_matrix=bt709:out_range=tv,format=yuva420p,split=2[cov_o][cov_c];" +
    "[cov_o]fade=t=out:st=0.9:d=0.4:alpha=1,setpts=PTS-STARTPTS[ovl_o];" +
    "[cov_c]fade=t=in:st=12.345:d=0.5:alpha=1,setpts=PTS-STARTPTS[ovl_c];" +
    "[0:v][ovl_o]overlay=x=0:y=0:eof_action=pass[stage1];" +
    "[stage1][ovl_c]overlay=x=0:y=0:enable='gte(t,12.345)':eof_action=pass,format=yuv420p[outv]";
  assert.equal(result, expected);
});

test('buildFilter: progress is also off when the option is simply omitted', () => {
  const withOmitted = buildFilter({ width: 1080, height: 1920, closeStartStr: '12.345' });
  const withFalse = buildFilter({ width: 1080, height: 1920, closeStartStr: '12.345', progress: false });
  assert.equal(withOmitted, withFalse);
});

test('buildFilter: with --progress, the bar fragment is appended last, after the existing chain', () => {
  const args = { width: 1080, height: 1920, closeStartStr: '12.345', progress: true, lastFrameT: 14.967, fps: 30, loopSec: 31 };
  const result = buildFilter(args);
  assert.ok(!result.includes('[outv];'), 'the base chain must not end at [outv] when the bar still has to be drawn on top');
  assert.match(result, /format=yuv420p\[stage2\];/, 'the base chain now hands off to [stage2] instead of [outv]');
  const expectedSuffix = progressBarFilter({ videoWidth: 1080, lastFrameT: 14.967, loopSec: 31, fps: 30, inLabel: '[stage2]', outLabel: '[outv]' });
  assert.ok(result.endsWith(expectedSuffix), 'the bar fragment is appended verbatim');
  assert.ok(result.endsWith('[outv]'), 'the final label is still [outv], for -map "[outv]"');
});

test('BAR_FILL_RGB is derived from BAR_FILL_COLOR, not a separately maintained constant', () => {
  assert.equal(BAR_FILL_COLOR, '0x9A4526');
  assert.deepEqual(BAR_FILL_RGB, [0x9a, 0x45, 0x26]);
});
