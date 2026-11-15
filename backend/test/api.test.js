import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { DatabaseSync } from 'node:sqlite';
import { openDb } from '../src/db.js';
import { createApp } from '../src/app.js';
import { dueDateFor, planDueDates, SCHEDULE } from '../src/schedule.js';
import { normalizePhone, parseAtResponse, sendSms } from '../src/sms.js';
import { buildMessage, kindFor } from '../src/messages.js';
import { hashPassword, verifyPassword, passwordProblem } from '../src/auth.js';

const CODE = 'test-registration-code';
let today = '2026-10-25';

const worker = {
  name: 'Achieng Otieno',
  phone: '0722000111',
  facility: 'Kangemi Health Centre',
  password: 'secret123',
  registrationCode: CODE,
};

async function setup({ login = true } = {}) {
  const db = openDb(':memory:');
  const server = createApp(db, { registrationCode: CODE, clock: () => today });
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
  if (login) {
    const r = await call('POST', '/auth/register', worker, null);
    assert.equal(r.status, 201);
    token = r.json.token;
  }
  return { db, server, call, token };
}

const child = {
  childName: 'Amani',
  caregiverName: 'Wanjiru',
  phone: '0712345678',
  dob: '2026-09-13',
  language: 'sw',
  facility: 'Kangemi Health Centre',
  consent: true,
};

const byCode = (v, code) => v.doses.find((d) => d.code === code);

test('due date maths', () => {
  const by = (c) => SCHEDULE.find((s) => s.code === c);
  assert.equal(dueDateFor('2026-01-01', by('PENTA1')), '2026-02-12');
  assert.equal(dueDateFor('2026-01-31', by('MR1')), '2026-10-31');
  assert.equal(dueDateFor('2026-05-31', by('VITA')), '2026-11-30');
  assert.equal(dueDateFor('2026-01-01', by('BCG')), '2026-01-01');
});

test('a late dose pushes the rest of its series back', () => {
  const plan = planDueDates('2026-01-01', new Map([['PENTA1', '2026-03-12']]));
  assert.equal(plan.get('PENTA1'), '2026-02-12');
  assert.equal(plan.get('PENTA2'), '2026-04-09');
  assert.equal(plan.get('PENTA3'), '2026-05-07');
  assert.equal(plan.get('OPV2'), '2026-03-12');
  assert.equal(planDueDates('2026-01-01').get('PENTA2'), '2026-03-12');
});

test('phone normalisation', () => {
  assert.equal(normalizePhone('0712 345 678'), '+254712345678');
  assert.equal(normalizePhone('254112345678'), '+254112345678');
  assert.equal(normalizePhone('+254712345678'), '+254712345678');
  assert.equal(normalizePhone('12345'), null);
});

test('messages in both languages and reminder windows', () => {
  assert.match(buildMessage('before', 'en', 'Amani', ['BCG'], '2026-11-01', 'Clinic'), /Reminder: Amani/);
  assert.match(buildMessage('before', 'sw', 'Amani', ['BCG'], '2026-11-01', 'Clinic'), /Kumbusho: Amani/);
  assert.match(buildMessage('followup', 'en', 'Amani', ['BCG'], '2026-11-01', ''), /Second reminder/);
  assert.match(buildMessage('followup', 'sw', 'Amani', ['BCG'], '2026-11-01', ''), /Kumbusho la pili/);
  assert.equal(kindFor(4), null);
  assert.equal(kindFor(3).kind, 'before');
  assert.equal(kindFor(1).kind, 'before');
  assert.equal(kindFor(0).kind, 'due');
  assert.equal(kindFor(-2), null);
  assert.equal(kindFor(-3).kind, 'overdue');
  assert.equal(kindFor(-14).kind, 'followup');
  assert.equal(kindFor(-61), null);
});

