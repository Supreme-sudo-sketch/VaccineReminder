export function normalizePhone(raw) {
  const s = String(raw || '').replace(/[\s\-()]/g, '');
  if (/^\+254[17]\d{8}$/.test(s)) return s;
  if (/^254[17]\d{8}$/.test(s)) return '+' + s;
  if (/^0[17]\d{8}$/.test(s)) return '+254' + s.slice(1);
  if (/^[17]\d{8}$/.test(s)) return '+254' + s;
  return null;
}

// SMS goes through TextBee, Twilio or Africa's Talking, whichever has its keys in the settings.
// SMS_PROVIDER picks one when several are set, and SMS_PROVIDER=none switches SMS off while keeping the keys.
const twilioReady = () => Boolean(process.env.TWILIO_ACCOUNT_SID && process.env.TWILIO_AUTH_TOKEN);
const atReady = () => Boolean(process.env.AT_USERNAME && process.env.AT_API_KEY);
const textbeeReady = () => Boolean(String(process.env.TEXTBEE_API_KEY || '').trim());

export function smsProvider() {
  const wanted = String(process.env.SMS_PROVIDER || '').trim().toLowerCase();
  if (wanted === 'twilio') return twilioReady() ? 'twilio' : null;
  if (wanted === 'africastalking') return atReady() ? 'africastalking' : null;
  if (wanted === 'textbee') return textbeeReady() ? 'textbee' : null;
  if (wanted === 'none') return null;
  if (twilioReady()) return 'twilio';
  if (atReady()) return 'africastalking';
  if (textbeeReady()) return 'textbee';
  return null;
}

export const PROVIDER_NAMES = { twilio: 'Twilio', africastalking: "Africa's Talking", textbee: 'TextBee' };

export function smsMode() {
  return smsProvider() ? 'live' : 'test';
}

// The Africa's Talking sandbox sends to its simulator only, never to real phones.
export const isSandbox = () => smsProvider() === 'africastalking' && process.env.AT_USERNAME === 'sandbox';

// SMS_ONLY_TO lists the only numbers that get real SMS, e.g. your own phone while the example data is
// loaded; messages to every other number are only logged, as in test mode. Returns null when it is
// not set, otherwise the valid numbers in it (possibly none, and then nothing is sent).
export function smsOnlyTo() {
  const raw = String(process.env.SMS_ONLY_TO || '').trim();
  if (!raw) return null;
  return [...new Set(raw.split(/[,;]/).map((n) => normalizePhone(n)).filter(Boolean))];
}

// Network errors where the request never reached the SMS service, so trying again cannot send the
// same SMS twice.
const NOT_CONNECTED = new Set(['UND_ERR_CONNECT_TIMEOUT', 'ECONNREFUSED', 'ENOTFOUND', 'EAI_AGAIN', 'ENETUNREACH', 'EHOSTUNREACH', 'ENETDOWN']);
export const notConnected = (e) => NOT_CONNECTED.has(e?.cause?.code) || NOT_CONNECTED.has(e?.code);

// A readable reason for a request that got no answer at all.
export function networkProblem(e) {
  if (notConnected(e)) return `Could not reach the SMS service (${e.cause?.code || e.code}). Check the computer's internet connection`;
  if (e?.name === 'TimeoutError') return 'The SMS service did not answer in time';
  return String(e?.message || e);
}

const wait = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

// Sends one SMS, or nothing in test mode. Returns { status, response } with status 'sent', 'failed',
// 'dry-run' or 'queued' (TextBee has it, but the phone has not confirmed sending it yet). With
// confirm: false, TextBee does not wait for the phone. sendSms() below also records it in sms_log.
export async function deliverSms(phone, message, { confirm = true } = {}) {
  const provider = smsProvider();
  if (!provider) return { status: 'dry-run', response: 'No SMS provider keys set, message not sent' };
  const only = smsOnlyTo();
  if (only && !only.includes(normalizePhone(phone))) {
    return { status: 'dry-run', response: 'Not sent: only the numbers in SMS_ONLY_TO get real SMS' };
  }
  try {
    return await { twilio: twilioSend, africastalking: atSend, textbee: textbeeSend }[provider](phone, message, { confirm });
  } catch (e) {
    return { status: 'failed', response: networkProblem(e) };
  }
}

// Checks the keys without sending anything. Returns { ok: true, balance, ... } or { ok: false, problem }
// with problem 'missing_keys', 'bad_keys', 'unreachable' or 'error'.
export async function checkAccount() {
  const provider = smsProvider();
  if (!provider) return { ok: false, problem: 'missing_keys' };
  try {
    return await { twilio: twilioAccount, africastalking: atAccount, textbee: textbeeAccount }[provider]();
  } catch (e) {
    return { ok: false, problem: 'unreachable', detail: String(e.message || e) };
  }
}

