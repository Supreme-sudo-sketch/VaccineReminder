import test from 'node:test';
import assert from 'node:assert/strict';
import { openDb } from '../src/db.js';
import { createApp } from '../src/app.js';
import { SCHEDULE } from '../src/schedule.js';

const CODE = 'test-registration-code';
const today = '2026-10-25';
const worker = { name: 'Achieng Otieno', phone: '0722000111', facility: 'Kangemi Health Centre', password: 'secret123', registrationCode: CODE };
const child = { childName: 'Amani', caregiverName: 'Wanjiru', phone: '0712345678', dob: '2026-09-13', language: 'sw', facility: 'Riruta Health Centre', consent: true };
const caregiver = { phone: '0712 345 678', childDob: child.dob, childName: child.childName };

async function setup() {
  const db = openDb(':memory:');
  const server = createApp(db, { registrationCode: CODE, clock: () => today, facilityList: [] });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, p, body, auth) => {
    const res = await fetch(base + p, {
      method,
      headers: { 'Content-Type': 'application/json', ...(auth ? { Authorization: `Bearer ${auth}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, json: await res.json() };
  };
  const token = (await call('POST', '/auth/register', worker)).json.token;
  const amani = (await call('POST', '/children', child, token)).json;
  const close = () => new Promise((r) => server.close(r)).then(() => db.close());
  return { db, call, token, amani, close };
}

test('a health worker sends a notification at any time, with the usual reminder or their own words', async () => {
  const { call, token, amani, close } = await setup();
  try {
    assert.equal((await call('POST', `/children/${amani.id}/notify`, {})).status, 401, 'health workers only');

    // Nothing typed: the same reminder the SMS button sends, in the child's language (Kiswahili).
    const usual = await call('POST', `/children/${amani.id}/notify`, {}, token);
    assert.equal(usual.status, 200);
    assert.match(usual.json.notification.message, /^Amani amekosa chanjo ya /);
    assert.equal(usual.json.notification.deliveredAt, null);
    assert.equal(usual.json.notification.sentBy, 'Achieng Otieno');
    assert.equal(usual.json.appSeenAt, null, 'no phone has checked in for this child yet');

    // Tapping Send twice does not send it twice, but a different message goes straight away.
    const again = await call('POST', `/children/${amani.id}/notify`, {}, token);
    assert.equal(again.status, 429);
    assert.equal(again.json.code, 'recently_notified');
    const own = await call('POST', `/children/${amani.id}/notify`, { message: '  The clinic is open on Saturday 9-12.  ' }, token);
    assert.equal(own.status, 200);
    assert.equal(own.json.notification.message, 'The clinic is open on Saturday 9-12.');
    assert.equal((await call('POST', `/children/${amani.id}/notify`, { message: 'x'.repeat(600) }, token)).json.notification.message.length, 500);

    // The child's record lists them, newest first, with no SMS limit applying.
    const view = (await call('GET', `/children/${amani.id}`, null, token)).json;
    assert.equal(view.appNotifications.length, 3);
    assert.equal(view.appNotifications[1].message, 'The clinic is open on Saturday 9-12.');
    assert.equal(view.appSeenAt, null);
    assert.equal((await call('POST', `/children/${amani.id}/remind`, null, token)).status, 200, 'SMS is separate');
    assert.equal((await call('POST', '/children/999/notify', {}, token)).status, 404);
  } finally {
    await close();
  }
});

test("the caregiver's app collects new notifications once, and the health worker sees them delivered", async () => {
  const { db, call, token, amani, close } = await setup();
  try {
    const other = (await call('POST', '/children', { ...child, childName: 'Baraka', phone: '0799000111' }, token)).json;
    await call('POST', `/children/${amani.id}/notify`, { message: 'Bring the immunisation card.' }, token);
    await call('POST', `/children/${other.id}/notify`, { message: 'For another family.' }, token);

    assert.equal((await call('POST', '/caregiver/notifications', { ...caregiver, childDob: '2026-01-01' })).status, 404);

    const first = await call('POST', '/caregiver/notifications', caregiver);
    assert.equal(first.status, 200);
    assert.deepEqual(
      first.json.notifications.map(({ childName, message, from }) => ({ childName, message, from })),
      [{ childName: 'Amani', message: 'Bring the immunisation card.', from: 'Kangemi Health Centre' }],
      "only this caregiver's child, from the sending health worker's facility"
    );
    const lastId = first.json.notifications[0].id;
    assert.deepEqual((await call('POST', '/caregiver/notifications', { ...caregiver, after: lastId })).json.notifications, []);

    const view = (await call('GET', `/children/${amani.id}`, null, token)).json;
    assert.ok(view.appNotifications[0].deliveredAt, 'marked delivered');
    assert.ok(view.appSeenAt, 'the app has checked in');
    assert.equal((await call('GET', `/children/${other.id}`, null, token)).json.appNotifications[0].deliveredAt, null);

    // A phone that was off for more than a week only gets the recent ones.
    await call('POST', `/children/${amani.id}/notify`, { message: 'Old news' }, token);
    db.prepare("UPDATE app_notifications SET created_at = datetime('now', '-8 days') WHERE message = 'Old news'").run();
    await call('POST', `/children/${amani.id}/notify`, { message: 'Fresh news' }, token);
    const later = await call('POST', '/caregiver/notifications', { ...caregiver, after: lastId });
    assert.deepEqual(later.json.notifications.map((n) => n.message), ['Fresh news']);
  } finally {
    await close();
  }
});

test('with every vaccine given, a notification needs a message', async () => {
  const { call, token, amani, close } = await setup();
  try {
    const given = SCHEDULE.map((s) => ({ code: s.code, date: today }));
    assert.equal((await call('POST', `/children/${amani.id}/doses`, { given }, token)).status, 200);
    const empty = await call('POST', `/children/${amani.id}/notify`, {}, token);
    assert.equal(empty.status, 409);
    assert.equal(empty.json.field, 'message');
    assert.equal((await call('POST', `/children/${amani.id}/notify`, { message: 'Well done, all vaccines given!' }, token)).status, 200);
  } finally {
    await close();
  }
});