test('password hashing and rules', async () => {
  const h = await hashPassword('secret123');
  assert.match(h, /^scrypt\$/);
  assert.equal(await verifyPassword('secret123', h), true);
  assert.equal(await verifyPassword('secret124', h), false);
  assert.equal(await verifyPassword('secret123', 'garbage'), false);
  assert.ok(passwordProblem('abc123'));
  assert.ok(passwordProblem('onlyletters'));
  assert.equal(passwordProblem('secret123'), null);
});

test('health worker sign up, login and logout', async () => {
  const { server, call } = await setup({ login: false });
  try {
    assert.equal((await call('GET', '/children', null, null)).status, 401);
    assert.equal((await call('GET', '/children', null, 'made-up-token')).status, 401);

    let r = await call('POST', '/auth/register', { ...worker, registrationCode: 'wrong' }, null);
    assert.equal(r.status, 403);
    assert.equal(r.json.code, 'bad_registration_code');
    r = await call('POST', '/auth/register', { ...worker, password: 'short' }, null);
    assert.equal(r.status, 400);
    assert.equal(r.json.field, 'password');
    r = await call('POST', '/auth/register', { ...worker, phone: '123' }, null);
    assert.equal(r.json.field, 'phone');

    r = await call('POST', '/auth/register', worker, null);
    assert.equal(r.status, 201);
    assert.equal(r.json.worker.phone, '+254722000111');
    assert.equal(r.json.worker.password_hash, undefined);
    const first = r.json.token;
    assert.equal((await call('POST', '/auth/register', worker, null)).json.code, 'phone_taken');

    r = await call('POST', '/auth/login', { phone: worker.phone, password: 'secret999' }, null);
    assert.equal(r.status, 401);
    assert.equal(r.json.code, 'invalid_credentials');
    r = await call('POST', '/auth/login', { phone: '+254 722 000 111', password: worker.password }, null);
    assert.equal(r.status, 200);
    const second = r.json.token;

    assert.equal((await call('GET', '/auth/me', null, second)).json.worker.name, 'Achieng Otieno');
    assert.equal((await call('POST', '/auth/logout', null, second)).status, 200);
    assert.equal((await call('GET', '/auth/me', null, second)).status, 401);
    assert.equal((await call('GET', '/auth/me', null, first)).status, 200);
  } finally {
    server.close();
  }
});

test('login locks after repeated wrong passwords', async () => {
  const { server, call } = await setup();
  try {
    for (let i = 0; i < 5; i++) {
      assert.equal((await call('POST', '/auth/login', { phone: worker.phone, password: 'wrong1234' }, null)).status, 401);
    }
    const locked = await call('POST', '/auth/login', { phone: worker.phone, password: worker.password }, null);
    assert.equal(locked.status, 429);
    assert.equal(locked.json.code, 'locked');
  } finally {
    server.close();
  }
});

test('expired sessions are rejected', async () => {
  const { db, server, call } = await setup();
  try {
    assert.equal((await call('GET', '/auth/me')).status, 200);
    db.exec("UPDATE sessions SET expires_at = datetime('now', '-1 minute')");
    const r = await call('GET', '/auth/me');
    assert.equal(r.status, 401);
    assert.equal(r.json.code, 'session_expired');
  } finally {
    server.close();
  }
});

test('changing the password logs out other phones', async () => {
  const { server, call, token } = await setup();
  try {
    const other = (await call('POST', '/auth/login', { phone: worker.phone, password: worker.password }, null)).json.token;
    assert.equal((await call('POST', '/auth/password', { currentPassword: 'nope', newPassword: 'newpass123' })).status, 400);
    assert.equal((await call('POST', '/auth/password', { currentPassword: worker.password, newPassword: 'weak' })).status, 400);
    assert.equal((await call('POST', '/auth/password', { currentPassword: worker.password, newPassword: 'newpass123' })).status, 200);
    assert.equal((await call('GET', '/auth/me', null, other)).status, 401);
    assert.equal((await call('GET', '/auth/me', null, token)).status, 200);
    assert.equal((await call('POST', '/auth/login', { phone: worker.phone, password: 'newpass123' }, null)).status, 200);
  } finally {
    server.close();
  }
});