// AUTO_REMINDERS=off stops the daily automatic reminders: SMS then go out only when a health
// worker sends them from the app. On unless switched off.
export function autoReminders() {
  return !['off', 'false', 'no', '0'].includes(String(process.env.AUTO_REMINDERS ?? '').trim().toLowerCase());
}

export const DEMO_NOT_SENT = 'Demo child with a made-up number: SMS not sent';

// Sends and records one SMS. A demo child's message is recorded with status "demo" but never sent,
// whatever the SMS settings, so the demo data can sit next to real children with live SMS on.
export async function sendSms(db, phone, message, { childId = null, kind = null, confirm = true } = {}) {
  const demo = childId != null && db.prepare('SELECT is_demo FROM children WHERE id = ?').get(childId)?.is_demo === 1;
  const { status, response } = demo ? { status: 'demo', response: DEMO_NOT_SENT } : await deliverSms(phone, message, { confirm });
  db.prepare('INSERT INTO sms_log (phone, message, status, response, child_id, kind) VALUES (?, ?, ?, ?, ?, ?)').run(
    phone,
    message,
    status,
    response,
    childId,
    kind
  );
  return { status, response };
}

// A readable reason from a provider's error reply, e.g. "21608: The number ... is unverified".
export function smsError(text) {
  try {
    const j = JSON.parse(text);
    const message = Array.isArray(j?.message) ? j.message.join('; ') : j?.message || j?.error;
    if (j?.code || message) return `${j.code ? j.code + ': ' : ''}${message || ''}`.trim();
  } catch {
    // not JSON
  }
  return String(text || '').slice(0, 200);
}

const refused = (status, text) => ({ ok: false, problem: status === 401 ? 'bad_keys' : 'error', detail: `${status} ${text.slice(0, 200)}` });

// --- Africa's Talking -----------------------------------------------------------------------------

const atHost = () => (process.env.AT_USERNAME === 'sandbox' ? 'api.sandbox.africastalking.com' : 'api.africastalking.com');

async function atSend(phone, message) {
  const params = { username: process.env.AT_USERNAME, to: phone, message };
  if (process.env.AT_SENDER_ID) params.from = process.env.AT_SENDER_ID;
  const res = await fetch(`https://${atHost()}/version1/messaging`, {
    method: 'POST',
    headers: { apiKey: process.env.AT_API_KEY, Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params).toString(),
    signal: AbortSignal.timeout(15000),
  });
  const response = (await res.text()).slice(0, 1000);
  return { status: parseAtResponse(res.ok, response), response };
}

// Africa's Talking answers 201 even when a recipient was rejected (bad number, no balance),
// so the per-recipient status decides whether the message really went out.
export function parseAtResponse(ok, text) {
  if (!ok) return 'failed';
  try {
    const r = JSON.parse(text)?.SMSMessageData?.Recipients?.[0];
    if (r && (r.status === 'Success' || [100, 101, 102].includes(r.statusCode))) return 'sent';
  } catch {
    // not JSON, treat as failed below
  }
  return 'failed';
}

async function atAccount() {
  const res = await fetch(`https://${atHost()}/version1/user?username=${encodeURIComponent(process.env.AT_USERNAME)}`, {
    headers: { apiKey: process.env.AT_API_KEY, Accept: 'application/json' },
    signal: AbortSignal.timeout(15000),
  });
  const text = await res.text();
  if (!res.ok) return refused(res.status, text);
  const balance = parseBalance(text);
  return balance ? { ok: true, balance } : { ok: false, problem: 'error', detail: text.slice(0, 200) };
}

export function parseBalance(text) {
  try {
    const balance = JSON.parse(text)?.UserData?.balance;
    return typeof balance === 'string' && balance.trim() ? balance.trim() : null;
  } catch {
    return null;
  }
}

// --- Twilio -----------------------------------------------------------------------------------------

const twilioBase = () => `https://api.twilio.com/2010-04-01/Accounts/${encodeURIComponent(process.env.TWILIO_ACCOUNT_SID)}`;
const twilioAuth = () => 'Basic ' + Buffer.from(`${process.env.TWILIO_ACCOUNT_SID}:${process.env.TWILIO_AUTH_TOKEN}`).toString('base64');

async function twilioGet(path) {
  const res = await fetch(twilioBase() + path, { headers: { Authorization: twilioAuth(), Accept: 'application/json' }, signal: AbortSignal.timeout(15000) });
  const text = await res.text();
  return { res, text, json: res.ok ? JSON.parse(text) : null };
}

