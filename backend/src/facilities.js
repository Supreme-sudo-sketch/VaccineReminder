const OVERPASS_URLS = (
  process.env.OVERPASS_URLS ||
  process.env.OVERPASS_URL ||
  'https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter'
)
  .split(',')
  .map((u) => u.trim())
  .filter(Boolean);
const NOMINATIM_URL = process.env.NOMINATIM_URL || 'https://nominatim.openstreetmap.org/search';
const DAY_MS = 24 * 3600 * 1000;
const RADII_M = [3000, 8000, 20000, 50000];
const MIN_RESULTS = 3;
const OVERPASS_PAUSE_MS = 5 * 60 * 1000;
const KENYA = { south: -4.9, north: 5.1, west: 33.8, east: 42.0 };

export const OSM_ATTRIBUTION = '© OpenStreetMap contributors';
export const MAX_SEARCH_KM = RADII_M[RADII_M.length - 1] / 1000;

export class OsmError extends Error {}

export function osmUserAgent() {
  return process.env.OSM_USER_AGENT || 'VaccineReminderKE/2.0 (student prototype, BBT 3203)';
}

export function haversineKm(lat1, lon1, lat2, lon2) {
  const rad = (d) => (d * Math.PI) / 180;
  const dLat = rad(lat2 - lat1);
  const dLon = rad(lon2 - lon1);
  const a = Math.sin(dLat / 2) ** 2 + Math.cos(rad(lat1)) * Math.cos(rad(lat2)) * Math.sin(dLon / 2) ** 2;
  return 6371 * 2 * Math.asin(Math.sqrt(a));
}

export function inKenya(lat, lon) {
  return lat >= KENYA.south && lat <= KENYA.north && lon >= KENYA.west && lon <= KENYA.east;
}

function facilityType(tags) {
  if (tags.amenity === 'hospital' || tags.healthcare === 'hospital') return 'hospital';
  if (tags.healthcare === 'centre') return 'health_centre';
  return 'clinic';
}

function overpassQuery(lat, lon, radius) {
  const around = `(around:${radius},${lat},${lon})`;
  return `[out:json][timeout:25];(nwr["amenity"~"^(hospital|clinic)$"]${around};nwr["healthcare"~"^(hospital|clinic|centre)$"]${around};);out center tags;`;
}

function toPlace(el) {
  const tags = el.tags || {};
  const name = String(tags.name || tags['name:en'] || '').trim();
  const lat = el.lat ?? el.center?.lat;
  const lon = el.lon ?? el.center?.lon;
  if (!name || typeof lat !== 'number' || typeof lon !== 'number' || !inKenya(lat, lon)) return null;
  const phone = String(tags.phone || tags['contact:phone'] || tags['contact:mobile'] || '').split(';')[0].trim();
  return {
    name,
    type: facilityType(tags),
    lat,
    lon,
    phone: phone || null,
    openingHours: tags.opening_hours ? String(tags.opening_hours) : null,
  };
}

function dedupe(places) {
  const kept = [];
  for (const p of places) {
    const same = kept.some((k) => k.name.toLowerCase() === p.name.toLowerCase() && haversineKm(k.lat, k.lon, p.lat, p.lon) < 0.3);
    if (!same) kept.push(p);
  }
  return kept;
}

const hospitalsIn = (places) => places.filter((p) => p.type === 'hospital');

function preferHospitals(places, radius) {
  const hospitals = hospitalsIn(places);
  return hospitals.length ? { kind: 'hospital', places: hospitals, radius } : { kind: 'clinic', places, radius };
}

