import { openDb } from './db.js';
import { createApp, runReminders } from './app.js';
import { createOsmClient } from './facilities.js';
import { autoReminders, isSandbox, PROVIDER_NAMES, smsMode, smsOnlyTo, smsProvider } from './sms.js';
import { markDemoChildren } from './seed.js';
import { createDocumentStore, loadDocumentKey } from './documents.js';

const port = Number(process.env.PORT || 3000);
// Health workers need this code to create an account. HW_API_KEY is still read so old start commands work.
const registrationCode = process.env.REGISTRATION_CODE || process.env.HW_API_KEY || 'demo-registration-code';
if (!process.env.REGISTRATION_CODE && !process.env.HW_API_KEY) {
  console.warn('REGISTRATION_CODE not set, using "demo-registration-code". Set your own before sharing the server.');
}

if (!process.env.OSM_USER_AGENT) {
  console.warn('OSM_USER_AGENT not set. OpenStreetMap asks for a User-Agent with your contact email, e.g. "VaccineReminderKE/2.0 (you@example.com)".');
}

const dbPath = process.env.DB_PATH || 'vaccine.db';
const db = openDb(dbPath);
// The demo children's made-up numbers may belong to real people, so they are flagged and never get
// real SMS. Children registered in the app do. SMS_ONLY_TO can narrow that further to a few numbers.
const demoChildren = markDemoChildren(db);
const onlyTo = smsOnlyTo();
const limitNote = !onlyTo
  ? ''
  : onlyTo.length
    ? `, only to ${onlyTo.join(', ')} (SMS_ONLY_TO); messages to other numbers are only logged`
    : ', but SMS_ONLY_TO has no valid Kenyan number, so every message is only logged';
console.log(
  `SMS: ${smsMode() === 'test' ? 'test mode, messages are only logged' : isSandbox() ? "Africa's Talking sandbox (simulator only)" : `live, sent through ${PROVIDER_NAMES[smsProvider()]}${limitNote}`}`
);
const osm = createOsmClient();
// Vaccination evidence pictures: encrypted files in DOCUMENT_DIR, with the key in DOCUMENT_KEY or
// document.key. Neither is ever committed or served directly.
const documents = createDocumentStore({
  dir: process.env.DOCUMENT_DIR || 'documents',
  key: loadDocumentKey(process.env.DOCUMENT_KEY_FILE || 'document.key'),
});
const server = createApp(db, { registrationCode, log: true, osm, documents });
if (demoChildren && smsMode() === 'live') {
  console.log(`${demoChildren} demo children never get real SMS; children registered in the app do.`);
}
server.listen(port, () => console.log(`Vaccine reminder API on port ${port}`));

const HOUR = 3600000;
let lastRunDay = null;
console.log(autoReminders() ? 'Automatic reminders: every day after 08:00 Nairobi time' : 'Automatic reminders: off. SMS go out only when a health worker sends them from the app');
const timer = setInterval(async () => {
  if (!autoReminders()) return;
  const nairobi = new Date(Date.now() + 3 * HOUR);
  const day = nairobi.toISOString().slice(0, 10);
  if (nairobi.getUTCHours() >= 8 && lastRunDay !== day) {
    lastRunDay = day;
    try {
      const sent = await runReminders(db, undefined, { osm });
      console.log(`Daily reminders: ${sent.length} processed`);
    } catch (e) {
      console.error('Daily reminder run failed', e);
      lastRunDay = null;
    }
  }
}, 60000);

function shutdown() {
  clearInterval(timer);
  server.close(() => {
    db.close();
    process.exit(0);
  });
  setTimeout(() => process.exit(0), 3000).unref();
}
process.on('SIGINT', shutdown);
process.on('SIGTERM', shutdown);
