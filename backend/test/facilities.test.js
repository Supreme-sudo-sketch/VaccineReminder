import test from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db.js';
import { createApp } from '../src/app.js';
import { createOsmClient, haversineKm, inKenya } from '../src/facilities.js';
import { nearbyClinicsText } from '../src/messages.js';

const CODE = 'test-registration-code';
const today = '2026-10-25';
const HOME = { lat: -4.0435, lon: 39.6682 };
const SERVERS = ['https://overpass.one/api/interpreter', 'https://overpass.two/api/interpreter'];

const worker = { name: 'Achieng Otieno', phone: '0722000111', facility: 'Kangemi Health Centre', password: 'secret123', registrationCode: CODE };
const child = { childName: 'Amani', caregiverName: 'Wanjiru', phone: '0712345678', dob: '2026-09-13', language: 'sw', facility: 'Kangemi Health Centre', consent: true };

const reply = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
const node = (id, name, lat, lon, tags) => ({ type: 'node', id, lat, lon, tags: { name, ...tags } });
const hospital = (id, name, lat, lon, tags = {}) => node(id, name, lat, lon, { amenity: 'hospital', ...tags });
const clinic = (id, name, lat, lon, tags = {}) => node(id, name, lat, lon, { amenity: 'clinic', ...tags });

const AGA_KHAN = hospital(2, 'Aga Khan Hospital', -4.0438, 39.6678, { 'contact:phone': '+254 41 222 7710;+254 41 000 0000', opening_hours: '24/7' });
const COAST_GENERAL = { type: 'way', id: 1, center: { lat: -4.048, lon: 39.674 }, tags: { healthcare: 'hospital', name: 'Coast General Hospital', phone: '041 231 4201' } };
const PANDYA = hospital(3, 'Pandya Memorial Hospital', -4.03, 39.7);
const CLINICS = [
  clinic(4, 'Tudor Clinic', -4.042, 39.669),
  node(5, 'Majengo Health Centre', -4.035, 39.675, { healthcare: 'centre' }),
  clinic(6, '', -4.044, 39.668),
];

function mockFetch({ overpass = {}, overpassStatus = 200, failingHosts = [], nominatim = {}, nominatimStatus = 200 } = {}) {
  const calls = [];
  const fetchImpl = async (url, init = {}) => {
    const u = String(url);
    calls.push({ url: u, init });
    if (u.includes('overpass')) {
      if (failingHosts.some((h) => u.includes(h))) return reply({ error: 'at capacity' }, 502);
      if (overpassStatus !== 200) return reply({ error: 'busy' }, overpassStatus);
      const query = new URLSearchParams(init.body).get('data');
      const radius = Number(/around:(\d+)/.exec(query)[1]);
      const elements = Object.entries(overpass)
        .filter(([r]) => Number(r) <= radius)
        .flatMap(([, list]) => list);
      return reply({ elements });
    }
    if (u.includes('nominatim')) {
      if (nominatimStatus !== 200) return reply({ error: 'busy' }, nominatimStatus);
      const q = new URL(u).searchParams.get('q');
      return reply(nominatim[q] || nominatim.town || []);
    }
    throw new Error(`unexpected request to ${u}`);
  };
  const count = (part) => calls.filter((c) => c.url.includes(part)).length;
  return { fetchImpl, calls, count };
}

