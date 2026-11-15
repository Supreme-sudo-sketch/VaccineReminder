export function normalizePhone(raw) {
  const s = String(raw || '').replace(/[\s\-()]/g, '');
  if (/^\+254[17]\d{8}$/.test(s)) return s;
  if (/^254[17]\d{8}$/.test(s)) return '+' + s;
  if (/^0[17]\d{8}$/.test(s)) return '+254' + s.slice(1);
  if (/^[17]\d{8}$/.test(s)) return '+254' + s;
  return null;
}

export function smsMode() {
  return process.env.AT_USERNAME && process.env.AT_API_KEY ? 'live' : 'test';
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

export async function sendSms(db, phone, message, { childId = null, kind = null } = {}) {
  const username = process.env.AT_USERNAME;
  const apiKey = process.env.AT_API_KEY;
  let status = 'dry-run';
  let response = 'AT_USERNAME / AT_API_KEY not set, message not sent';

  if (username && apiKey) {
    const host = username === 'sandbox' ? 'api.sandbox.africastalking.com' : 'api.africastalking.com';
    const params = { username, to: phone, message };
    if (process.env.AT_SENDER_ID) params.from = process.env.AT_SENDER_ID;
    try {
      const res = await fetch(`https://${host}/version1/messaging`, {
        method: 'POST',
        headers: {
          apiKey,
          Accept: 'application/json',
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams(params).toString(),
        signal: AbortSignal.timeout(15000),
      });
      response = (await res.text()).slice(0, 1000);
      status = parseAtResponse(res.ok, response);
    } catch (e) {
      status = 'failed';
      response = String(e.message || e);
    }
  }

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
