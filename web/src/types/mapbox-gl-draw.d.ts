// @mapbox/mapbox-gl-draw ships no TypeScript definitions, and the MapLibre types do not
// know about its custom "draw.create" / "draw.update" / "draw.delete" map events.
// Declaring the module keeps tsc happy; the map instance is cast at the call sites.
declare module "@mapbox/mapbox-gl-draw";