async function setup(fetchImpl) {
  const db = openDb(':memory:');
  const osm = createOsmClient({ fetchImpl, sleep: async () => {}, nominatimIntervalMs: 0, overpassUrls: SERVERS });
  const server = createApp(db, { registrationCode: CODE, clock: () => today, osm });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  let token = null;
  const call = async (method, p, body, auth = token) => {
    const res = await fetch(base + p, {
      method,
      headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, json: await res.json() };
  };
  token = (await call('POST', '/auth/register', worker, null)).json.token;
  return { db, server, call };
}

test('haversine distance and the Kenya check', () => {
  const km = haversineKm(-1.2864, 36.8172, -4.0435, 39.6682);
  assert.ok(km > 430 && km < 450, `Nairobi to Mombasa was ${km}`);
  assert.equal(haversineKm(1, 1, 1, 1), 0);
  assert.equal(inKenya(-4.0435, 39.6682), true);
  assert.equal(inKenya(0.338, 32.576), false);
  assert.equal(inKenya(-6.8, 39.28), false);
});

test('hospitals come first: the search widens until 3 hospitals and leaves smaller clinics out', async () => {
  const { fetchImpl, calls, count } = mockFetch({
    overpass: {
      3000: [COAST_GENERAL, AGA_KHAN, ...CLINICS, hospital(9, '', -4.044, 39.67)],
      8000: [PANDYA, { ...AGA_KHAN, type: 'way', id: 10, lat: undefined, lon: undefined, center: { lat: -4.0439, lon: 39.6679 }, tags: { amenity: 'hospital', name: 'aga khan hospital' } }],
    },
  });
  let clock = 1_000_000;
  const osm = createOsmClient({ fetchImpl, now: () => clock, overpassUrls: SERVERS });
  const result = await osm.nearbyFacilities(HOME.lat, HOME.lon);

  assert.equal(count('overpass'), 2);
  assert.equal(result.kind, 'hospital');
  assert.equal(result.radiusKm, 8);
  assert.equal(result.source, 'overpass');
  assert.deepEqual(result.facilities.map((f) => f.name), ['Aga Khan Hospital', 'Coast General Hospital', 'Pandya Memorial Hospital']);
  const [agaKhan, coast] = result.facilities;
  assert.equal(agaKhan.phone, '+254 41 222 7710');
  assert.equal(agaKhan.openingHours, '24/7');
  assert.equal(coast.type, 'hospital');
  assert.equal(coast.lat, -4.048);
  assert.equal(coast.distanceKm, Math.round(haversineKm(HOME.lat, HOME.lon, -4.048, 39.674) * 10) / 10);

  const first = calls[0];
  assert.equal(first.init.method, 'POST');
  assert.match(first.init.headers['User-Agent'], /VaccineReminder/);
  assert.ok(first.init.signal);
  const query = new URLSearchParams(first.init.body).get('data');
  assert.match(query, /around:3000,-4\.04,39\.67/);
  assert.match(query, /out center tags/);
  assert.match(query, /nwr\["amenity"~"\^\(hospital\|clinic\)\$"\]/);
  assert.match(query, /nwr\["healthcare"~"\^\(hospital\|clinic\|centre\)\$"\]/);

  await osm.nearbyFacilities(HOME.lat, HOME.lon);
  await osm.nearbyFacilities(HOME.lat + 0.001, HOME.lon);
  assert.equal(count('overpass'), 2);

  clock += 25 * 3600 * 1000;
  await osm.nearbyFacilities(HOME.lat, HOME.lon);
  assert.equal(count('overpass'), 4);
});

test('one hospital within 50 km still comes before nearer clinics', async () => {
  const kilifiRoad = hospital(7, 'Mtwapa Hospital', -3.85, 39.75);
  const { fetchImpl, count } = mockFetch({ overpass: { 3000: CLINICS, 50000: [kilifiRoad] } });
  const result = await createOsmClient({ fetchImpl, overpassUrls: SERVERS }).nearbyFacilities(HOME.lat, HOME.lon);
  assert.equal(count('overpass'), 4);
  assert.equal(result.kind, 'hospital');
  assert.equal(result.radiusKm, 50);
  assert.deepEqual(result.facilities.map((f) => f.name), ['Mtwapa Hospital']);
});

test('with no hospital in Kenya within 50 km it shows the nearest smaller clinics', async () => {
  const acrossTheBorder = hospital(8, 'Tanga Regional Hospital', -5.07, 39.1);
  const { fetchImpl, count } = mockFetch({ overpass: { 20000: [...CLINICS, acrossTheBorder] } });
  const result = await createOsmClient({ fetchImpl, overpassUrls: SERVERS }).nearbyFacilities(HOME.lat, HOME.lon);
  assert.equal(count('overpass'), 4);
  assert.equal(result.kind, 'clinic');
  assert.equal(result.radiusKm, 50);
  assert.deepEqual(result.facilities.map((f) => f.name), ['Tudor Clinic', 'Majengo Health Centre']);
  assert.equal(result.facilities[1].type, 'health_centre');
});

test('a busy Overpass server is skipped for the next one, with a pause in between', async () => {
  const { fetchImpl, calls } = mockFetch({ overpass: { 3000: [COAST_GENERAL, AGA_KHAN, PANDYA] }, failingHosts: ['overpass.one'] });
  const waits = [];
  const osm = createOsmClient({ fetchImpl, sleep: async (ms) => waits.push(ms), overpassUrls: SERVERS });
  const result = await osm.nearbyFacilities(HOME.lat, HOME.lon);
  assert.equal(result.facilities.length, 3);
  assert.deepEqual(calls.map((c) => new URL(c.url).host), ['overpass.one', 'overpass.two']);
  assert.deepEqual(waits, [1000]);

  await osm.nearbyFacilities(-1.29, 36.82);
  assert.equal(new URL(calls.at(-1).url).host, 'overpass.two');
  assert.equal(calls.length, 3);
});

test('when every Overpass server is down, Nominatim finds hospitals in Kenya instead', async () => {
  const found = (name, lat, lon, extra = {}) => ({ category: 'amenity', type: 'hospital', name, lat: String(lat), lon: String(lon), ...extra });
  const { fetchImpl, calls } = mockFetch({
    overpassStatus: 504,
    nominatim: {
      hospital: [
        found('Mombasa Hospital', -4.0648, 39.6808, { extratags: { phone: '+254 41 231 2191' } }),
        found('Coast General Teaching & Referral Hospital', -4.0483, 39.6741),
        found('Tudor sub county hospital', -4.0402, 39.6639),
        found('', -4.05, 39.66),
        { category: 'amenity', type: 'pharmacy', name: 'Mombasa Chemist', lat: '-4.05', lon: '39.67' },
      ],
    },
  });
  let clock = 0;
  const osm = createOsmClient({ fetchImpl, now: () => clock, sleep: async () => {}, nominatimIntervalMs: 0, overpassUrls: SERVERS });
  const result = await osm.nearbyFacilities(HOME.lat, HOME.lon);
  const overpassCalls = () => calls.filter((c) => c.url.includes('overpass')).length;
  assert.equal(overpassCalls(), SERVERS.length);
  await osm.nearbyFacilities(-4.2, 39.6);
  assert.equal(overpassCalls(), SERVERS.length);
  clock += 6 * 60 * 1000;
  await osm.nearbyFacilities(-3.6, 39.85);
  assert.equal(overpassCalls(), SERVERS.length * 2);
  assert.equal(result.source, 'nominatim');
  assert.equal(result.kind, 'hospital');
  assert.deepEqual(result.facilities.map((f) => f.name), [
    'Tudor sub county hospital',
    'Coast General Teaching & Referral Hospital',
    'Mombasa Hospital',
  ]);
  assert.equal(result.facilities[2].phone, '+254 41 231 2191');
  const search = new URL(calls.find((c) => c.url.includes('nominatim')).url).searchParams;
  assert.equal(search.get('q'), 'hospital');
  assert.equal(search.get('countrycodes'), 'ke');
  assert.equal(search.get('bounded'), '1');
  assert.equal(search.get('viewbox').split(',').length, 4);
});

test('when everything is down, the last saved results for that area are shown', async () => {
  let down = false;
  const live = mockFetch({ overpass: { 3000: [COAST_GENERAL, AGA_KHAN, PANDYA] } });
  const dead = mockFetch({ overpassStatus: 502, nominatimStatus: 503 });
  let clock = 0;
  const osm = createOsmClient({
    fetchImpl: (...args) => (down ? dead.fetchImpl(...args) : live.fetchImpl(...args)),
    now: () => clock,
    sleep: async () => {},
    nominatimIntervalMs: 0,
    overpassUrls: SERVERS,
  });
  assert.equal((await osm.nearbyFacilities(HOME.lat, HOME.lon)).facilities.length, 3);
  down = true;
  clock += 30 * 3600 * 1000;
  assert.equal((await osm.nearbyFacilities(HOME.lat, HOME.lon)).facilities.length, 3);
  await assert.rejects(() => osm.nearbyFacilities(0.52, 35.27));
});

test('town search uses Nominatim for Kenya only, one request per second, and caches', async () => {
  const { fetchImpl, calls, count } = mockFetch({
    nominatim: { town: [{ display_name: 'Mombasa, Mombasa County, Coast, Kenya', name: 'Mombasa', lat: '-4.0435', lon: '39.6682' }] },
  });
  const waits = [];
  const osm = createOsmClient({ fetchImpl, now: () => 5000, sleep: async (ms) => waits.push(ms) });

  const results = await osm.geocode('Mombasa');
  assert.deepEqual(results, [{ label: 'Mombasa, Mombasa County, Coast, Kenya', name: 'Mombasa', lat: -4.0435, lon: 39.6682 }]);
  const url = new URL(calls[0].url);
  assert.equal(url.searchParams.get('countrycodes'), 'ke');
  assert.equal(url.searchParams.get('q'), 'Mombasa');
  assert.match(calls[0].init.headers['User-Agent'], /VaccineReminder/);

  await osm.geocode('Kisumu');
  assert.deepEqual(waits, [1000]);
  await osm.geocode('  mombasa ');
  assert.equal(count('nominatim'), 2);
});

test('clinic and town endpoints validate input and report OpenStreetMap failures', async () => {
  const ok = mockFetch({
    overpass: { 3000: [COAST_GENERAL, AGA_KHAN, ...CLINICS] },
    nominatim: { town: [{ display_name: 'Mombasa, Kenya', lat: '-4.04', lon: '39.67' }] },
  });
  const { server, call } = await setup(ok.fetchImpl);
  try {
    for (const q of ['?lat=abc&lon=39.6', '?lat=95&lon=39.6', '?lat=-4.04', '?lat=&lon=39.6']) {
      assert.equal((await call('GET', '/facilities/nearby' + q, null, null)).status, 400, q);
    }
    const found = await call('GET', `/facilities/nearby?lat=${HOME.lat}&lon=${HOME.lon}`, null, null);
    assert.equal(found.status, 200);
    assert.equal(found.json.kind, 'hospital');
    assert.equal(found.json.maxSearchKm, 50);
    assert.deepEqual(found.json.facilities.map((f) => f.name), ['Aga Khan Hospital', 'Coast General Hospital']);
    assert.equal(found.json.attribution, '© OpenStreetMap contributors');
    assert.equal(found.json.note, 'Call to confirm that the vaccine is available.');

    assert.equal((await call('GET', '/geocode?q=a', null, null)).status, 400);
    const town = await call('GET', '/geocode?q=Mombasa', null, null);
    assert.equal(town.json.results[0].name, 'Mombasa');
  } finally {
    server.close();
  }

  const down = mockFetch({ overpassStatus: 504, nominatimStatus: 503 });
  const failing = await setup(down.fetchImpl);
  try {
    const r = await failing.call('GET', `/facilities/nearby?lat=${HOME.lat}&lon=${HOME.lon}`, null, null);
    assert.equal(r.status, 502);
    assert.equal(r.json.code, 'osm_unavailable');
  } finally {
    failing.server.close();
  }
});

test('caregiver saves, reads and deletes the area used for reminders', async () => {
  const { fetchImpl } = mockFetch();
  const { server, call } = await setup(fetchImpl);
  try {
    const c = (await call('POST', '/children', child)).json;
    const who = { phone: child.phone, childDob: child.dob, childName: child.childName };

    assert.equal((await call('POST', '/caregiver/location', { ...who, childDob: '2026-01-01', lat: 1, lon: 1 }, null)).status, 404);
    assert.equal((await call('POST', '/caregiver/location', { ...who, lat: 'x', lon: 39 }, null)).json.field, 'lat');
    assert.equal((await call('POST', '/caregiver/location', { ...who, lat: -4, lon: 200 }, null)).json.field, 'lon');
    assert.equal((await call('POST', '/caregiver/location', who, null)).status, 400);

    const saved = await call('POST', '/caregiver/location', { ...who, lat: -4.04351, lon: 39.66829, label: 'Mombasa' }, null);
    assert.equal(saved.status, 200);
    assert.equal(saved.json.updated, 1);
    assert.deepEqual(
      { label: saved.json.savedArea.label, lat: saved.json.savedArea.lat, lon: saved.json.savedArea.lon },
      { label: 'Mombasa', lat: -4.044, lon: 39.668 }
    );
    assert.equal(saved.json.savedArea.expiresOn, new Date(Date.now() + 14 * 86400000).toISOString().slice(0, 10));

    const looked = await call('POST', '/caregiver/lookup', who, null);
    assert.equal(looked.json.children[0].savedArea.label, 'Mombasa');
    const seenByWorker = (await call('GET', `/children/${c.id}`)).json;
    assert.equal(seenByWorker.savedArea, undefined);

    const cleared = await call('POST', '/caregiver/location', { ...who, lat: null }, null);
    assert.equal(cleared.json.savedArea, null);
    assert.equal((await call('POST', '/caregiver/lookup', who, null)).json.children[0].savedArea, null);
  } finally {
    server.close();
  }
});

test('reminder SMS lists the 2 nearest hospitals, asks to call first, and hides them from health workers', async () => {
  const { fetchImpl } = mockFetch({ overpass: { 3000: [COAST_GENERAL, AGA_KHAN, PANDYA, ...CLINICS] } });
  const { db, server, call } = await setup(fetchImpl);
  try {
    const c = (await call('POST', '/children', child)).json;
    const area = (await call('POST', '/caregiver/location', { phone: child.phone, childDob: child.dob, childName: child.childName, lat: HOME.lat, lon: HOME.lon, label: 'Mombasa' }, null)).json.savedArea;

    const run = await call('POST', '/reminders/run');
    assert.equal(run.json.sent.length, 1);
    const sms = db.prepare('SELECT message FROM sms_log ORDER BY id DESC').get().message;
    const km = (lat, lon) => (Math.round(haversineKm(area.lat, area.lon, lat, lon) * 10) / 10).toFixed(1);
    assert.match(sms, /^Leo ni siku ya chanjo ya Amani/);
    assert.ok(
      sms.includes(
        `\nHospitali zilizo karibu na ulipo: 1) Aga Khan Hospital, km ${km(-4.0438, 39.6678)}, +254 41 222 7710; 2) Coast General Hospital, km ${km(-4.048, 39.674)}, 041 231 4201.`
      ),
      sms
    );
    assert.match(sms, /Piga simu kuthibitisha kuwa chanjo inapatikana\.$/);
    assert.doesNotMatch(sms, /3\)|Tudor/);

    const history = (await call('GET', `/children/${c.id}`)).json.smsHistory;
    assert.doesNotMatch(history[0].message, /Aga Khan|\n/);

    const manual = await call('POST', `/children/${c.id}/remind`);
    assert.equal(manual.status, 200);
    assert.doesNotMatch(manual.json.message, /Aga Khan/);
    assert.match(db.prepare('SELECT message FROM sms_log ORDER BY id DESC').get().message, /Aga Khan Hospital/);
  } finally {
    server.close();
  }
});