test('child validation and registration', async () => {
  const { server, call } = await setup();
  try {
    const consent = await call('POST', '/children', { ...child, consent: false });
    assert.equal(consent.status, 400);
    assert.equal(consent.json.field, 'consent');
    assert.equal((await call('POST', '/children', { ...child, phone: '123' })).json.field, 'phone');
    assert.equal((await call('POST', '/children', { ...child, dob: '2030-01-01' })).status, 400);
    assert.equal((await call('POST', '/children', { ...child, dob: '2026-02-30' })).status, 400);
    assert.equal((await call('POST', '/children', { ...child, dob: '2020-01-01' })).status, 400);
    assert.equal((await call('POST', '/children', { ...child, childName: '  ' })).json.field, 'childName');

    const ok = await call('POST', '/children', child);
    assert.equal(ok.status, 201);
    assert.equal(ok.json.phone, '+254712345678');
    assert.equal(ok.json.doses.length, SCHEDULE.length);
    assert.equal(ok.json.totalCount, SCHEDULE.length);
    assert.equal(ok.json.consent, true);
    assert.equal(byCode(ok.json, 'BCG').milestone, 'birth');
    assert.equal(byCode(ok.json, 'MR1').milestone, '9m');
    assert.equal((await call('POST', '/children', child)).status, 409);

    const noFacility = await call('POST', '/children', { ...child, childName: 'Baraka', facility: '' });
    assert.equal(noFacility.json.facility, worker.facility);
  } finally {
    server.close();
  }
});

test('dose statuses, mark given and undo', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    assert.equal(byCode(c, 'BCG').status, 'overdue');
    assert.equal(byCode(c, 'PENTA1').dueDate, '2026-10-25');
    assert.equal(byCode(c, 'PENTA1').status, 'due');
    assert.equal(byCode(c, 'PENTA2').status, 'upcoming');

    const given = await call('POST', `/children/${c.id}/doses/BCG/given`, {});
    assert.equal(given.status, 200);
    assert.equal(byCode(given.json, 'BCG').status, 'given');
    assert.equal((await call('POST', `/children/${c.id}/doses/BCG/given`, { date: '2030-01-01' })).status, 400);
    assert.equal((await call('POST', `/children/${c.id}/doses/NOPE/given`, {})).status, 404);

    const undone = await call('POST', `/children/${c.id}/doses/BCG/undo`);
    assert.equal(byCode(undone.json, 'BCG').status, 'overdue');
  } finally {
    server.close();
  }
});

test('giving a dose late moves the next dose in the series', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    assert.equal(byCode(c, 'PENTA2').dueDate, '2026-11-22');
    today = '2026-11-08';
    const late = await call('POST', `/children/${c.id}/doses/PENTA1/given`, {});
    assert.equal(byCode(late.json, 'PENTA2').dueDate, '2026-12-06');
    assert.equal(byCode(late.json, 'PENTA3').dueDate, '2027-01-03');
    assert.equal(byCode(late.json, 'OPV2').dueDate, '2026-11-22');

    const early = await call('POST', `/children/${c.id}/doses/PENTA2/given`, { date: '2026-11-01' });
    assert.equal(early.status, 400);
    assert.match(early.json.error, /before Pentavalent 1/);

    const undone = await call('POST', `/children/${c.id}/doses/PENTA1/undo`);
    assert.equal(byCode(undone.json, 'PENTA2').dueDate, '2026-11-22');
  } finally {
    today = '2026-10-25';
    server.close();
  }
});