export function createOsmClient({
  fetchImpl = (...args) => globalThis.fetch(...args),
  now = () => Date.now(),
  sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  timeoutMs = 15000,
  nominatimIntervalMs = 1000,
  retryDelayMs = 1000,
  overpassUrls = OVERPASS_URLS,
} = {}) {
  const placeCache = new Map();
  const geocodeCache = new Map();
  const inflight = new Map();
  let nextNominatimAt = 0;
  let preferredOverpass = 0;
  let overpassPausedUntil = 0;

  function prune(cache) {
    if (cache.size < 500) return;
    for (const [key, entry] of cache) if (now() - entry.at > DAY_MS) cache.delete(key);
  }

  async function getJson(url, init = {}) {
    let res;
    try {
      res = await fetchImpl(url, {
        ...init,
        headers: { ...(init.headers || {}), 'User-Agent': osmUserAgent(), Accept: 'application/json' },
        signal: AbortSignal.timeout(timeoutMs),
      });
    } catch (e) {
      throw new OsmError(`OpenStreetMap service could not be reached: ${e.message || e}`);
    }
    if (!res.ok) throw new OsmError(`OpenStreetMap service answered ${res.status}`);
    try {
      return await res.json();
    } catch {
      throw new OsmError('OpenStreetMap service sent an unreadable reply');
    }
  }

  async function nominatimJson(params) {
    const wait = Math.max(0, nextNominatimAt - now());
    nextNominatimAt = Math.max(now(), nextNominatimAt) + nominatimIntervalMs;
    if (wait > 0) await sleep(wait);
    return getJson(`${NOMINATIM_URL}?${params}`, { headers: { 'Accept-Language': 'en' } });
  }

  async function overpassPlaces(lat, lon, radius) {
    const body = new URLSearchParams({ data: overpassQuery(lat, lon, radius) }).toString();
    let lastError;
    for (let attempt = 0; attempt < overpassUrls.length; attempt++) {
      const index = (preferredOverpass + attempt) % overpassUrls.length;
      try {
        const json = await getJson(overpassUrls[index], {
          method: 'POST',
          headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
          body,
        });
        preferredOverpass = index;
        return dedupe((json.elements || []).map(toPlace).filter(Boolean));
      } catch (e) {
        lastError = e;
        if (attempt < overpassUrls.length - 1 && retryDelayMs > 0) await sleep(retryDelayMs * 2 ** attempt);
      }
    }
    throw lastError;
  }

  async function searchOverpass(lat, lon) {
    let places = [];
    let radius = RADII_M[0];
    for (radius of RADII_M) {
      places = await overpassPlaces(lat, lon, radius);
      if (hospitalsIn(places).length >= MIN_RESULTS) break;
    }
    return preferHospitals(places, radius);
  }

  async function nominatimPlaces(kind, lat, lon, radius) {
    const dLat = radius / 111320;
    const dLon = radius / (111320 * Math.cos((lat * Math.PI) / 180));
    const viewbox = [lon - dLon, lat + dLat, lon + dLon, lat - dLat].map((n) => n.toFixed(4)).join(',');
    const params = new URLSearchParams({
      q: kind,
      format: 'jsonv2',
      countrycodes: 'ke',
      viewbox,
      bounded: '1',
      limit: '40',
      extratags: '1',
    });
    const json = await nominatimJson(params);
    const wanted = kind === 'hospital' ? ['hospital'] : ['clinic', 'doctors', 'centre'];
    return dedupe(
      (Array.isArray(json) ? json : [])
        .filter((r) => ['amenity', 'healthcare'].includes(r.category) && wanted.includes(r.type))
        .map((r) =>
          toPlace({
            lat: Number(r.lat),
            lon: Number(r.lon),
            tags: { ...(r.extratags || {}), name: r.name, [r.category]: r.type === 'doctors' ? 'clinic' : r.type },
          })
        )
        .filter(Boolean)
    );
  }

  async function searchNominatim(lat, lon) {
    let hospitals = [];
    let radius = RADII_M[0];
    for (radius of RADII_M) {
      hospitals = await nominatimPlaces('hospital', lat, lon, radius);
      if (hospitals.length >= MIN_RESULTS) break;
    }
    if (hospitals.length) return { kind: 'hospital', places: hospitals, radius };
    let clinics = [];
    for (radius of RADII_M) {
      clinics = await nominatimPlaces('clinic', lat, lon, radius);
      if (clinics.length >= MIN_RESULTS) break;
    }
    return { kind: 'clinic', places: clinics, radius };
  }

  async function search(lat, lon) {
    if (now() < overpassPausedUntil) return { ...(await searchNominatim(lat, lon)), source: 'nominatim' };
    try {
      return { ...(await searchOverpass(lat, lon)), source: 'overpass' };
    } catch (overpassError) {
      overpassPausedUntil = now() + OVERPASS_PAUSE_MS;
      try {
        return { ...(await searchNominatim(lat, lon)), source: 'nominatim' };
      } catch {
        throw overpassError;
      }
    }
  }

  async function nearbyFacilities(lat, lon, limit = 20) {
    const key = `${lat.toFixed(2)},${lon.toFixed(2)}`;
    let entry = placeCache.get(key);
    if (!entry || now() - entry.at > DAY_MS) {
      if (!inflight.has(key)) {
        const centre = key.split(',').map(Number);
        inflight.set(key, search(centre[0], centre[1]).finally(() => inflight.delete(key)));
      }
      try {
        const found = await inflight.get(key);
        prune(placeCache);
        entry = { at: now(), ...found };
        placeCache.set(key, entry);
      } catch (e) {
        if (!entry) throw e;
      }
    }
    const facilities = entry.places
      .map((p) => ({ ...p, distanceKm: Math.round(haversineKm(lat, lon, p.lat, p.lon) * 10) / 10 }))
      .sort((a, b) => a.distanceKm - b.distanceKm || a.name.localeCompare(b.name))
      .slice(0, limit);
    return { facilities, kind: entry.kind, radiusKm: entry.radius / 1000, source: entry.source };
  }

  async function geocode(query) {
    const key = query.trim().toLowerCase();
    const hit = geocodeCache.get(key);
    if (hit && now() - hit.at < DAY_MS) return hit.results;
    const json = await nominatimJson(new URLSearchParams({ q: query, countrycodes: 'ke', format: 'jsonv2', limit: '5' }));
    const results = (Array.isArray(json) ? json : [])
      .map((r) => ({
        label: String(r.display_name || r.name || ''),
        name: String(r.name || String(r.display_name || '').split(',')[0]).trim(),
        lat: Number(r.lat),
        lon: Number(r.lon),
      }))
      .filter((r) => r.label && Number.isFinite(r.lat) && Number.isFinite(r.lon));
    prune(geocodeCache);
    geocodeCache.set(key, { at: now(), results });
    return results;
  }

  return { nearbyFacilities, geocode };
}