// Messages come from TWILIO_FROM (a Twilio phone number, or an approved sender name) or from a
// Messaging Service when TWILIO_MESSAGING_SERVICE_SID is set.
async function twilioSend(phone, message) {
  const from = String(process.env.TWILIO_FROM || '').trim();
  const service = String(process.env.TWILIO_MESSAGING_SERVICE_SID || '').trim();
  if (!from && !service) return { status: 'failed', response: 'TWILIO_FROM is not set: put your Twilio phone number in backend/.env' };
  const params = { To: phone, Body: message, ...(service ? { MessagingServiceSid: service } : { From: from }) };
  const res = await fetch(`${twilioBase()}/Messages.json`, {
    method: 'POST',
    headers: { Authorization: twilioAuth(), Accept: 'application/json', 'Content-Type': 'application/x-www-form-urlencoded' },
    body: new URLSearchParams(params).toString(),
    signal: AbortSignal.timeout(15000),
  });
  const response = (await res.text()).slice(0, 1000);
  return { status: parseTwilioResponse(res.ok, response), response };
}

// Twilio accepts the message and queues it; a refused one (unverified number on a trial account,
// country not enabled, bad number) comes back as an error instead.
export function parseTwilioResponse(ok, text) {
  if (!ok) return 'failed';
  try {
    const status = JSON.parse(text)?.status;
    return ['accepted', 'scheduled', 'queued', 'sending', 'sent', 'delivered'].includes(status) ? 'sent' : 'failed';
  } catch {
    return 'failed';
  }
}

async function twilioAccount() {
  const account = await twilioGet('.json');
  if (!account.res.ok) return refused(account.res.status, account.text);
  const balance = await twilioGet('/Balance.json');
  const numbers = await twilioGet('/IncomingPhoneNumbers.json');
  const trial = String(account.json.type || '').toLowerCase() === 'trial';
  // On a trial account Twilio only delivers to numbers verified in the console.
  const verified = trial ? await twilioGet('/OutgoingCallerIds.json') : null;
  return {
    ok: true,
    provider: 'twilio',
    status: account.json.status,
    trial,
    balance: balance.json ? `${balance.json.currency} ${balance.json.balance}` : 'unknown',
    numbers: (numbers.json?.incoming_phone_numbers || []).map((n) => n.phone_number),
    verified: (verified?.json?.outgoing_caller_ids || []).map((n) => n.phone_number),
  };
}

// --- TextBee ----------------------------------------------------------------------------------------
// TextBee (textbee.dev) sends each SMS from an Android phone running the TextBee app, through that
// phone's own SIM, so it costs the phone's normal SMS rate and goes to any number.

const textbeeBase = () => String(process.env.TEXTBEE_BASE_URL || 'https://api.textbee.dev/api/v1').trim().replace(/\/+$/, '');
const textbeeHeaders = () => ({ 'x-api-key': String(process.env.TEXTBEE_API_KEY).trim(), Accept: 'application/json' });

// How long to wait for the phone to confirm an SMS. Tests shorten these.
export const textbeeTiming = { retryMs: 1000, firstCheckMs: 2000, everyMs: 1500, confirmMs: 10000 };

// One request to TextBee, tried once more if TextBee could not be reached at all.
async function textbeeFetch(path, opts = {}) {
  for (let attempt = 1; ; attempt++) {
    try {
      return await fetch(textbeeBase() + path, { ...opts, headers: { ...textbeeHeaders(), ...opts.headers }, signal: AbortSignal.timeout(15000) });
    } catch (e) {
      if (attempt >= 2 || !notConnected(e)) throw e;
      await wait(textbeeTiming.retryMs);
    }
  }
}

// The phone TextBee sends from: TEXTBEE_DEVICE_ID, or else the account's default phone.
let knownDevice = null;
async function textbeeDevice() {
  const chosen = String(process.env.TEXTBEE_DEVICE_ID || '').trim();
  if (chosen) return chosen;
  const account = textbeeBase() + textbeeHeaders()['x-api-key'];
  if (knownDevice?.account === account) return knownDevice.id;
  const res = await textbeeFetch('/gateway/devices');
  const list = res.ok ? (await res.json().catch(() => null))?.data : null;
  if (!Array.isArray(list) || !list.length) return null;
  knownDevice = { account, id: (list.find((d) => d.isDefault) || list[0])._id };
  return knownDevice.id;
}

export function textbeeBatchId(text) {
  try {
    return JSON.parse(text)?.data?.smsBatchId || null;
  } catch {
    return null;
  }
}