test('reminders still go out when OpenStreetMap fails, without retrying for every child', async () => {
  const down = mockFetch({ overpassStatus: 500, nominatimStatus: 500 });
  const { db, server, call } = await setup(down.fetchImpl);
  try {
    for (const [name, phone] of [['Amani', '0712345678'], ['Baraka', '0799111222']]) {
      await call('POST', '/children', { ...child, childName: name, phone });
      await call('POST', '/caregiver/location', { phone, childDob: child.dob, childName: name, lat: HOME.lat, lon: HOME.lon }, null);
    }
    const run = await call('POST', '/reminders/run');
    assert.equal(run.json.sent.length, 2);
    for (const { message } of db.prepare('SELECT message FROM sms_log').all()) assert.doesNotMatch(message, /\n/);
    assert.equal(down.count('overpass'), SERVERS.length);
    assert.equal(down.count('nominatim'), 1);
  } finally {
    server.close();
  }
});

test('a saved area is deleted after 14 days and no longer used', async () => {
  const { fetchImpl, count } = mockFetch({ overpass: { 3000: [COAST_GENERAL, AGA_KHAN, PANDYA] } });
  const { db, server, call } = await setup(fetchImpl);
  try {
    await call('POST', '/children', child);
    const who = { phone: child.phone, childDob: child.dob, childName: child.childName };
    await call('POST', '/caregiver/location', { ...who, lat: HOME.lat, lon: HOME.lon, label: 'Mombasa' }, null);
    db.exec("UPDATE children SET area_saved_at = datetime('now', '-15 days')");

    assert.equal((await call('POST', '/caregiver/lookup', who, null)).json.children[0].savedArea, null);
    const row = db.prepare('SELECT area_lat, area_lon, area_label, area_saved_at FROM children').get();
    assert.deepEqual({ ...row }, { area_lat: null, area_lon: null, area_label: null, area_saved_at: null });

    await call('POST', '/reminders/run');
    assert.doesNotMatch(db.prepare('SELECT message FROM sms_log').get().message, /\n/);
    assert.equal(count('overpass'), 0);
  } finally {
    server.close();
  }
});

test('nearby lines for the SMS in English and Kiswahili, for hospitals and for clinics', () => {
  const list = [
    { name: 'Aga Khan Hospital', distanceKm: 0.4, phone: '+254412227710' },
    { name: 'Coast General Hospital', distanceKm: 1.25, phone: null },
  ];
  assert.equal(
    nearbyClinicsText('en', list, 'hospital'),
    '\nNearest hospitals to where you are: 1) Aga Khan Hospital, 0.4 km, +254412227710; 2) Coast General Hospital, 1.3 km. Call to confirm that the vaccine is available.'
  );
  assert.equal(
    nearbyClinicsText('sw', [{ name: 'Tudor Clinic', distanceKm: 0.2, phone: null }], 'clinic'),
    '\nKliniki zilizo karibu na ulipo: 1) Tudor Clinic, km 0.2. Piga simu kuthibitisha kuwa chanjo inapatikana.'
  );
  assert.equal(nearbyClinicsText('en', [], 'hospital'), '');
});
