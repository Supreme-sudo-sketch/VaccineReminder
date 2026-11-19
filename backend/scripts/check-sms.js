// Checks the SMS keys in backend/.env (TextBee, Twilio or Africa's Talking), and optionally sends one test SMS.
//
//   npm run check-sms                  checks the keys and shows the balance or phones; sends nothing
//   npm run check-sms -- 0712345678    also sends one test SMS to that number
//
// The test SMS is not saved in the database, so it does not appear in any child's history.
import { checkAccount, deliverSms, isSandbox, normalizePhone, PROVIDER_NAMES, smsError, smsOnlyTo, smsProvider } from '../src/sms.js';

const number = process.argv[2];
const provider = smsProvider();

if (!provider) {
  console.log(`SMS is in test mode: no SMS provider keys are set${process.env.SMS_PROVIDER ? ` for SMS_PROVIDER=${process.env.SMS_PROVIDER}` : ''}, so reminders are only logged.

To send SMS, put one provider's details in backend/.env, either
  TEXTBEE_API_KEY (and TEXTBEE_DEVICE_ID)                  (textbee.dev dashboard, phone with the TextBee app)
or
  TWILIO_ACCOUNT_SID, TWILIO_AUTH_TOKEN and TWILIO_FROM   (Twilio console > Account info)
or
  AT_USERNAME and AT_API_KEY                               (Africa's Talking dashboard > Settings > API Key)
Then run this check again.`);
  process.exit(1);
}

const name = PROVIDER_NAMES[provider];
if (provider === 'africastalking') {
  console.log(`${name}, username "${process.env.AT_USERNAME}"${isSandbox() ? ' (sandbox: messages go to the simulator only)' : ''}.`);
  if (process.env.AT_SENDER_ID) console.log(`Sender ID: ${process.env.AT_SENDER_ID} (must be approved by Africa's Talking)`);
} else if (provider === 'twilio') {
  console.log(`${name}, account ${process.env.TWILIO_ACCOUNT_SID}.`);
} else {
  console.log(`${name}${process.env.TEXTBEE_BASE_URL ? ` at ${process.env.TEXTBEE_BASE_URL}` : ''}: SMS go out from your Android phone's SIM.`);
}

const account = await checkAccount();
if (!account.ok) {
  const why = {
    bad_keys: {
      twilio: 'Twilio refused the Account SID / Auth Token. Copy both again from the Twilio console (live credentials, not test ones).',
      africastalking:
        'Africa\'s Talking refused the API key. The key must belong to AT_USERNAME (use "sandbox" with a sandbox key), and a new key replaces the old one.',
      textbee: 'TextBee refused the API key. Copy it again from the textbee.dev dashboard (a new key replaces a revoked one).',
    }[provider],
    unreachable: `Could not reach ${name}. Check the internet connection.`,
    error: `${name} gave an unexpected answer.`,
  }[account.problem];
  console.error(`Keys not working. ${why}${account.detail ? `\n  (${account.detail})` : ''}`);
  process.exit(1);
}

console.log(`Keys work.${account.balance ? ` Balance: ${account.balance}` : ''}`);
let ready = true;
if (provider === 'twilio') {
  console.log(`Account: ${account.trial ? 'trial' : 'full (upgraded)'}, ${account.status}.`);
  console.log(`Phone numbers on the account: ${account.numbers.length ? account.numbers.join(', ') : 'none'}`);
  const from = String(process.env.TWILIO_FROM || '').trim();
  if (process.env.TWILIO_MESSAGING_SERVICE_SID) {
    console.log(`Sending through Messaging Service ${process.env.TWILIO_MESSAGING_SERVICE_SID}.`);
  } else if (!from) {
    ready = false;
    console.warn(
      account.numbers.length
        ? `TWILIO_FROM is not set. Put one of the numbers above in backend/.env, e.g. TWILIO_FROM=${account.numbers[0]}`
        : 'TWILIO_FROM is not set and the account has no phone number. In the Twilio console, get a phone number (trial accounts get one free), then put it in backend/.env as TWILIO_FROM.'
    );
  } else {
    console.log(`Messages come from ${from}.`);
  }
  if (account.trial) {
    console.log(
      `Trial account: Twilio only delivers to verified numbers (${account.verified.length ? account.verified.join(', ') : 'none yet'}), ` +
        'and adds "Sent from your Twilio trial account" to each SMS. Verify more numbers in the Twilio console, or upgrade to text any number.'
    );
  }
}
if (provider === 'textbee') ready = showTextbeePhones(account.devices);
const onlyTo = smsOnlyTo();
if (onlyTo) {
  console.log(
    onlyTo.length
      ? `SMS_ONLY_TO is set: only ${onlyTo.join(', ')} get real SMS; messages to other numbers are only logged.`
      : 'SMS_ONLY_TO is set but has no valid Kenyan number, so no real SMS is sent at all.'
  );
}
if (account.balance && !isSandbox() && /^[A-Z]{3}\s*-?0+(\.0+)?$/.test(account.balance)) {
  console.warn('The balance is empty: top up the account, or every SMS will fail.');
}