// What the phone did with a batch: 'sent' (or delivered), 'failed' with the phone's reason, or
// 'waiting' while it has not done either yet.
export function textbeeBatchState(json) {
  const messages = json?.data?.messages;
  if (!Array.isArray(messages) || !messages.length) return { state: 'unknown' };
  if (messages.some((m) => m.status === 'sent' || m.status === 'delivered')) return { state: 'sent' };
  const failed = messages.find((m) => m.status === 'failed');
  if (failed) return { state: 'failed', reason: String(failed.errorMessage || failed.errorCode || 'no reason given') };
  return { state: 'waiting' };
}

async function textbeeBatch(batchId) {
  const device = await textbeeDevice();
  if (!device) return { state: 'unknown' };
  const res = await textbeeFetch(`/gateway/devices/${encodeURIComponent(device)}/sms-batch/${encodeURIComponent(batchId)}`);
  if (!res.ok) return { state: 'unknown' };
  return textbeeBatchState(await res.json().catch(() => null));
}

export const phoneFailed = (reason) => `The SMS phone could not send it: ${reason}`;

// TextBee only queues the SMS: its app on the gateway phone sends it a few seconds later, if the
// phone is on and the app is allowed to run. So the result is 'sent' only once the phone says it sent
// it; still waiting after a few seconds gives 'queued'. A queued SMS still goes out later, as soon as
// the TextBee app runs again, so sending it again would reach the parent twice.
// Without TEXTBEE_DEVICE_ID, TextBee uses the account's default phone (or the one online most recently).
async function textbeeSend(phone, message, { confirm = true } = {}) {
  const body = { recipients: [phone], message };
  const device = String(process.env.TEXTBEE_DEVICE_ID || '').trim();
  if (device) body.deviceId = device;
  const res = await textbeeFetch('/gateway/send-sms', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  });
  const response = (await res.text()).slice(0, 1000);
  if (parseTextbeeResponse(res.ok, response) === 'failed') return { status: 'failed', response };
  const batchId = textbeeBatchId(response);
  if (!batchId) return { status: 'sent', response };
  if (!confirm) return { status: 'queued', response };

  const until = Date.now() + textbeeTiming.confirmMs;
  await wait(textbeeTiming.firstCheckMs);
  for (;;) {
    let out;
    try {
      out = await textbeeBatch(batchId);
    } catch {
      out = { state: 'waiting' };
    }
    // A TextBee server that cannot report on batches: trust its queue, as before.
    if (out.state === 'unknown') return { status: 'sent', response };
    if (out.state === 'sent') return { status: 'sent', response };
    if (out.state === 'failed') return { status: 'failed', response: phoneFailed(out.reason) };
    if (Date.now() + textbeeTiming.everyMs > until) return { status: 'queued', response };
    await wait(textbeeTiming.everyMs);
  }
}

// Asks TextBee what became of a child's SMS still waiting for the phone, and records the answer. One
// that waits is never given up on here: the phone sends it whenever the TextBee app runs again.
export async function refreshQueuedSms(db, childId) {
  if (smsProvider() !== 'textbee') return;
  const rows = db
    .prepare("SELECT id, response FROM sms_log WHERE status = 'queued' AND child_id = ? ORDER BY id DESC LIMIT 10")
    .all(childId);
  const update = db.prepare('UPDATE sms_log SET status = ?, response = ? WHERE id = ?');
  for (const r of rows) {
    const batchId = textbeeBatchId(r.response);
    let out = { state: 'unknown' };
    try {
      if (batchId) out = await textbeeBatch(batchId);
    } catch {
      // TextBee unreachable: leave it waiting and ask again next time
    }
    if (out.state === 'sent') update.run('sent', r.response, r.id);
    else if (out.state === 'failed') update.run('failed', phoneFailed(out.reason), r.id);
  }
}

// TextBee accepts the message and queues it for the phone, which sends it as soon as it gets it.
export function parseTextbeeResponse(ok, text) {
  if (!ok) return 'failed';
  try {
    const data = JSON.parse(text)?.data;
    return data && data.success !== false && !(data.failureCount > 0) ? 'sent' : 'failed';
  } catch {
    return 'failed';
  }
}

async function textbeeAccount() {
  const res = await textbeeFetch('/gateway/devices');
  const text = await res.text();
  if (!res.ok) return refused(res.status, text);
  let list = null;
  try {
    list = JSON.parse(text)?.data;
  } catch {
    // not JSON, reported below
  }
  if (!Array.isArray(list)) return { ok: false, problem: 'error', detail: text.slice(0, 200) };
  return {
    ok: true,
    provider: 'textbee',
    devices: list.map((d) => ({
      id: d._id,
      name: d.name || [d.brand, d.model].filter(Boolean).join(' ') || 'Android phone',
      enabled: d.enabled !== false,
      isDefault: Boolean(d.isDefault),
      lastSeen: d.lastHeartbeat || null,
    })),
  };
}
