"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import maplibregl from "maplibre-gl";
import "maplibre-gl/dist/maplibre-gl.css";
// The drawing plugin's toolbar icons are defined in this stylesheet. Without it the polygon
// and delete buttons render as empty boxes, so the tools are effectively undiscoverable.
import "@mapbox/mapbox-gl-draw/dist/mapbox-gl-draw.css";
// maplibre-gl.css cannot make the plugin's toolbar clickable: it styles `maplibregl-` classes
// while the plugin emits `mapboxgl-` ones. See the file for the measured effect.
import "./maplibre-draw-controls.css";
import { createClient } from "@/lib/supabase";
import { formatRupees } from "@/lib/money";
import { Loader2, MapPinned, Radar, Save, Trash2 } from "lucide-react";
import { toast } from "sonner";

const STYLE_URL = "https://tiles.openfreemap.org/styles/liberty";
const COVERAGE_SOURCE = "balaji-coverage";
const COVERAGE_FILL = "balaji-coverage-fill";
const COVERAGE_OUTLINE = "balaji-coverage-outline";
// Explicit pixel height for the map container: MapLibre needs the element it attaches
// to to have a measurable size of its own.
const MAP_HEIGHT_PX = 420;

type ZoneRow = {
  id: string;
  kind: string;
  value: string;
  label: string;
  priority: number;
  active: boolean;
  delivery_fee_paise: number | null;
  delivery_charge_threshold_paise: number | null;
  free_delivery_threshold_paise: number | null;
  addresses_inside: number;
  overlapping_areas: number;
  /** Added by 20261103000002. Optional so the table stays correct against the older function. */
  has_boundary?: boolean;
  geojson: GeoJSONGeometry | null;
};

type GeoJSONGeometry = { type: string; coordinates: number[][][] };

type SaveResult = {
  id?: string;
  has_boundary?: boolean;
  overlapping_areas?: number;
  error?: string;
  message?: string;
};

/** One row from search_area_suggestions - the same shape the customer app decodes. */
type AreaSuggestion = {
  type: string;
  id: string;
  label: string;
  kind?: string | null;
  value?: string | null;
  has_boundary?: boolean | null;
  lat?: number | null;
  lng?: number | null;
};

type SpotAnswer = {
  deliverable?: boolean;
  fee_paise?: number;
  zone_label?: string | null;
  rule_note?: string;
  amount_to_free_delivery?: number;
  message?: string;
  error?: string;
};

/** Rupee text to integer paise. Blank stays blank so the store-wide rule keeps applying. */
function rupeesToPaise(input: string): number | null {
  const trimmed = input.trim();
  if (trimmed === "") return null;
  const value = Number(trimmed.replace(/,/g, ""));
  if (!Number.isFinite(value) || value < 0) return NaN;
  return Math.round(value * 100);
}