test('record several past doses at once', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    const r = await call('POST', `/children/${c.id}/doses`, {
      given: [
        { code: 'OPV0', date: '2026-09-14' },
        { code: 'BCG', date: '2026-09-13' },
      ],
    });
    assert.equal(r.status, 200);
    assert.equal(byCode(r.json, 'BCG').givenDate, '2026-09-13');
    assert.equal(byCode(r.json, 'OPV0').givenDate, '2026-09-14');
    assert.equal(r.json.overdueCount, 0);
    assert.equal(r.json.givenCount, 2);

    const bad = await call('POST', `/children/${c.id}/doses`, {
      given: [
        { code: 'OPV1', date: '2026-10-20' },
        { code: 'PENTA1', date: '2030-01-01' },
      ],
    });
    assert.equal(bad.status, 400);
    const after = (await call('GET', `/children/${c.id}`)).json;
    assert.equal(byCode(after, 'OPV1').givenDate, null);
    assert.equal((await call('POST', `/children/${c.id}/doses`, { given: [{ code: 'NOPE' }] })).status, 404);
    assert.equal((await call('POST', `/children/${c.id}/doses`, { given: [] })).status, 400);
  } finally {
    server.close();
  }
});

test('caregiver lookup needs matching phone and date of birth', async () => {
  const { server, call } = await setup();
  try {
    await call('POST', '/children', child);
    const miss = await call('POST', '/caregiver/lookup', { phone: '0712345678', childDob: '2026-01-01' }, null);
    assert.equal(miss.status, 404);
    assert.equal((await call('POST', '/caregiver/lookup', { phone: '07', childDob: '2026-09-13' }, null)).json.field, 'phone');
    const hit = await call('POST', '/caregiver/lookup', { phone: '+254712345678', childDob: '2026-09-13' }, null);
    assert.equal(hit.status, 200);
    assert.equal(hit.json.children[0].childName, 'Amani');
    assert.equal(hit.json.children[0].smsHistory, undefined);
  } finally {
    server.close();
  }
});

test('caregiver lookup slows down guessing for one phone number', async () => {
  const { server, call } = await setup();
  try {
    await call('POST', '/children', child);
    for (let i = 1; i <= 10; i++) {
      const dob = `2026-01-${String(i).padStart(2, '0')}`;
      assert.equal((await call('POST', '/caregiver/lookup', { phone: child.phone, childDob: dob }, null)).status, 404);
    }
    const blocked = await call('POST', '/caregiver/lookup', { phone: child.phone, childDob: child.dob }, null);
    assert.equal(blocked.status, 429);
  } finally {
    server.close();
  }
});

test('search by name or local phone number, and filters', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    await call('POST', '/children', { ...child, childName: 'Baraka', phone: '0799111222', dob: '2026-10-20' });
    const names = async (q) => (await call('GET', '/children' + q)).json.children.map((x) => x.childName);

    assert.deepEqual(await names('?q=0712 345'), ['Amani']);
    assert.deepEqual(await names('?q=254799'), ['Baraka']);
    assert.deepEqual(await names('?q=wanj'), ['Baraka', 'Amani']);
    assert.deepEqual(await names('?q=zzz'), []);
    assert.deepEqual(await names('?q=%25'), []);
    assert.deepEqual(await names('?filter=overdue'), ['Amani', 'Baraka']);

    await call('POST', `/children/${c.id}/doses`, { given: [{ code: 'BCG' }, { code: 'OPV0' }] });
    assert.deepEqual(await names('?filter=overdue'), ['Baraka']);
    assert.deepEqual(await names('?filter=due'), ['Amani']);
    const list = (await call('GET', '/children')).json.children;
    assert.equal(list.find((x) => x.id === c.id).givenCount, 2);
  } finally {
    server.close();
  }
});

