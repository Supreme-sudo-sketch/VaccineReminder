import test from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db.js';
import { createApp } from '../src/app.js';
import { createFacilityIndex, loadFacilityList, MAX_RESULTS } from '../src/facilityList.js';

const LIST = [
  { name: 'Kenyatta National Hospital', type: 'hospital', county: 'Nairobi' },
  { name: 'Riruta Health Centre', type: 'clinic', county: 'Nairobi' },
  { name: 'Riruta Health Centre', type: 'hospital', county: 'Nairobi' },
  { name: 'St. Mary\'s Mission Hospital', type: 'hospital', county: 'Nakuru' },
  { name: 'St. Mary\'s Mission Hospital', type: 'hospital', county: 'Nairobi' },
  { name: 'Coast General Teaching & Referral Hospital', type: 'hospital', county: 'Mombasa' },
  { name: 'Majengo Health Centre', type: 'health_centre', county: 'Mombasa' },
  { name: 'Majengo Dispensary', type: 'clinic', county: 'Mombasa' },
  { name: 'Kenya Medical Clinic', type: 'clinic', county: 'Kisumu' },
];

const names = (r) => r.facilities.map((f) => `${f.name} (${f.county ?? '-'})`);

test('facility search matches the start of each word typed, in the name or the county', () => {
  const index = createFacilityIndex(LIST);
  assert.equal(index.size, LIST.length - 1, 'same name in the same county is listed once');

  assert.deepEqual(names(index.search('ken nat')), ['Kenyatta National Hospital (Nairobi)']);
  assert.deepEqual(names(index.search('riruta')), ['Riruta Health Centre (Nairobi)']);
  assert.deepEqual(names(index.search('coast general and')), ['Coast General Teaching & Referral Hospital (Mombasa)'], '"&" is read as "and"');
  assert.deepEqual(names(index.search('st marys')), ["St. Mary's Mission Hospital (Nairobi)", "St. Mary's Mission Hospital (Nakuru)"], 'apostrophes and dots can be left out');
  assert.deepEqual(names(index.search("st. mary's nakuru")), ["St. Mary's Mission Hospital (Nakuru)"], 'the county narrows it down');
  assert.deepEqual(names(index.search('ital')), [], 'only the start of a word matches');
  assert.deepEqual(names(index.search('   ')), []);

  // Names starting with what was typed first, then hospitals before health centres and clinics.
  assert.deepEqual(names(index.search('ken')), ['Kenyatta National Hospital (Nairobi)', 'Kenya Medical Clinic (Kisumu)']);
  assert.deepEqual(names(index.search('majengo')), ['Majengo Health Centre (Mombasa)', 'Majengo Dispensary (Mombasa)']);
  assert.deepEqual(names(index.search('mombasa')), ['Coast General Teaching & Referral Hospital (Mombasa)', 'Majengo Health Centre (Mombasa)', 'Majengo Dispensary (Mombasa)']);
});

test('facility names already used in the system can be chosen too', () => {
  const index = createFacilityIndex(LIST);
  const r = index.search('kangemi', ['Kangemi Health Centre', 'kangemi health centre', 'Riruta Health Centre']);
  assert.deepEqual(r.facilities, [{ name: 'Kangemi Health Centre', type: 'in_use', county: null }]);
  assert.deepEqual(names(index.search('riruta', ['Riruta Health Centre'])), ['Riruta Health Centre (Nairobi)'], 'not listed twice');
});

test('at most 30 results, and "more" says when there are others', () => {
  const many = Array.from({ length: MAX_RESULTS + 5 }, (_, i) => ({ name: `Clinic ${i}`, type: 'clinic', county: 'Kitui' }));
  const r = createFacilityIndex(many).search('clinic');
  assert.equal(r.facilities.length, MAX_RESULTS);
  assert.equal(r.more, true);
  assert.equal(createFacilityIndex(many).search('clinic 3').more, false);
});

test('the saved OpenStreetMap list loads, and every entry has a name, type and county', () => {
  const list = loadFacilityList();
  assert.ok(list.length > 1000, `only ${list.length} facilities`);
  for (const f of list) {
    assert.ok(f.name && f.county, JSON.stringify(f));
    assert.ok(['hospital', 'health_centre', 'clinic'].includes(f.type), JSON.stringify(f));
  }
  assert.ok(list.some((f) => f.name === 'Kenyatta National Hospital' && f.county === 'Nairobi'));
});

test('GET /facilities/list searches without logging in and includes facilities already in use', async () => {
  const db = openDb(':memory:');
  db.prepare("INSERT INTO workers (name, phone, facility, password_hash) VALUES ('A', '+254700000001', 'Kangemi Health Centre', 'x')").run();
  const server = createApp(db, { registrationCode: 'code', facilityList: LIST });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  try {
    let res = await fetch(`${base}/facilities/list?q=${encodeURIComponent('k')}`);
    assert.equal(res.status, 200);
    const body = await res.json();
    // Facilities from OpenStreetMap come before names only known from this system.
    assert.deepEqual(names(body), ['Kenyatta National Hospital (Nairobi)', 'Kenya Medical Clinic (Kisumu)', 'Kangemi Health Centre (-)']);
    assert.equal(body.more, false);
    assert.match(body.attribution, /OpenStreetMap/);

    res = await fetch(`${base}/facilities/list`);
    assert.deepEqual((await res.json()).facilities, [], 'nothing typed, nothing listed');
  } finally {
    await new Promise((r) => server.close(r));
    db.close();
  }
});
