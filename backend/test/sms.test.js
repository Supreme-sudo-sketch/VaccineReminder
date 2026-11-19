import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { openDb } from '../src/db.js';
import { autoReminders, checkAccount, deliverSms, parseBalance, parseTextbeeResponse, parseTwilioResponse, refreshQueuedSms, sendSms, smsError, smsOnlyTo, smsProvider, textbeeBatchId, textbeeTiming } from '../src/sms.js';
import { DEMO_CHILDREN, DEMO_WORKERS, hasDemoData, markDemoChildren, seedDemo } from '../src/seed.js';
import { runReminders } from '../src/app.js';

const BACKEND = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');

// Runs fn with these SMS settings and a fake fetch, then puts everything back.
async function withAt(env, fetchImpl, fn) {
  const names = ['AT_USERNAME', 'AT_API_KEY', 'AT_SENDER_ID', 'SMS_PROVIDER', 'TWILIO_ACCOUNT_SID', 'TWILIO_AUTH_TOKEN', 'TWILIO_FROM', 'TWILIO_MESSAGING_SERVICE_SID', 'TEXTBEE_API_KEY', 'TEXTBEE_DEVICE_ID', 'TEXTBEE_BASE_URL', 'SMS_ONLY_TO', 'AUTO_REMINDERS'];
  const saved = Object.fromEntries(names.map((n) => [n, process.env[n]]));
  const savedFetch = globalThis.fetch;
  for (const n of names) delete process.env[n];
  Object.assign(process.env, env);
  if (fetchImpl) globalThis.fetch = fetchImpl;
  try {
    return await fn();
  } finally {
    for (const n of names) (saved[n] === undefined ? delete process.env[n] : (process.env[n] = saved[n]));
    globalThis.fetch = savedFetch;
  }
}

test('without keys nothing is sent and the check says keys are missing', async () => {
  await withAt({}, async () => assert.fail('no request expected'), async () => {
    assert.equal((await deliverSms('+254712345678', 'Hi')).status, 'dry-run');
    assert.deepEqual(await checkAccount(), { ok: false, problem: 'missing_keys' });
  });
});