test('edit details, withdraw consent and delete a child', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    let r = await call('PATCH', `/children/${c.id}`, { childName: 'Amani Otieno', language: 'en' });
    assert.equal(r.status, 200);
    assert.equal(r.json.childName, 'Amani Otieno');
    assert.equal(r.json.language, 'en');
    assert.equal((await call('PATCH', `/children/${c.id}`, { phone: '123' })).json.field, 'phone');
    assert.equal((await call('PATCH', `/children/${c.id}`, {})).status, 400);

    r = await call('PATCH', `/children/${c.id}`, { dob: '2026-09-20' });
    assert.equal(byCode(r.json, 'PENTA1').dueDate, '2026-11-01');
    await call('POST', `/children/${c.id}/doses/BCG/given`, { date: '2026-09-21' });
    assert.equal((await call('PATCH', `/children/${c.id}`, { dob: '2026-09-25' })).json.field, 'dob');

    r = await call('PATCH', `/children/${c.id}`, { consent: false });
    assert.equal(r.json.consent, false);
    assert.equal((await call('POST', '/reminders/run')).json.sent.length, 0);
    assert.equal((await call('POST', `/children/${c.id}/remind`)).json.code, 'no_consent');

    assert.equal((await call('DELETE', `/children/${c.id}`)).status, 200);
    assert.equal((await call('GET', `/children/${c.id}`)).status, 404);
  } finally {
    server.close();
  }
});