export default function DeliveryZonesPanel() {
  const container = useRef<HTMLDivElement | null>(null);
  const map = useRef<maplibregl.Map | null>(null);
  // The draw plugin is a UMD bundle; under webpack's ESM interop a static import can
  // resolve to a wrapper and `new Draw()` then throws, so it is loaded at runtime.
  const draw = useRef<any>(null);
  const coverageSource = useRef<maplibregl.GeoJSONSource | null>(null);

  const [ready, setReady] = useState(false);
  const [drawReady, setDrawReady] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  // Set when the browser cannot create a WebGL context at all. The rest of the panel -
  // the form, the spot test and the coverage list - must still be usable without it.
  const [mapUnavailable, setMapUnavailable] = useState<string | null>(null);
  const [spot, setSpot] = useState<{ lat: number; lng: number } | null>(null);
  const [geometry, setGeometry] = useState<GeoJSONGeometry | null>(null);
  const [zones, setZones] = useState<ZoneRow[] | null>(null);
  const [loadingZones, setLoadingZones] = useState(true);
  const [zonesError, setZonesError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [testing, setTesting] = useState(false);
  const [answer, setAnswer] = useState<SpotAnswer | null>(null);
  const [editingId, setEditingId] = useState<string | null>(null);

  const [kind, setKind] = useState<"pincode" | "neighbourhood">("neighbourhood");
  const [value, setValue] = useState("");
  const [name, setName] = useState("");
  const [priority, setPriority] = useState("100");
  const [feeRupees, setFeeRupees] = useState("");
  const [chargeRupees, setChargeRupees] = useState("");
  const [freeRupees, setFreeRupees] = useState("");
  const [active, setActive] = useState(true);
  const [suggestions, setSuggestions] = useState<AreaSuggestion[]>([]);
  const [clearBoundary, setClearBoundary] = useState(false);

  const refreshZones = useCallback(async () => {
    const supabase = createClient();
    const { data, error } = await supabase.rpc("get_delivery_zone_coverage");
    if (error) {
      // A failure to load must not look like an empty database: it used to fall through to the
      // "no areas drawn yet" message, which told the manager the opposite of the truth.
      setZonesError(`Could not load delivery areas: ${error.message}`);
      setLoadingZones(false);
      return;
    }
    // The function returns an array for a manager and an object ({ error: 'FORBIDDEN' }) for
    // anyone else. Treating either as an array made zones.map throw and blanked the whole page.
    if (!Array.isArray(data)) {
      setZonesError((data as any)?.message || "This list is only available to managers.");
      setZones([]);
      setLoadingZones(false);
      return;
    }
    setZonesError(null);
    setZones(data as ZoneRow[]);
    setLoadingZones(false);
  }, []);

  useEffect(() => {
    // `map.current` is the only guard needed: StrictMode tears the map down in the
    // cleanup below, which nulls it, so the second run legitimately builds a new one.
    // An extra "already attempted" flag here permanently blanks the map in dev.
    if (!container.current || map.current || mapUnavailable) return;

    let instance: maplibregl.Map;
    try {
      instance = new maplibregl.Map({
        container: container.current,
        style: STYLE_URL,
        center: [79.6, 22.9],
        zoom: 4.2,
        attributionControl: { compact: true },
      });
    } catch (error) {
      // MapLibre throws here when WebGL cannot be created. Left unguarded, the throw
      // escapes this effect, trips the route error boundary and blanks the whole page -
      // including the pre-existing service-area table rendered above this panel.
      console.error("Map could not start", error);
      setMapUnavailable(
        "This browser could not start WebGL, so the map and the drawing tool are unavailable. " +
          "The form and the list below still work.",
      );
      return;
    }
    map.current = instance;
    instance.addControl(new maplibregl.NavigationControl({ visualizePitch: false }), "top-right");
    // Clicking picks the spot that "Test this spot" asks the server about, so the check
    // is about a place the manager chose rather than whatever the camera happens to show.
    instance.on("click", (event) => setSpot({ lat: event.lngLat.lat, lng: event.lngLat.lng }));

    instance.on("load", () => {
      // addSource takes a source *specification*; GeoJSONSource is the runtime class, so
      // the spec is passed inline and the class is only used for the retrieved handle.
      instance.addSource(COVERAGE_SOURCE, {
        type: "geojson",
        data: { type: "FeatureCollection", features: [] },
      });
      coverageSource.current = instance.getSource(COVERAGE_SOURCE) as maplibregl.GeoJSONSource;
      // Plain style-spec objects rather than the layer classes: maplibre-gl 4's type
      // surface does not export LineLayer, and the object form is fully typed.
      instance.addLayer({
        id: COVERAGE_FILL,
        type: "fill",
        source: COVERAGE_SOURCE,
        paint: { "fill-color": "#218838", "fill-opacity": 0.18 },
      });
      instance.addLayer({
        id: COVERAGE_OUTLINE,
        type: "line",
        source: COVERAGE_SOURCE,
        paint: { "line-color": "#17662A", "line-width": 2 },
      });
      setReady(true);
    });

    instance.on("error", (event) => {
      // A tile fetch can fail offline; only a style failure is worth telling the manager about.
      if (String(event?.error?.status || "") === "error") setMapError("The map could not load.");
    });

    void (async () => {
      try {
        const mod: any = await import("@mapbox/mapbox-gl-draw");
        const DrawCtor = mod?.default?.default ?? mod?.default ?? mod;
        const control = new DrawCtor({
          displayControlsDefault: false,
          controls: { polygon: true, trash: true },
        });
        instance.addControl(control, "top-left");
        draw.current = control;
        setDrawReady(true);
        const anyMap = instance as any;
        const sync = () => setGeometry(pickGeometry(control));
        anyMap.on("draw.create", sync);
        anyMap.on("draw.update", sync);
        anyMap.on("draw.delete", () => {
          setGeometry(null);
          setClearBoundary(false);
        });
      } catch (error) {
        console.error("Failed to start the drawing tool", error);
        toast.error("The drawing tool did not load. Reload the page.");
      }
    })();

    return () => {
      instance.remove();
      map.current = null;
      draw.current = null;
      coverageSource.current = null;
    };
  }, []);

  // Paint the saved areas on the map whenever the list changes.
  useEffect(() => {
    if (!ready || !coverageSource.current || !zones) return;
    coverageSource.current.setData({
      type: "FeatureCollection",
      features: zones
        .filter((zone) => zone.geojson?.type === "Polygon")
        .map((zone) => ({
          type: "Feature",
          properties: { id: zone.id, label: zone.label },
          geometry: zone.geojson as any,
        })),
    } as any);
  }, [ready, zones]);

  const selectedZone = useMemo(
    () => zones?.find((zone) => zone.id === editingId) ?? null,
    [zones, editingId],
  );

  const startEdit = (zone: ZoneRow) => {
    setEditingId(zone.id);
    setKind(zone.kind as any);
    setValue(zone.value);
    setName(zone.label === zone.value ? "" : zone.label);
    setPriority(String(zone.priority));
    setFeeRupees(zone.delivery_fee_paise == null ? "" : String(zone.delivery_fee_paise / 100));
    setChargeRupees(
      zone.delivery_charge_threshold_paise == null
        ? ""
        : String(zone.delivery_charge_threshold_paise / 100),
    );
    setFreeRupees(
      zone.free_delivery_threshold_paise == null
        ? ""
        : String(zone.free_delivery_threshold_paise / 100),
    );
    setActive(zone.active);
    setClearBoundary(false);
    setAnswer(null);
    if (zone.geojson && map.current) {
      const bounds = new maplibregl.LngLatBounds();
      for (const [lng, lat] of zone.geojson.coordinates[0]) bounds.extend([lng, lat]);
      map.current.fitBounds(bounds, { padding: 40 });
    }
  };

  const resetForm = () => {
    setEditingId(null);
    setKind("neighbourhood");
    setValue("");
    setName("");
    setPriority("100");
    setFeeRupees("");
    setChargeRupees("");
    setFreeRupees("");
    setActive(true);
    setClearBoundary(false);
    setGeometry(null);
    setAnswer(null);
    draw.current?.deleteAll();
  };

  const save = async () => {
    const fee = rupeesToPaise(feeRupees);
    const charge = rupeesToPaise(chargeRupees);
    const free = rupeesToPaise(freeRupees);
    if ([fee, charge, free].some((amount) => Number.isNaN(amount))) {
      toast.error("Enter amounts as plain numbers, like 30 or 30.00");
      return;
    }
    if (!value.trim()) {
      toast.error("A PIN code or area name is required");
      return;
    }
    const priorityNumber = Number(priority);
    if (!Number.isInteger(priorityNumber) || priorityNumber < 1 || priorityNumber > 1000) {
      toast.error("Priority must be a whole number between 1 and 1000");
      return;
    }
    if (!editingId && kind !== "pincode" && !geometry) {
      // An area with no shape can never be the resolved zone (resolve_delivery_zone requires a
      // boundary) and is not a PIN rule either, so it would be saved as a rule that does nothing.
      toast.error(
        "This area has no finished shape - click the first point again to close the outline. Without a boundary a neighbourhood prices nothing, so set the type to PIN code instead if that is what you meant.",
      );
      return;
    }
    // A boundary is optional. A row with no drawn shape is a PIN-only rule, which is still how a
    // PIN is gated, and the pre-existing table created those too - refusing one here left managers
    // unable to do from this panel what they could do before it.

    setSaving(true);
    try {
      const supabase = createClient();
      const { data, error } = await supabase.rpc("save_delivery_zone", {
        p_id: editingId,
        p_kind: kind,
        p_value: value.trim(),
        p_name: name.trim(),
        p_priority: priorityNumber,
        p_geojson: geometry ? JSON.stringify(geometry) : null,
        p_delivery_fee_paise: fee,
        p_delivery_charge_threshold_paise: charge,
        p_free_delivery_threshold_paise: free,
        p_active: active,
        p_clear_boundary: clearBoundary,
      });
      if (error) throw error;
      const result = (data ?? {}) as SaveResult;
      if (result.error) {
        toast.error(result.message || "Could not save this area");
        return;
      }
      toast.success(
        result.overlapping_areas
          ? `Saved - it overlaps ${result.overlapping_areas} other area(s); priority decides which prices a spot`
          : geometry
            ? "Delivery area saved with its drawn boundary"
            : "Saved as a PIN-only rule - no shape attached, so it sets no zone price",
      );
      resetForm();
      await refreshZones();
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not save this area");
    } finally {
      setSaving(false);
    }
  };

  // The list must load on its own, not only after a save. Without this the coverage
  // table sits on "Loading delivery areas…" forever on first visit.
  useEffect(() => {
    void refreshZones();
  }, [refreshZones]);

  // M4: completions from the store's own areas, over the same RPC the customer app queries, so
  // a manager and a shopper are never offered different lists. Debounced and cleared on every
  // keystroke, because a stale chip that fills the wrong PIN is worse than no chip at all.
  useEffect(() => {
    const query = value.trim();
    if (query.length < 2) {
      setSuggestions([]);
      return;
    }
    let cancelled = false;
    const timer = setTimeout(async () => {
      const supabase = createClient();
      const { data, error } = await supabase.rpc("search_area_suggestions", {
        p_query: query,
        p_limit: 6,
      });
      if (cancelled) return;
      const rows = !error && Array.isArray(data) ? (data as AreaSuggestion[]) : [];
      // A manager is completing an area to price or check; their own saved addresses would be
      // noise in a field that means "the value the store serves".
      setSuggestions(rows.filter((row) => row.type === "area"));
    }, 300);
    return () => {
      cancelled = true;
      clearTimeout(timer);
    };
  }, [value]);

  const applySuggestion = (item: AreaSuggestion) => {
    if (item.value) setValue(item.value);
    if (item.kind === "pincode" || item.kind === "neighbourhood") setKind(item.kind);
    if (typeof item.lat === "number" && typeof item.lng === "number") {
      // Take the spot from the drawn area itself, so "check this spot" then prices the centre
      // of what the manager just picked rather than wherever the camera happened to be.
      setSpot({ lat: item.lat, lng: item.lng });
      map.current?.flyTo({
        center: [item.lng, item.lat],
        zoom: Math.max(map.current.getZoom(), 11),
        essential: true,
      });
    }
    setSuggestions([]);
  };

  /** Ask the server what it would charge at the chosen spot for the typed PIN. */
  const testSpot = async () => {
    // Validate the PIN first: it is the actionable message, and checking the spot first
    // would hide it whenever the map cannot start.
    if (!value.trim()) {
      toast.error("Enter the PIN code you want to test");
      return;
    }
    const center = spot ?? map.current?.getCenter();
    if (!center) {
      // Never fail silently: say why there is nothing to test.
      toast.error(
        mapUnavailable
          ? "The map is unavailable in this browser, so there is no spot to test."
          : "Click the map to choose a spot to test.",
      );
      return;
    }
    setTesting(true);
    setAnswer(null);
    try {
      const supabase = createClient();
      const { data, error } = await supabase.rpc("check_delivery_availability", {
        p_pin_code: value.trim(),
        p_lat: center.lat,
        p_lng: center.lng,
        p_eligible_subtotal: 0,
      });
      if (error) throw error;
      setAnswer((data ?? null) as SpotAnswer);
    } catch (err) {
      toast.error(err instanceof Error ? err.message : "Could not check this spot");
    } finally {
      setTesting(false);
    }
  };

  return (
    <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
      <div className="lg:col-span-2 space-y-3">
        <div className="relative rounded-xl overflow-hidden border border-gray-200 bg-gray-100">
          {/* MapLibre rewrites this element's position and sizing when it attaches, so the
              container must carry its own dimensions: an `absolute inset-0` div collapses
              to height 0 and MapLibre silently falls back to its default 300px canvas. */}
          <div ref={container} style={{ width: "100%", height: MAP_HEIGHT_PX }} />
          {!ready && !mapUnavailable && (
            // z-index 1, not auto: these full-area overlays come after the map in the DOM, so they
            // would otherwise cover the drawing toolbar (MapLibre sits its controls at z-index 2)
            // and the tools would be visible but unclickable until the style finished loading.
            <div
              className="absolute inset-0 flex items-center justify-center bg-white/70"
              style={{ zIndex: 1 }}
            >
              <Loader2 className="h-6 w-6 animate-spin text-indigo-600" />
            </div>
          )}
          {mapUnavailable && (
            <div
              className="absolute inset-0 flex items-center justify-center p-6 text-center"
              style={{ zIndex: 1 }}
            >
              <p className="text-sm text-gray-600 max-w-sm">{mapUnavailable}</p>
            </div>
          )}
          {mapError && (
            <div
              className="absolute inset-x-0 bottom-0 bg-red-50 text-red-700 text-sm px-4 py-2"
              style={{ zIndex: 1 }}
            >
              {mapError}
            </div>
          )}
          {ready && !drawReady && (
            <div className="absolute bottom-2 left-2 text-xs text-gray-600 bg-white/80 rounded px-2 py-1">
              Starting the drawing tool…
            </div>
          )}
        </div>
        <p className="text-xs text-gray-500">
          Use the polygon button (top-left of the map) to drop points, then click the first point
          again to close the shape. Press Escape to abandon an unfinished shape, and the trash
          button to delete a drawn one. The outline is a pricing region only: a customer is still
          accepted or rejected by their PIN code, never by this shape.
        </p>
        {/* The shape is captured in React state, not on the map, so say plainly whether this form
            holds one. Without it a manager cannot tell a drawn boundary from one that will save. */}
        <p
          className={`text-xs ${geometry ? "text-green-700" : "text-gray-500"}`}
          data-testid="captured-shape"
        >
          {geometry
            ? `Shape ready to save: ${(geometry.coordinates?.[0]?.length ?? 1) - 1} points.`
            : "No shape captured yet - drawing one and closing it shows a point count here."}
        </p>
      </div>

      <div className="space-y-4">
        <div className="bg-white rounded-xl border border-gray-200 p-5 space-y-3">
          <h3 className="text-sm font-semibold text-gray-900">
            {selectedZone ? `Editing “${selectedZone.label}”` : "New delivery area"}
          </h3>

          <Field label="Type">
            <select
              value={kind}
              onChange={(event) => setKind(event.target.value as any)}
              className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
            >
              <option value="neighbourhood">Neighbourhood</option>
              <option value="pincode">PIN code</option>
            </select>
          </Field>

          <Field label="PIN code / area name *">
            <input
              value={value}
              onChange={(event) => setValue(event.target.value)}
              placeholder={kind === "pincode" ? "e.g. 122001" : "e.g. Sector 14"}
              className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
            />
          </Field>

          {/* Completions come from the store's own rows, so every chip is an area this account
              has actually chosen to serve. Nothing here is bought from a third-party geocoder. */}
          {suggestions.length > 0 && (
            <div className="flex flex-wrap gap-1.5 mt-1">
              {suggestions.map((item) => (
                <button
                  key={item.id}
                  type="button"
                  onClick={() => applySuggestion(item)}
                  className="text-xs px-2 py-1 rounded-full bg-indigo-50 text-indigo-700 hover:bg-indigo-100"
                  title={
                    item.has_boundary
                      ? "Drawn area - the map moves to its centre"
                      : "No shape drawn yet"
                  }
                >
                  {item.label}
                  {item.value && item.value !== item.label ? ` · ${item.value}` : ""}
                  {item.has_boundary ? " · drawn" : ""}
                </button>
              ))}
            </div>
          )}

          <Field label="Display name">
            <input
              value={name}
              onChange={(event) => setName(event.target.value)}
              placeholder="Shown on the customer map"
              className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
            />
          </Field>

          <Field label="Priority (1 wins where areas overlap)">
            <input
              value={priority}
              onChange={(event) => setPriority(event.target.value)}
              inputMode="numeric"
              className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
            />
          </Field>

          <div className="grid grid-cols-3 gap-2">
            <Field label="Fee (₹)">
              <RupeeInput value={feeRupees} onChange={setFeeRupees} />
            </Field>
            <Field label="Charge below (₹)">
              <RupeeInput value={chargeRupees} onChange={setChargeRupees} />
            </Field>
            <Field label="Free above (₹)">
              <RupeeInput value={freeRupees} onChange={setFreeRupees} />
            </Field>
          </div>
          <p className="text-xs text-gray-500">
            Blank means use the store-wide setting. Amounts are stored in paise.
          </p>

          <label className="flex items-center gap-2 text-sm text-gray-700">
            <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />
            Active
          </label>

          {selectedZone && (
            <label className="flex items-center gap-2 text-sm text-gray-700">
              <input
                type="checkbox"
                checked={clearBoundary}
                onChange={(e) => setClearBoundary(e.target.checked)}
              />
              Remove the drawn shape (keep the pricing rule)
            </label>
          )}

          <div className="flex items-center gap-2 pt-1">
            <button
              onClick={save}
              disabled={saving}
              className="btn-primary inline-flex items-center gap-2 disabled:opacity-60"
            >
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Save area
            </button>
            <button
              onClick={testSpot}
              disabled={testing}
              className="btn-primary inline-flex items-center gap-2"
            >
              {testing ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : (
                <Radar className="h-4 w-4" />
              )}
              Test this spot
            </button>
            {editingId && (
              <button onClick={resetForm} className="btn-primary inline-flex items-center gap-2">
                New
              </button>
            )}
          </div>

          {answer && (
            <div
              className={`text-sm rounded-lg px-3 py-2 ${
                answer.error
                  ? "bg-red-50 text-red-700"
                  : answer.deliverable
                    ? "bg-green-50 text-green-800"
                    : "bg-amber-50 text-amber-800"
              }`}
            >
              {answer.error ? (
                answer.message || "Could not check this spot"
              ) : (
                <>
                  <p>{answer.message}</p>
                  <p className="text-xs mt-1">
                    Fee {formatRupees(answer.fee_paise ?? 0)}
                    {answer.zone_label ? ` · area “${answer.zone_label}”` : " · no area covers it"}
                    {(answer.amount_to_free_delivery ?? 0) > 0
                      ? ` · free above ${formatRupees(answer.amount_to_free_delivery ?? 0)}`
                      : ""}
                  </p>
                  {answer.rule_note ? (
                    <p className="text-[11px] mt-1 text-gray-600">{answer.rule_note}</p>
                  ) : null}
                </>
              )}
            </div>
          )}
        </div>
      </div>

      <div className="lg:col-span-3 bg-white rounded-xl border border-gray-200 overflow-hidden">
        <div className="px-6 py-4 border-b border-gray-200 flex items-center gap-2">
          <MapPinned className="h-4 w-4 text-indigo-600" />
          <h3 className="text-sm font-semibold text-gray-900">
            Delivery areas and what they cover
          </h3>
        </div>
        {loadingZones ? (
          <div className="p-6 flex items-center gap-2 text-gray-500">
            <Loader2 className="h-4 w-4 animate-spin" /> Loading delivery areas…
          </div>
        ) : zonesError ? (
          <div className="p-6 text-sm text-red-700 bg-red-50">{zonesError}</div>
        ) : !zones || zones.length === 0 ? (
          <div className="p-6 text-sm text-gray-500">
            No areas saved yet. Until one is drawn, every PIN uses the store-wide delivery fee, and
            the customer map shows no shading.
          </div>
        ) : (
          <table className="w-full text-sm">
            <thead className="bg-gray-50 text-left text-xs uppercase tracking-wider text-gray-500">
              <tr>
                <th className="px-6 py-3">Area</th>
                <th className="px-6 py-3">Type</th>
                <th className="px-6 py-3">Priority</th>
                <th className="px-6 py-3">Fee</th>
                <th className="px-6 py-3">Addresses inside</th>
                <th className="px-6 py-3">Overlaps</th>
                <th className="px-6 py-3">Shape</th>
                <th className="px-6 py-3">Status</th>
                <th className="px-6 py-3" />
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-200">
              {zones.map((zone) => (
                <tr key={zone.id} className={editingId === zone.id ? "bg-indigo-50" : ""}>
                  <td className="px-6 py-3 font-medium text-gray-900">{zone.label}</td>
                  <td className="px-6 py-3 text-gray-600">{zone.kind}</td>
                  <td className="px-6 py-3 text-gray-600">{zone.priority}</td>
                  <td className="px-6 py-3 text-gray-600">
                    {zone.delivery_fee_paise == null
                      ? "store default"
                      : formatRupees(zone.delivery_fee_paise)}
                  </td>
                  <td className="px-6 py-3 text-gray-600">{zone.addresses_inside}</td>
                  <td className="px-6 py-3 text-gray-600">
                    {zone.overlapping_areas > 0 ? (
                      <span className="text-amber-700">{zone.overlapping_areas}</span>
                    ) : (
                      "0"
                    )}
                  </td>
                  <td className="px-6 py-3">
                    {/* Every saved area is listed now, drawn or not, so say what each one does.
                        A row with neither a shape nor PIN semantics can never be selected. */}
                    {(zone.has_boundary ?? !!zone.geojson) ? (
                      <span className="text-green-700 text-xs">Drawn</span>
                    ) : zone.kind === "pincode" ? (
                      <span className="text-gray-600 text-xs">PIN rule, no shape</span>
                    ) : (
                      <span className="text-amber-700 text-xs">No shape - prices nothing</span>
                    )}
                  </td>
                  <td className="px-6 py-3">
                    <span
                      className={
                        zone.active
                          ? "px-2 py-1 rounded-full bg-green-100 text-green-700 text-xs"
                          : "px-2 py-1 rounded-full bg-gray-100 text-gray-600 text-xs"
                      }
                    >
                      {zone.active ? "Active" : "Inactive"}
                    </span>
                  </td>
                  <td className="px-6 py-3 text-right">
                    <button
                      onClick={() => startEdit(zone)}
                      className="text-indigo-600 hover:underline mr-3"
                    >
                      Edit
                    </button>
                    <button
                      onClick={() => {
                        draw.current?.set({
                          type: "FeatureCollection",
                          features: zone.geojson
                            ? [{ type: "Feature", properties: {}, geometry: zone.geojson as any }]
                            : [],
                        });
                        setGeometry(zone.geojson);
                      }}
                      className="text-gray-500 hover:text-red-600 inline-flex items-center gap-1"
                      title="Load this shape into the editor to redraw it"
                    >
                      <Trash2 className="h-3.5 w-3.5" /> Redraw
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

function pickGeometry(control: any): GeoJSONGeometry | null {
  const features = control?.getAll?.()?.features ?? [];
  // The newest shape wins: a manager correcting a boundary draws over the old one.
  const latest = features[features.length - 1];
  const geometry = latest?.geometry as GeoJSONGeometry | undefined;
  return geometry && geometry.type === "Polygon" ? geometry : null;
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <label className="block">
      <span className="block text-sm font-medium text-gray-700 mb-1">{label}</span>
      {children}
    </label>
  );
}

function RupeeInput({ value, onChange }: { value: string; onChange: (next: string) => void }) {
  return (
    <input
      value={value}
      onChange={(event) => onChange(event.target.value)}
      inputMode="decimal"
      placeholder="—"
      className="w-full px-3 py-2 border rounded-md focus:ring-2 focus:ring-indigo-500"
    />
  );
}