test("the key check reads the balance, or says Africa's Talking refused the key", async () => {
  assert.equal(parseBalance('{"UserData":{"balance":"KES 120.50"}}'), 'KES 120.50');
  assert.equal(parseBalance('{"UserData":{}}'), null);
  assert.equal(parseBalance('Unauthorized'), null);

  let seen;
  const ok = async (url, opts) => {
    seen = { url: String(url), key: opts.headers.apiKey };
    return new Response('{"UserData":{"balance":"KES 120.50"}}', { status: 200 });
  };
  await withAt({ AT_USERNAME: 'clinic-app', AT_API_KEY: 'live-key' }, ok, async () => {
    assert.deepEqual(await checkAccount(), { ok: true, balance: 'KES 120.50' });
    assert.equal(seen.url, 'https://api.africastalking.com/version1/user?username=clinic-app');
    assert.equal(seen.key, 'live-key');
  });
  await withAt({ AT_USERNAME: 'sandbox', AT_API_KEY: 'k' }, ok, async () => {
    await checkAccount();
    assert.match(seen.url, /^https:\/\/api\.sandbox\.africastalking\.com\//);
  });
  const refused = async () => new Response('The supplied authentication is invalid', { status: 401 });
  await withAt({ AT_USERNAME: 'clinic-app', AT_API_KEY: 'wrong' }, refused, async () => {
    assert.equal((await checkAccount()).problem, 'bad_keys');
  });
  const offline = async () => {
    throw new TypeError('fetch failed');
  };
  await withAt({ AT_USERNAME: 'clinic-app', AT_API_KEY: 'k' }, offline, async () => {
    assert.equal((await checkAccount()).problem, 'unreachable');
  });
});

test('the demo data is recognised', async () => {
  const db = openDb(':memory:');
  assert.equal(hasDemoData(db), false);
  await seedDemo(db, '2026-10-25');
  assert.equal(hasDemoData(db), true);
  db.close();
});

test('the server starts with live SMS keys on the demo data, and in the sandbox', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vaccine-sms-'));
  const dbPath = path.join(dir, 'demo.db');
  const db = openDb(dbPath);
  await seedDemo(db, '2026-10-25');
  db.close();
  const start = (env) =>
    spawnSync(process.execPath, ['src/index.js'], {
      cwd: BACKEND,
      env: { ...process.env, DB_PATH: dbPath, DOCUMENT_DIR: path.join(path.dirname(dbPath), 'documents'), DOCUMENT_KEY_FILE: path.join(path.dirname(dbPath), 'document.key'), PORT: '0', SMS_PROVIDER: '', TWILIO_ACCOUNT_SID: '', TWILIO_AUTH_TOKEN: '', AT_USERNAME: '', AT_API_KEY: '', AT_SENDER_ID: '', TEXTBEE_API_KEY: '', SMS_ONLY_TO: '', ...env },
      encoding: 'utf8',
      timeout: 4000,
    });
  try {
    // Demo children never get real SMS, so live keys are safe with the demo data loaded.
    const live = start({ AT_USERNAME: 'clinic-app', AT_API_KEY: 'live-key' });
    assert.match(live.stdout, /SMS: live, sent through Africa's Talking/);
    assert.match(live.stdout, new RegExp(`${DEMO_CHILDREN.length} demo children never get real SMS`));
    assert.match(live.stdout, /Vaccine reminder API on port/);

    // The sandbox only reaches the simulator, so it starts (and is stopped by the timeout).
    const sandbox = start({ AT_USERNAME: 'sandbox', AT_API_KEY: 'k' });
    assert.match(sandbox.stdout, /SMS: Africa's Talking sandbox/);
    assert.match(sandbox.stdout, /Vaccine reminder API on port/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('the provider is whichever has keys; SMS_PROVIDER picks one or switches SMS off', async () => {
  const twilio = { TWILIO_ACCOUNT_SID: 'AC1', TWILIO_AUTH_TOKEN: 't' };
  const at = { AT_USERNAME: 'clinic-app', AT_API_KEY: 'k' };
  await withAt({}, null, async () => assert.equal(smsProvider(), null));
  await withAt(twilio, null, async () => assert.equal(smsProvider(), 'twilio'));
  await withAt(at, null, async () => assert.equal(smsProvider(), 'africastalking'));
  await withAt({ ...twilio, ...at }, null, async () => assert.equal(smsProvider(), 'twilio'));
  await withAt({ ...twilio, ...at, SMS_PROVIDER: 'africastalking' }, null, async () => assert.equal(smsProvider(), 'africastalking'));
  await withAt({ ...twilio, SMS_PROVIDER: 'none' }, null, async () => assert.equal(smsProvider(), null));
  await withAt({ ...at, SMS_PROVIDER: 'Twilio' }, null, async () => assert.equal(smsProvider(), null, 'Twilio chosen but its keys are missing'));
  const textbee = { TEXTBEE_API_KEY: 'tb-key' };
  await withAt(textbee, null, async () => assert.equal(smsProvider(), 'textbee'));
  await withAt({ ...twilio, ...textbee, SMS_PROVIDER: 'textbee' }, null, async () => assert.equal(smsProvider(), 'textbee'));
  await withAt({ ...twilio, SMS_PROVIDER: 'textbee' }, null, async () => assert.equal(smsProvider(), null, 'TextBee chosen but its key is missing'));
  await withAt({ TEXTBEE_API_KEY: '  ' }, null, async () => assert.equal(smsProvider(), null));
});

test('Twilio: sends from TWILIO_FROM with the account credentials, and reads refusals', async () => {
  assert.equal(parseTwilioResponse(true, '{"sid":"SM1","status":"queued"}'), 'sent');
  assert.equal(parseTwilioResponse(true, '{"status":"failed"}'), 'failed');
  assert.equal(parseTwilioResponse(false, '{"status":"queued"}'), 'failed');
  assert.equal(smsError('{"code":21608,"message":"The number +254711000101 is unverified."}'), '21608: The number +254711000101 is unverified.');

  let seen;
  const accept = async (url, opts) => {
    seen = { url: String(url), auth: opts.headers.Authorization, body: new URLSearchParams(opts.body) };
    return new Response('{"sid":"SM1","status":"queued"}', { status: 201 });
  };
  const env = { TWILIO_ACCOUNT_SID: 'AC123', TWILIO_AUTH_TOKEN: 'secret', TWILIO_FROM: '+15005550006' };
  await withAt(env, accept, async () => {
    assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'sent');
    assert.equal(seen.url, 'https://api.twilio.com/2010-04-01/Accounts/AC123/Messages.json');
    assert.equal(seen.auth, 'Basic ' + Buffer.from('AC123:secret').toString('base64'));
    assert.deepEqual(Object.fromEntries(seen.body), { To: '+254712345678', Body: 'Hello', From: '+15005550006' });
  });
  await withAt({ ...env, TWILIO_MESSAGING_SERVICE_SID: 'MG9' }, accept, async () => {
    await deliverSms('+254712345678', 'Hello');
    assert.equal(seen.body.get('MessagingServiceSid'), 'MG9');
    assert.equal(seen.body.get('From'), null);
  });
  const unverified = async () => new Response('{"code":21608,"message":"The number is unverified."}', { status: 400 });
  await withAt(env, unverified, async () => assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'failed'));
  await withAt({ ...env, TWILIO_FROM: '' }, async () => assert.fail('nothing to send from'), async () => {
    const out = await deliverSms('+254712345678', 'Hello');
    assert.equal(out.status, 'failed');
    assert.match(out.response, /TWILIO_FROM is not set/);
  });
});

test('Twilio: the key check shows trial status, balance, numbers and verified numbers', async () => {
  const replies = {
    '/Accounts/AC123.json': { type: 'Trial', status: 'active' },
    '/Accounts/AC123/Balance.json': { currency: 'USD', balance: '10.89' },
    '/Accounts/AC123/IncomingPhoneNumbers.json': { incoming_phone_numbers: [{ phone_number: '+15551234567' }] },
    '/Accounts/AC123/OutgoingCallerIds.json': { outgoing_caller_ids: [{ phone_number: '+254712345678' }] },
  };
  const api = async (url) => {
    const path = new URL(String(url)).pathname.replace('/2010-04-01', '');
    return replies[path] ? new Response(JSON.stringify(replies[path]), { status: 200 }) : new Response('{}', { status: 404 });
  };
  await withAt({ TWILIO_ACCOUNT_SID: 'AC123', TWILIO_AUTH_TOKEN: 'secret' }, api, async () => {
    assert.deepEqual(await checkAccount(), {
      ok: true,
      provider: 'twilio',
      status: 'active',
      trial: true,
      balance: 'USD 10.89',
      numbers: ['+15551234567'],
      verified: ['+254712345678'],
    });
  });
  const refused = async () => new Response('{"code":20003,"message":"Authenticate"}', { status: 401 });
  await withAt({ TWILIO_ACCOUNT_SID: 'AC123', TWILIO_AUTH_TOKEN: 'wrong' }, refused, async () => {
    assert.equal((await checkAccount()).problem, 'bad_keys');
  });
});

test('the server starts with live Twilio keys on the demo data too', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vaccine-sms-'));
  const dbPath = path.join(dir, 'demo.db');
  const db = openDb(dbPath);
  await seedDemo(db, '2026-10-25');
  db.close();
  try {
    const run = spawnSync(process.execPath, ['src/index.js'], {
      cwd: BACKEND,
      env: { ...process.env, DB_PATH: dbPath, DOCUMENT_DIR: path.join(path.dirname(dbPath), 'documents'), DOCUMENT_KEY_FILE: path.join(path.dirname(dbPath), 'document.key'), PORT: '0', SMS_PROVIDER: '', AT_USERNAME: '', AT_API_KEY: '', TEXTBEE_API_KEY: '', SMS_ONLY_TO: '', TWILIO_ACCOUNT_SID: 'AC1', TWILIO_AUTH_TOKEN: 't', TWILIO_FROM: '+15005550006' },
      encoding: 'utf8',
      timeout: 4000,
    });
    assert.match(run.stdout, /SMS: live, sent through Twilio/);
    assert.match(run.stdout, /Vaccine reminder API on port/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('TextBee: sends through the API key, from the chosen phone, and reads refusals', async () => {
  assert.equal(parseTextbeeResponse(true, '{"data":{"success":true,"smsBatchId":"b1","recipientCount":1}}'), 'sent');
  assert.equal(parseTextbeeResponse(true, '{"data":{"success":false}}'), 'failed');
  assert.equal(parseTextbeeResponse(true, '{"data":{"successCount":0,"failureCount":1}}'), 'failed');
  assert.equal(parseTextbeeResponse(false, '{"data":{"success":true}}'), 'failed');
  assert.equal(parseTextbeeResponse(true, 'not json'), 'failed');
  assert.equal(smsError('{"statusCode":400,"message":["recipients must be an array"],"error":"Bad Request"}'), 'recipients must be an array');

  let seen;
  const accept = async (url, opts) => {
    seen = { url: String(url), key: opts.headers['x-api-key'], body: JSON.parse(opts.body) };
    return new Response('{"data":{"success":true,"message":"SMS added to queue for processing","recipientCount":1}}', { status: 201 });
  };
  await withAt({ TEXTBEE_API_KEY: 'tb-key' }, accept, async () => {
    assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'sent');
    assert.equal(seen.url, 'https://api.textbee.dev/api/v1/gateway/send-sms');
    assert.equal(seen.key, 'tb-key');
    assert.deepEqual(seen.body, { recipients: ['+254712345678'], message: 'Hello' });
  });
  await withAt({ TEXTBEE_API_KEY: 'tb-key', TEXTBEE_DEVICE_ID: 'dev1', TEXTBEE_BASE_URL: 'https://sms.example.org/api/v1/' }, accept, async () => {
    await deliverSms('+254712345678', 'Hello');
    assert.equal(seen.url, 'https://sms.example.org/api/v1/gateway/send-sms');
    assert.equal(seen.body.deviceId, 'dev1');
  });
  const revoked = async () => new Response('{"statusCode":401,"message":"Unauthorized"}', { status: 401 });
  await withAt({ TEXTBEE_API_KEY: 'old' }, revoked, async () => {
    assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'failed');
    assert.equal((await checkAccount()).problem, 'bad_keys');
  });
});

// A fake TextBee: send-sms queues batch "b1"; the phone's answer for it comes from `phone()`.
function fakeTextbee(phone, { sendFails = 0 } = {}) {
  const calls = [];
  let failures = sendFails;
  const impl = async (url, opts = {}) => {
    const path = new URL(String(url)).pathname.replace('/api/v1', '');
    calls.push(path);
    if (path === '/gateway/send-sms') {
      if (failures-- > 0) throw Object.assign(new TypeError('fetch failed'), { cause: { code: 'UND_ERR_CONNECT_TIMEOUT' } });
      return new Response('{"data":{"success":true,"message":"SMS added to queue for processing","smsBatchId":"b1","recipientCount":1}}', { status: 201 });
    }
    if (path === '/gateway/devices') return new Response('{"data":[{"_id":"dev2"},{"_id":"dev1","isDefault":true}]}', { status: 200 });
    if (path === '/gateway/devices/dev1/sms-batch/b1') return new Response(JSON.stringify({ data: { messages: [phone()] } }), { status: 200 });
    return new Response('{}', { status: 404 });
  };
  return { impl, calls };
}

test('TextBee: an SMS counts as sent only once the phone confirms it', async () => {
  const saved = { ...textbeeTiming };
  Object.assign(textbeeTiming, { retryMs: 1, firstCheckMs: 1, everyMs: 5, confirmMs: 40 });
  const env = { TEXTBEE_API_KEY: 'tb-confirm' };
  try {
    let answers = [{ status: 'dispatched' }, { status: 'sent' }];
    const sent = fakeTextbee(() => answers.shift() || { status: 'delivered' });
    await withAt(env, sent.impl, async () => {
      const out = await deliverSms('+254712345678', 'Hello');
      assert.equal(out.status, 'sent');
      assert.ok(sent.calls.includes('/gateway/devices/dev1/sms-batch/b1'), 'asks the default phone');
    });

    const failed = fakeTextbee(() => ({ status: 'failed', errorMessage: 'A network error stopped the send.' }));
    await withAt(env, failed.impl, async () => {
      const out = await deliverSms('+254712345678', 'Hello');
      assert.equal(out.status, 'failed');
      assert.equal(out.response, 'The SMS phone could not send it: A network error stopped the send.');
    });

    // The phone is off or TextBee is asleep on it: the SMS waits, and is not called sent.
    const asleep = fakeTextbee(() => ({ status: 'dispatched' }));
    await withAt(env, asleep.impl, async () => {
      const out = await deliverSms('+254712345678', 'Hello');
      assert.equal(out.status, 'queued');
      assert.equal(textbeeBatchId(out.response), 'b1');
      // Without confirm, it does not wait at all.
      const before = asleep.calls.length;
      assert.equal((await deliverSms('+254712345678', 'Hello', { confirm: false })).status, 'queued');
      assert.deepEqual(asleep.calls.slice(before), ['/gateway/send-sms']);
    });
  } finally {
    Object.assign(textbeeTiming, saved);
  }
});

test('TextBee: tries once more when it cannot be reached, never after it answered', async () => {
  const saved = { ...textbeeTiming };
  Object.assign(textbeeTiming, { retryMs: 1, firstCheckMs: 1, everyMs: 5, confirmMs: 40 });
  try {
    const once = fakeTextbee(() => ({ status: 'sent' }), { sendFails: 1 });
    await withAt({ TEXTBEE_API_KEY: 'tb-retry' }, once.impl, async () => {
      assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'sent');
      assert.equal(once.calls.filter((c) => c === '/gateway/send-sms').length, 2);
    });
    const down = fakeTextbee(() => ({ status: 'sent' }), { sendFails: 5 });
    await withAt({ TEXTBEE_API_KEY: 'tb-retry' }, down.impl, async () => {
      const out = await deliverSms('+254712345678', 'Hello');
      assert.equal(out.status, 'failed');
      assert.match(out.response, /Could not reach the SMS service \(UND_ERR_CONNECT_TIMEOUT\)/);
      assert.equal(down.calls.length, 2);
    });
    // An answer that came back, even an error, is never sent again.
    let posts = 0;
    const refused = async () => {
      posts++;
      return new Response('{"statusCode":500,"message":"Internal error"}', { status: 500 });
    };
    await withAt({ TEXTBEE_API_KEY: 'tb-retry' }, refused, async () => {
      assert.equal((await deliverSms('+254712345678', 'Hello')).status, 'failed');
      assert.equal(posts, 1);
    });
  } finally {
    Object.assign(textbeeTiming, saved);
  }
});

test('TextBee: SMS left waiting are updated later, however long the phone takes', async () => {
  const db = openDb(':memory:');
  db.prepare("INSERT INTO children (child_name, caregiver_name, phone, dob, language, facility, consent) VALUES ('Zuri', 'Mama Zuri', '+254712345678', '2026-11-01', 'en', '', 1)").run();
  const childId = db.prepare('SELECT id FROM children').get().id;
  const queued = '{"data":{"success":true,"smsBatchId":"b1"}}';
  const add = (minutesAgo) =>
    db
      .prepare(`INSERT INTO sms_log (phone, message, status, response, child_id, kind, created_at) VALUES ('+254712345678', 'Hi', 'queued', ?, ?, 'manual', datetime('now', ?))`)
      .run(queued, childId, `-${minutesAgo} minutes`).lastInsertRowid;
  const status = (id) => db.prepare('SELECT status, response FROM sms_log WHERE id = ?').get(id);
  try {
    let answer = { status: 'dispatched' };
    const tb = fakeTextbee(() => answer);
    await withAt({ TEXTBEE_API_KEY: 'tb-refresh' }, tb.impl, async () => {
      const fresh = add(2);
      const old = add(300);
      await refreshQueuedSms(db, childId);
      assert.equal(status(fresh).status, 'queued');
      // Still waiting hours later is not a failure: the phone sends it once TextBee runs again.
      assert.equal(status(old).status, 'queued');

      answer = { status: 'delivered' };
      await refreshQueuedSms(db, childId);
      assert.equal(status(fresh).status, 'sent');
      assert.equal(status(old).status, 'sent');

      answer = { status: 'failed', errorMessage: 'No signal' };
      const failing = add(1);
      await refreshQueuedSms(db, childId);
      assert.equal(status(failing).status, 'failed');
      assert.equal(status(failing).response, 'The SMS phone could not send it: No signal');
    });
  } finally {
    db.close();
  }
});

test('TextBee: the key check lists the linked phones', async () => {
  let seen;
  const devices = async (url, opts) => {
    seen = { url: String(url), key: opts.headers['x-api-key'] };
    return new Response(
      JSON.stringify({
        data: [
          { _id: 'dev1', brand: 'samsung', model: 'SM-A155F', enabled: true, isDefault: true, lastHeartbeat: '2026-11-18T07:00:00.000Z' },
          { _id: 'dev2', name: 'Clinic phone', brand: 'TECNO', model: 'KI5', enabled: false },
        ],
      }),
      { status: 200 }
    );
  };
  await withAt({ TEXTBEE_API_KEY: 'tb-key' }, devices, async () => {
    assert.deepEqual(await checkAccount(), {
      ok: true,
      provider: 'textbee',
      devices: [
        { id: 'dev1', name: 'samsung SM-A155F', enabled: true, isDefault: true, lastSeen: '2026-11-18T07:00:00.000Z' },
        { id: 'dev2', name: 'Clinic phone', enabled: false, isDefault: false, lastSeen: null },
      ],
    });
    assert.equal(seen.url, 'https://api.textbee.dev/api/v1/gateway/devices');
    assert.equal(seen.key, 'tb-key');
  });
});

test('the server starts with a live TextBee key on the demo data too', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vaccine-sms-'));
  const dbPath = path.join(dir, 'demo.db');
  const db = openDb(dbPath);
  await seedDemo(db, '2026-10-25');
  db.close();
  try {
    const run = spawnSync(process.execPath, ['src/index.js'], {
      cwd: BACKEND,
      env: { ...process.env, DB_PATH: dbPath, DOCUMENT_DIR: path.join(path.dirname(dbPath), 'documents'), DOCUMENT_KEY_FILE: path.join(path.dirname(dbPath), 'document.key'), PORT: '0', SMS_PROVIDER: 'textbee', TEXTBEE_API_KEY: 'tb-key', SMS_ONLY_TO: '', TWILIO_ACCOUNT_SID: '', AT_USERNAME: '' },
      encoding: 'utf8',
      timeout: 4000,
    });
    assert.match(run.stdout, /SMS: live, sent through TextBee/);
    assert.match(run.stdout, /Vaccine reminder API on port/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('AUTO_REMINDERS=off stops the daily automatic reminders', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vaccine-sms-'));
  const dbPath = path.join(dir, 'empty.db');
  try {
    const start = (value) =>
      spawnSync(process.execPath, ['src/index.js'], {
        cwd: BACKEND,
        env: { ...process.env, DB_PATH: dbPath, DOCUMENT_DIR: path.join(dir, 'documents'), DOCUMENT_KEY_FILE: path.join(dir, 'document.key'), PORT: '0', SMS_PROVIDER: 'none', AUTO_REMINDERS: value },
        encoding: 'utf8',
        timeout: 4000,
      });
    assert.match(start('off').stdout, /Automatic reminders: off/);
    assert.match(start('').stdout, /Automatic reminders: every day after 08:00/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
  await withAt({ AUTO_REMINDERS: 'off' }, null, async () => assert.equal(autoReminders(), false));
  await withAt({ AUTO_REMINDERS: '' }, null, async () => assert.equal(autoReminders(), true));
});

test('demo children never get real SMS; children registered in the app do', async () => {
  const db = openDb(':memory:');
  await seedDemo(db, '2026-10-25');
  // A real child, registered by a health worker, whose 6-week vaccines are due today.
  db.prepare(
    "INSERT INTO children (child_name, caregiver_name, phone, dob, language) VALUES ('Real Child', 'Real Parent', '+254769703902', '2026-09-13', 'en')"
  ).run();
  const realId = db.prepare("SELECT id FROM children WHERE child_name = 'Real Child'").get().id;
  const ins = db.prepare("INSERT INTO doses (child_id, code, name, due_date) VALUES (?, 'PENTA1', 'Pentavalent 1', '2026-10-25')");
  ins.run(realId);
  const sent = [];
  const accept = async (url, opts) => {
    sent.push(JSON.parse(opts.body).recipients[0]);
    return new Response('{"data":{"success":true}}', { status: 201 });
  };
  await withAt({ TEXTBEE_API_KEY: 'k' }, accept, async () => {
    const results = await runReminders(db, '2026-10-25');
    assert.ok(results.length > 5, 'demo children are still processed');
    const byName = Object.fromEntries(results.map((r) => [r.childName, r.status]));
    assert.equal(byName['Real Child'], 'sent');
    for (const c of DEMO_CHILDREN) if (byName[c.childName]) assert.equal(byName[c.childName], 'demo', c.childName);
    const demoChild = db.prepare('SELECT id, phone FROM children WHERE is_demo = 1').get();
    const manual = await sendSms(db, demoChild.phone, 'Hi', { childId: demoChild.id, kind: 'manual' });
    assert.equal(manual.status, 'demo');
  });
  assert.deepEqual(sent, ['+254769703902'], 'only the real child was texted');
  assert.equal(db.prepare("SELECT COUNT(*) AS n FROM sms_log WHERE status = 'demo'").get().n > 5, true);

  // Databases seeded before the flag existed are fixed by their made-up numbers.
  db.exec('UPDATE children SET is_demo = 0');
  assert.equal(markDemoChildren(db), DEMO_CHILDREN.length);
  assert.equal(db.prepare('SELECT is_demo FROM children WHERE id = ?').get(realId).is_demo, 0);
  db.close();
});

test('SMS_ONLY_TO: only the listed numbers get real SMS, the rest are only logged', async () => {
  const sent = [];
  const accept = async (url, opts) => {
    sent.push(JSON.parse(opts.body).recipients[0]);
    return new Response('{"data":{"success":true}}', { status: 201 });
  };
  await withAt({ TEXTBEE_API_KEY: 'k', SMS_ONLY_TO: '0769 703 902, +254711000999' }, accept, async () => {
    assert.deepEqual(smsOnlyTo(), ['+254769703902', '+254711000999']);
    assert.equal((await deliverSms('+254769703902', 'Hi')).status, 'sent');
    assert.equal((await deliverSms('+254711000999', 'Hi')).status, 'sent');
    const other = await deliverSms('+254711000101', 'Hi');
    assert.equal(other.status, 'dry-run');
    assert.match(other.response, /SMS_ONLY_TO/);
  });
  assert.deepEqual(sent, ['+254769703902', '+254711000999']);
  // A setting with no valid number blocks everything rather than texting everyone.
  await withAt({ TEXTBEE_API_KEY: 'k', SMS_ONLY_TO: 'my phone' }, async () => assert.fail('nothing should be sent'), async () => {
    assert.deepEqual(smsOnlyTo(), []);
    assert.equal((await deliverSms('+254769703902', 'Hi')).status, 'dry-run');
  });
  await withAt({ TEXTBEE_API_KEY: 'k' }, null, async () => assert.equal(smsOnlyTo(), null));
});

test('the seed adds the demo data next to real accounts with live keys set, and SMS_ONLY_TO still narrows sending', async () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vaccine-sms-'));
  const dbPath = path.join(dir, 'live.db');
  const db = openDb(dbPath);
  db.prepare("INSERT INTO workers (name, phone, facility, password_hash) VALUES ('Real Worker', '+254769703902', 'Kenyatta National Hospital', 'x')").run();
  db.close();
  const live = { DB_PATH: dbPath, DOCUMENT_DIR: path.join(path.dirname(dbPath), 'documents'), DOCUMENT_KEY_FILE: path.join(path.dirname(dbPath), 'document.key'), PORT: '0', SMS_PROVIDER: 'textbee', TEXTBEE_API_KEY: 'tb-key', TWILIO_ACCOUNT_SID: '', AT_USERNAME: '' };
  const run = (args, env) =>
    spawnSync(process.execPath, args, { cwd: BACKEND, env: { ...process.env, ...live, ...env }, encoding: 'utf8', timeout: 8000 });
  try {
    const added = run(['src/seed.js', '--add'], { SMS_ONLY_TO: '' });
    assert.equal(added.status, 0, added.stderr);
    assert.match(added.stdout, /Demo data added to/);
    const check = openDb(dbPath);
    assert.ok(check.prepare("SELECT 1 FROM workers WHERE name = 'Real Worker'").get(), 'the real account is kept');
    assert.equal(check.prepare('SELECT COUNT(*) AS n FROM workers').get().n, 1 + DEMO_WORKERS.length);
    assert.equal(check.prepare('SELECT COUNT(*) AS n FROM children').get().n, DEMO_CHILDREN.length);
    assert.equal(check.prepare('SELECT COUNT(*) AS n FROM children WHERE is_demo = 1').get().n, DEMO_CHILDREN.length);
    check.close();

    assert.match(run(['src/seed.js', '--add'], { SMS_ONLY_TO: '0769703902' }).stderr, /already has the demo data/);

    const open = run(['src/index.js'], { SMS_ONLY_TO: '' });
    assert.match(open.stdout, /SMS: live, sent through TextBee\n/);
    assert.match(open.stdout, /Vaccine reminder API on port/);
    const started = run(['src/index.js'], { SMS_ONLY_TO: '0769703902' });
    assert.match(started.stdout, /SMS: live, sent through TextBee, only to \+254769703902 \(SMS_ONLY_TO\); messages to other numbers are only logged/);
    assert.match(started.stdout, /Vaccine reminder API on port/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});