if (number) {
  const phone = normalizePhone(number);
  if (!phone) {
    console.error(`"${number}" is not a Kenyan mobile number, e.g. 0712 345 678.`);
    process.exit(1);
  }
  if (!ready) process.exit(1);
  if (onlyTo && !onlyTo.includes(phone)) {
    console.error(`${phone} is not in SMS_ONLY_TO, so the server would not text it. Add it to SMS_ONLY_TO in backend/.env to test it.`);
    process.exit(1);
  }
  const out = await deliverSms(phone, 'Test message from the Vaccine Reminder system. SMS sending works.');
  if (out.status === 'sent') {
    console.log(`Test SMS accepted for ${phone}.${isSandbox() ? " Open the Africa's Talking simulator with this number to see it." : ''}`);
    if (provider === 'textbee') {
      console.log(
        'TextBee passes it to your phone, which sends it from its SIM; it should arrive within a minute. If it does not, open the ' +
          'TextBee app on that phone: it must be enabled, allowed to send SMS, and the SIM needs airtime or an SMS bundle.'
      );
    }
  } else {
    console.error(`Test SMS failed: ${smsError(out.response)}`);
    process.exit(1);
  }
} else {
  console.log('Nothing was sent. Add a phone number to send one test SMS: npm run check-sms -- 0712345678');
}
process.exit(ready ? 0 : 1);

// Lists the phones linked to the TextBee account and says which one will send. Returns false when
// none can.
function showTextbeePhones(devices) {
  if (!devices.length) {
    console.warn(
      'No phone is linked to this TextBee account yet. Install the TextBee app on the Android phone that will send the SMS, ' +
        'then in the textbee.dev dashboard click "Add device" and scan the QR code with the app.'
    );
    return false;
  }
  console.log('Phones linked to this TextBee account:');
  for (const d of devices) {
    const notes = [d.isDefault && 'default', !d.enabled && 'DISABLED', d.lastSeen && `last checked in ${new Date(d.lastSeen).toLocaleString()}`];
    console.log(`  ${d.name}  id ${d.id}${notes.some(Boolean) ? `  (${notes.filter(Boolean).join(', ')})` : ''}`);
  }
  const wanted = String(process.env.TEXTBEE_DEVICE_ID || '').trim();
  if (!wanted) {
    if (!devices.some((d) => d.enabled)) {
      console.warn('Every linked phone is disabled. Enable one in the TextBee app or dashboard.');
      return false;
    }
    console.log(
      `TEXTBEE_DEVICE_ID is not set, so TextBee picks the default phone (or the one online most recently). ` +
        `To always use one phone, put its id in backend/.env, e.g. TEXTBEE_DEVICE_ID=${devices[0].id}`
    );
    return true;
  }
  const chosen = devices.find((d) => d.id === wanted);
  if (!chosen) {
    console.warn(`TEXTBEE_DEVICE_ID=${wanted} is not one of the phones above. Copy the id of the phone you want into backend/.env.`);
    return false;
  }
  if (!chosen.enabled) {
    console.warn(`${chosen.name} (TEXTBEE_DEVICE_ID) is disabled. Enable it in the TextBee app or dashboard.`);
    return false;
  }
  console.log(`Messages go out from ${chosen.name}.`);
  return true;
}
