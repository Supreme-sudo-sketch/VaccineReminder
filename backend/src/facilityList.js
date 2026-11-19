// The list health workers pick a facility from: Kenyan hospitals, clinics and health centres from
// OpenStreetMap (data/facilities-ke.json, made by scripts/update-facility-list.js), plus any facility
// name already saved in this system, so a clinic OpenStreetMap is missing can be chosen once someone
// has entered it.
import { readFileSync } from 'node:fs';

const FILE = new URL('../data/facilities-ke.json', import.meta.url);
const TYPE_ORDER = { hospital: 0, health_centre: 1, clinic: 2, in_use: 3 };
export const MAX_RESULTS = 30;

export function loadFacilityList(file = FILE) {
  try {
    const list = JSON.parse(readFileSync(file, 'utf8')).facilities;
    return Array.isArray(list) ? list : [];
  } catch (e) {
    console.warn(`Facility list not loaded (${e.message}). Run "npm run update-facilities".`);
    return [];
  }
}

// Lower case, no accents or punctuation, "&" read as "and": "St. Mary's & Co" -> "st marys and co".
export const normalizeName = (s) =>
  String(s ?? '')
    .normalize('NFKD')
    .replace(/[̀-ͯ]/g, '')
    .toLowerCase()
    .replace(/['’`]/g, '')
    .replace(/&/g, ' and ')
    .replace(/[^a-z0-9]+/g, ' ')
    .trim();

const entry = (name, type, county) => ({
  name,
  type,
  county,
  key: normalizeName(name),
  nameWords: normalizeName(name).split(' '),
  countyWords: county ? normalizeName(county).split(' ') : [],
});

export function createFacilityIndex(list) {
  const seen = new Set();
  const known = new Set();
  const entries = [];
  for (const f of list) {
    if (!f || !f.name) continue;
    const e = entry(String(f.name), TYPE_ORDER[f.type] === undefined ? 'clinic' : f.type, String(f.county || ''));
    const id = `${e.key}|${e.county}`;
    if (seen.has(id)) continue;
    seen.add(id);
    known.add(e.key);
    entries.push(e);
  }

  // Every word typed must start a word of the name or the county: "ken nat" finds Kenyatta National
  // Hospital and "riruta nairobi" finds Riruta Health Centre in Nairobi. Names starting with what was
  // typed come first, then hospitals before health centres and clinics.
  function search(query, inUseNames = []) {
    const q = normalizeName(query);
    if (!q) return { facilities: [], more: false };
    const words = q.split(' ');
    const extra = [];
    for (const name of inUseNames) {
      const key = normalizeName(name);
      if (key && !known.has(key) && !extra.some((e) => e.key === key)) extra.push(entry(String(name).trim(), 'in_use', ''));
    }
    const matches = [];
    for (const e of [...entries, ...extra]) {
      let inCounty = false;
      const all = words.every((w) => {
        if (e.nameWords.some((n) => n.startsWith(w))) return true;
        if (e.countyWords.some((c) => c.startsWith(w))) return (inCounty = true);
        return false;
      });
      if (all) matches.push({ e, score: (e.key.startsWith(q) ? 0 : 2) + (inCounty ? 1 : 0) });
    }
    matches.sort(
      (a, b) => a.score - b.score || TYPE_ORDER[a.e.type] - TYPE_ORDER[b.e.type] || a.e.name.localeCompare(b.e.name) || a.e.county.localeCompare(b.e.county)
    );
    return {
      facilities: matches.slice(0, MAX_RESULTS).map(({ e }) => ({ name: e.name, type: e.type, county: e.county || null })),
      more: matches.length > MAX_RESULTS,
    };
  }

  return { size: entries.length, search };
}