test('POST with a method override header works like PATCH (Android cannot send PATCH)', async () => {
  const { server, token } = await setup();
  try {
    const base = `http://127.0.0.1:${server.address().port}`;
    const headers = { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` };
    const c = await (await fetch(base + '/children', { method: 'POST', headers, body: JSON.stringify(child) })).json();
    const res = await fetch(base + `/children/${c.id}`, {
      method: 'POST',
      headers: { ...headers, 'X-HTTP-Method-Override': 'PATCH' },
      body: JSON.stringify({ caregiverName: 'Mama Amani' }),
    });
    assert.equal(res.status, 200);
    assert.equal((await res.json()).caregiverName, 'Mama Amani');
  } finally {
    server.close();
  }
});

test('missed and due lists', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    const missed = await call('GET', '/missed');
    assert.equal(missed.json.children.length, 1);
    assert.equal(missed.json.children[0].missedCount, 2);
    assert.equal(missed.json.children[0].daysOverdue, 42);
    assert.equal(missed.json.children[0].consent, true);

    await call('POST', `/children/${c.id}/doses/BCG/given`, {});
    await call('POST', `/children/${c.id}/doses/OPV0/given`, {});
    assert.equal((await call('GET', '/missed')).json.children.length, 0);

    const near = await call('GET', '/due?days=14');
    assert.ok(near.json.items.some((i) => i.dueDate === '2026-10-25'));
    assert.ok(!near.json.items.some((i) => i.dueDate === '2026-11-22'));
    const wider = await call('GET', '/due?days=30');
    assert.ok(wider.json.items.some((i) => i.dueDate === '2026-11-22'));
  } finally {
    server.close();
  }
});

test('reminders run once per milestone, one SMS per child per day, and skip given doses', async () => {
  const { db, server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    today = '2026-10-25';
    const first = await call('POST', '/reminders/run');
    assert.equal(first.json.sent.length, 1);
    assert.equal(first.json.sent[0].kind, 'due');
    assert.equal(first.json.sent[0].status, 'dry-run');
    assert.equal(first.json.smsMode, 'test');
    assert.equal((await call('POST', '/reminders/run')).json.sent.length, 0);

    const log = db.prepare('SELECT message, child_id, kind FROM sms_log').all();
    assert.equal(log.length, 1);
    assert.match(log[0].message, /Leo ni siku ya chanjo ya Amani/);
    assert.match(log[0].message, /Pentavalent 1/);
    assert.equal(log[0].child_id, c.id);
    assert.equal(log[0].kind, 'due');

    today = '2026-10-28';
    for (const code of ['PENTA1', 'OPV1', 'PCV1']) await call('POST', `/children/${c.id}/doses/${code}/given`, {});
    const overdue = await call('POST', '/reminders/run');
    assert.equal(overdue.json.sent.length, 1);
    assert.equal(overdue.json.sent[0].kind, 'overdue');
    const last = db.prepare('SELECT message FROM sms_log ORDER BY id DESC').get().message;
    assert.match(last, /Rotavirus 1/);
    assert.doesNotMatch(last, /Pentavalent 1/);

    today = '2026-11-19';
    const before = await call('POST', '/reminders/run');
    assert.equal(before.json.sent.length, 1);
    assert.equal(before.json.sent[0].kind, 'before');
    assert.equal(before.json.sent[0].dueDate, '2026-11-22');

    const manual = await call('POST', `/children/${c.id}/remind`);
    assert.equal(manual.status, 200);
    assert.equal(manual.json.kind, 'overdue');
    assert.equal(manual.json.smsStatus, 'dry-run');
    const again = await call('POST', `/children/${c.id}/remind`);
    assert.equal(again.status, 429);
    assert.equal(again.json.code, 'recently_reminded');

    const detail = (await call('GET', `/children/${c.id}`)).json;
    assert.equal(detail.smsHistory.length, 4);
    assert.equal(detail.smsHistory[0].kind, 'manual');
  } finally {
    today = '2026-10-25';
    server.close();
  }
});

test('a reminder missed while the server was off is sent on the next run', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    await call('POST', `/children/${c.id}/doses`, {
      given: ['BCG', 'OPV0'].map((code) => ({ code, date: '2026-09-13' })).concat(
        ['OPV1', 'PENTA1', 'PCV1', 'ROTA1'].map((code) => ({ code }))
      ),
    });
    // The "before" reminder for 22 Nov was due on the 19th; the server only comes back on the 21st.
    today = '2026-11-21';
    const late = (await call('POST', '/reminders/run')).json.sent;
    assert.deepEqual(late.map((s) => [s.kind, s.dueDate]), [['before', '2026-11-22']]);
    today = '2026-11-22';
    assert.equal((await call('POST', '/reminders/run')).json.sent[0].kind, 'due');
    today = '2026-11-23';
    assert.equal((await call('POST', '/reminders/run')).json.sent.length, 0);
    today = '2026-11-25';
    assert.equal((await call('POST', '/reminders/run')).json.sent[0].kind, 'overdue');
  } finally {
    today = '2026-10-25';
    server.close();
  }
});

test('children with no consent are never messaged', async () => {
  const { server, call } = await setup();
  try {
    const c = (await call('POST', '/children', child)).json;
    await call('PATCH', `/children/${c.id}`, { consent: false });
    assert.equal((await call('POST', '/reminders/run')).json.sent.length, 0);
  } finally {
    server.close();
  }
});

test('dashboard stats', async () => {
  const { server, call } = await setup();
  try {
    await call('POST', '/children', child);
    let s = (await call('GET', '/stats')).json;
    assert.equal(s.children, 1);
    assert.equal(s.overdueChildren, 1);
    assert.equal(s.dueToday, 1);
    assert.equal(s.dueThisWeek, 1);
    assert.equal(s.smsLast7Days, 0);
    await call('POST', '/reminders/run');
    s = (await call('GET', '/stats')).json;
    assert.equal(s.smsLast7Days, 1);
  } finally {
    server.close();
  }
});

test('opens a database made by the first version and upgrades it', () => {
  const file = path.join(os.tmpdir(), `vaccine-upgrade-${process.pid}-${Date.now()}.db`);
  const old = new DatabaseSync(file);
  old.exec(`
    CREATE TABLE children (id INTEGER PRIMARY KEY AUTOINCREMENT, child_name TEXT NOT NULL, caregiver_name TEXT NOT NULL,
      phone TEXT NOT NULL, dob TEXT NOT NULL, language TEXT NOT NULL DEFAULT 'en', facility TEXT NOT NULL DEFAULT '',
      consent INTEGER NOT NULL DEFAULT 1, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    CREATE TABLE doses (id INTEGER PRIMARY KEY AUTOINCREMENT, child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      code TEXT NOT NULL, name TEXT NOT NULL, due_date TEXT NOT NULL, given_date TEXT, UNIQUE(child_id, code));
    CREATE TABLE reminders (id INTEGER PRIMARY KEY AUTOINCREMENT, child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      due_date TEXT NOT NULL, kind TEXT NOT NULL, status TEXT NOT NULL, sent_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(child_id, due_date, kind));
    CREATE TABLE sms_log (id INTEGER PRIMARY KEY AUTOINCREMENT, phone TEXT NOT NULL, message TEXT NOT NULL, status TEXT NOT NULL,
      response TEXT, created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
    INSERT INTO children (child_name, caregiver_name, phone, dob) VALUES ('Old', 'Parent', '+254712345678', '2026-01-01');
  `);
  const ins = old.prepare('INSERT INTO doses (child_id, code, name, due_date, given_date) VALUES (1, ?, ?, ?, ?)');
  for (const item of SCHEDULE) {
    ins.run(item.code, item.name, dueDateFor('2026-01-01', item), item.code === 'PENTA1' ? '2026-03-12' : null);
  }
  old.close();

  const db = openDb(file);
  try {
    const cols = (t) => db.prepare(`PRAGMA table_info(${t})`).all().map((c) => c.name);
    assert.ok(cols('children').includes('registered_by'));
    assert.ok(cols('sms_log').includes('child_id'));
    assert.ok(cols('reminders').includes('run_date'));
    assert.ok(cols('workers').includes('password_hash'));
    assert.ok(['area_lat', 'area_lon', 'area_label', 'area_saved_at'].every((c) => cols('children').includes(c)));
    assert.equal(db.prepare('PRAGMA user_version').get().user_version, 2);
    assert.equal(db.prepare("SELECT due_date FROM doses WHERE code = 'PENTA2'").get().due_date, '2026-04-09');
    assert.equal(db.prepare('SELECT child_name FROM children').get().child_name, 'Old');
  } finally {
    db.close();
    fs.rmSync(file, { force: true });
  }
});

test("Africa's Talking replies are checked per recipient", async () => {
  const success = JSON.stringify({ SMSMessageData: { Recipients: [{ status: 'Success', statusCode: 101 }] } });
  const rejected = JSON.stringify({ SMSMessageData: { Recipients: [{ status: 'InvalidPhoneNumber', statusCode: 403 }] } });
  assert.equal(parseAtResponse(true, success), 'sent');
  assert.equal(parseAtResponse(true, rejected), 'failed');
  assert.equal(parseAtResponse(true, '{"SMSMessageData":{"Recipients":[]}}'), 'failed');
  assert.equal(parseAtResponse(false, success), 'failed');
  assert.equal(parseAtResponse(true, 'not json'), 'failed');

  const db = openDb(':memory:');
  const saved = { user: process.env.AT_USERNAME, key: process.env.AT_API_KEY, fetch: globalThis.fetch };
  const restore = (name, value) => (value === undefined ? delete process.env[name] : (process.env[name] = value));
  let seen;
  process.env.AT_USERNAME = 'sandbox';
  process.env.AT_API_KEY = 'k';
  globalThis.fetch = async (url, opts) => {
    seen = { url, body: opts.body };
    return new Response(success, { status: 201 });
  };
  try {
    const out = await sendSms(db, '+254712345678', 'Hello', { kind: 'manual' });
    assert.equal(out.status, 'sent');
    assert.match(seen.url, /api\.sandbox\.africastalking\.com/);
    assert.match(seen.body, /to=%2B254712345678/);
    assert.equal(db.prepare('SELECT status, kind FROM sms_log').get().status, 'sent');
  } finally {
    restore('AT_USERNAME', saved.user);
    restore('AT_API_KEY', saved.key);
    globalThis.fetch = saved.fetch;
    db.close();
  }
});
