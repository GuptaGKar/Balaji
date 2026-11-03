// Regression guard for the "unable to draw area" defect (2026-11-03).
//
// mapbox-gl-draw emits `mapboxgl-ctrl*` classes while maplibre-gl.css only styles
// `maplibregl-ctrl*`. MapLibre's corner container is `pointer-events: none` and opts each of its
// own controls back in through `.maplibregl-ctrl { pointer-events: auto }` - a selector the
// plugin's group never matches. The toolbar therefore paints its icons but passes every click
// through to the canvas: measured with document.elementFromPoint(), the polygon button's own
// centre returned the canvas. These assertions pin the two things that made drawing work, since
// neither is visible in the component's logic and both can be lost in an import cleanup.

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const path = require("node:path");

const panelDir = path.resolve(__dirname, "../../web/src/app/(admin)/service-areas");
const panel = fs.readFileSync(path.join(panelDir, "DeliveryZonesPanel.tsx"), "utf8");
const shim = fs.readFileSync(path.join(panelDir, "maplibre-draw-controls.css"), "utf8");
// Assert on declarations only: the shim's own comment quotes the maplibre-gl.css rules it works
// around, so matching against the raw file would trip on prose.
const shimCode = shim.replace(/\/\*[\s\S]*?\*\//g, "");

test("the panel imports the drawing plugin stylesheet and the pointer-events shim", () => {
  assert.match(
    panel,
    /import "@mapbox\/mapbox-gl-draw\/dist\/mapbox-gl-draw\.css"/,
    "icons come from this stylesheet",
  );
  assert.match(
    panel,
    /import "\.\/maplibre-draw-controls\.css"/,
    "without the shim the toolbar is click-transparent",
  );
});

test("the shim restores pointer events for the plugin's own class prefix", () => {
  assert.match(
    shimCode,
    /\.mapboxgl-ctrl\s*\{[^}]*pointer-events:\s*auto/,
    "must re-enable hits on the plugin control",
  );
  assert.doesNotMatch(
    shimCode,
    /\.maplibregl-ctrl[a-z-]*\s*\{[^}]*pointer-events/,
    "must not restyle MapLibre's own controls",
  );
});

test("draw events are read from the map, not the control", () => {
  // Measured on the live page: the Draw instance exposes no on/off/fire (map.on is the only
  // channel that delivers draw.create), so listeners moved onto the control would silently stop
  // reporting a finished shape and the boundary would never reach save_delivery_zone.
  assert.match(panel, /anyMap\.on\("draw\.create"/, "draw.create must be registered on the map");
  assert.doesNotMatch(panel, /control\.on\(["']draw\.create/, "the control has no event API");
});

test("a finished shape is optional so a PIN-only rule can still be saved", () => {
  // The panel used to refuse a new row without a drawn shape while its own message offered one,
  // which blocked the PIN-plus-zone combination the pricing precedence depends on.
  assert.doesNotMatch(panel, /if \(!editingId && !geometry && !clearBoundary\)/);
  assert.match(panel, /PIN-only rule/);
});

test("the map overlays stay under the drawing toolbar", () => {
  // Measured on the live page: while the map style was still loading, document.elementFromPoint()
  // at the polygon button's centre returned the loading overlay, so the tools painted but could
  // not be clicked - the same click-transparent class of bug as the plugin's control prefix.
  // MapLibre places its control corners at z-index 2, so these overlays must declare z-index 1.
  const overlays = panel.match(/className="absolute inset[^"]*"/g) || [];
  const declared = panel.match(/style=\{\{ zIndex: 1 \}\}/g) || [];
  assert.ok(
    overlays.length >= 2,
    `expected the full-area overlays to exist, found ${overlays.length}`,
  );
  assert.equal(
    declared.length,
    overlays.length,
    `every map overlay must declare zIndex 1 (${declared.length} of ${overlays.length})`,
  );
});
