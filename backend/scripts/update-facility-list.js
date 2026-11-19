// Downloads every named hospital, clinic and health centre in Kenya from OpenStreetMap, with its
// county, into data/facilities-ke.json. Health workers pick from this list instead of typing a name.
//
//   npm run update-facilities
//
// It is one large Overpass query (a minute or two). Run it again now and then to pick up changes.
import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { osmUserAgent, inKenya } from '../src/facilities.js';

const OUT = join(dirname(fileURLToPath(import.meta.url)), '..', 'data', 'facilities-ke.json');
const SERVERS = (process.env.OVERPASS_URLS || 'https://overpass-api.de/api/interpreter,https://overpass.private.coffee/api/interpreter')
  .split(',')
  .map((u) => u.trim())
  .filter(Boolean);

const FACILITIES = (area) =>
  `(nwr(area.${area})["amenity"~"^(hospital|clinic)$"]["name"];nwr(area.${area})["healthcare"~"^(hospital|clinic|centre)$"]["name"];);out center tags;`;

// Each county's tags are printed just before the facilities inside it, so every facility can be
// given its county. The last group is all of Kenya, to catch anything outside a county boundary.
const QUERY = `[out:json][timeout:900];
area["ISO3166-1"="KE"]["admin_level"="2"]->.ke;
rel(area.ke)["boundary"="administrative"]["admin_level"="4"]["ISO3166-2"~"^KE-"];
map_to_area->.counties;
foreach.counties->.county(.county out tags;${FACILITIES('county')});
.ke out tags;${FACILITIES('ke')}`;

function facilityType(tags) {
  if (tags.amenity === 'hospital' || tags.healthcare === 'hospital') return 'hospital';
  if (tags.healthcare === 'centre') return 'health_centre';
  return 'clinic';
}

const countyName = (tags) => String(tags.name || '').replace(/`/g, "'").replace(/\s+County$/i, '').trim();

async function download() {
  for (const url of SERVERS) {
    console.log(`Asking ${url} ...`);
    try {
      const res = await fetch(url, {
        method: 'POST',
        headers: { 'User-Agent': osmUserAgent(), 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ data: QUERY }),
        signal: AbortSignal.timeout(15 * 60 * 1000),
      });
      if (res.ok) return await res.json();
      console.warn(`  ${res.status} ${(await res.text()).slice(0, 200)}`);
    } catch (e) {
      console.warn(`  ${e.message}`);
    }
  }
  throw new Error('Every Overpass server failed. Try again later.');
}

const { elements } = await download();
const seen = new Set();
const facilities = [];
let county = '';
for (const el of elements) {
  if (el.type === 'area') {
    county = el.tags?.['ISO3166-1'] === 'KE' ? '' : countyName(el.tags || {});
    continue;
  }
  const key = `${el.type}/${el.id}`;
  if (seen.has(key)) continue;
  const tags = el.tags || {};
  const name = String(tags.name || '').replace(/\s+/g, ' ').trim();
  const lat = el.lat ?? el.center?.lat;
  const lon = el.lon ?? el.center?.lon;
  if (!name || typeof lat !== 'number' || typeof lon !== 'number' || !inKenya(lat, lon)) continue;
  seen.add(key);
  facilities.push({ name, type: facilityType(tags), county, lat: Number(lat.toFixed(5)), lon: Number(lon.toFixed(5)) });
}
facilities.sort((a, b) => a.name.localeCompare(b.name) || a.county.localeCompare(b.county));

mkdirSync(dirname(OUT), { recursive: true });
const out = {
  source: 'OpenStreetMap, via the Overpass API',
  attribution: '© OpenStreetMap contributors, licensed under the Open Database License (ODbL)',
  downloaded: new Date().toISOString().slice(0, 10),
  facilities,
};
// One facility per line keeps the file readable and its changes small when it is updated.
writeFileSync(OUT, JSON.stringify(out, null, 0).replace(/\[\{/, '[\n{').replace(/\},\{/g, '},\n{').replace(/\}\]\}$/, '}\n]}\n'));
const byType = facilities.reduce((m, f) => ({ ...m, [f.type]: (m[f.type] || 0) + 1 }), {});
console.log(`Saved ${facilities.length} facilities to ${OUT}`, byType, `${facilities.filter((f) => !f.county).length} without a county`);
