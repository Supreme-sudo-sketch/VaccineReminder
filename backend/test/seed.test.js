import test from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db.js';
import { createApp } from '../src/app.js';
import { addDays } from '../src/schedule.js';
import { seedDemo, DEMO_PASSWORD, DEMO_WORKERS, DEMO_CHILDREN } from '../src/seed.js';

const today = '2026-10-25';
const osm = {
  nearbyFacilities: async () => ({
    facilities: [
      { name: 'Tudor Clinic', distanceKm: 0.4, phone: '+254712000001' },
      { name: 'Coast General Hospital', distanceKm: 1.3, phone: null },
    ],
    radiusKm: 3,
  }),
};

test('demo data shows each situation through the real API', async () => {
  const db = openDb(':memory:');
  await seedDemo(db, today);
  const server = createApp(db, { registrationCode: 'unused', clock: () => today, osm });
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
  try {
    for (const w of DEMO_WORKERS) {
      assert.equal((await call('POST', '/auth/login', { phone: w.phone, password: DEMO_PASSWORD }, null)).status, 200);
    }
    token = (await call('POST', '/auth/login', { phone: DEMO_WORKERS[0].phone, password: DEMO_PASSWORD }, null)).json.token;

    const list = (await call('GET', '/children')).json.children;
    assert.equal(list.length, DEMO_CHILDREN.length);
    const byName = Object.fromEntries(list.map((c) => [c.childName.split(' ')[0], c]));
    const detail = async (name) => (await call('GET', `/children/${byName[name].id}`)).json;
    const dose = (child, code) => child.doses.find((d) => d.code === code);

    assert.equal(dose(await detail('Neema'), 'BCG').status, 'given');
    assert.equal(byName.Neema.nextDueDate, addDays(today, 39));
    assert.equal(dose(await detail('Amani'), 'PENTA1').status, 'due');
    assert.equal(byName.Baraka.nextDueDate, addDays(today, 3));
    assert.equal(byName.Zawadi.overdueCount, 4);
    assert.equal((await detail('Zawadi')).smsHistory.length, 2);
    assert.equal(byName.Imani.overdueCount, 6);
    const jabari = await detail('Jabari');
    assert.equal(dose(jabari, 'PENTA3').dueDate, addDays(today, 3));
    assert.equal(dose(jabari, 'IPV').status, 'overdue');
    const wanjiku = await detail('Wanjiku');
    assert.equal(wanjiku.overdueCount, 0);
    assert.deepEqual(wanjiku.nextVaccines, ['Measles-Rubella 2']);
    assert.equal(byName.Otieno.consent, false);
    assert.equal(byName.Otieno.overdueCount, 4);
    assert.equal(byName.Malaika.nextDueDate, null);

    const kioko = DEMO_CHILDREN.find((c) => c.childName.startsWith('Kioko'));
    const twins = DEMO_CHILDREN.find((c) => c.childName.startsWith('Faith'));
    const lookup = async (c) => (await call('POST', '/caregiver/lookup', { phone: c.phone, childDob: addDays(today, -c.ageDays), childName: c.childName.split(' ')[0] }, null)).json;
    // Twins share a phone and date of birth; each is found by their own name.
    assert.deepEqual((await lookup(twins)).children.map((c) => c.childName), [twins.childName]);
    assert.equal((await lookup(kioko)).children[0].savedArea.label, 'Mombasa');

    const missed = (await call('GET', '/missed')).json.children.map((c) => c.childName.split(' ')[0]);
    assert.deepEqual(missed.sort(), ['Imani', 'Jabari', 'Liam', 'Otieno', 'Zawadi']);

    const sent = (await call('POST', '/reminders/run')).json.sent;
    const kinds = Object.fromEntries(sent.map((r) => [r.childName.split(' ')[0], r.kind]));
    assert.deepEqual(kinds, {
      Amani: 'due',
      Baraka: 'before',
      Zawadi: 'overdue',
      Imani: 'followup',
      Jabari: 'before',
      Faith: 'due',
      Hope: 'due',
      Kioko: 'before',
      Tumaini: 'due',
      Zuri: 'due',
      Liam: 'overdue',
    });
    const kiokoSms = db.prepare('SELECT message FROM sms_log WHERE child_id = ? ORDER BY id DESC').get(byName.Kioko.id).message;
    assert.match(kiokoSms, /Tudor Clinic/);
    assert.match(kiokoSms, /Piga simu kuthibitisha kuwa chanjo inapatikana/);
  } finally {
    server.close();
  }
});
