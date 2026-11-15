import { openDb } from './db.js';
import { createApp, runReminders } from './app.js';
import { createOsmClient } from './facilities.js';

const port = Number(process.env.PORT || 3000);
// Health workers need this code to create an account. HW_API_KEY is still read so old start commands work.
const registrationCode = process.env.REGISTRATION_CODE || process.env.HW_API_KEY || 'demo-registration-code';
if (!process.env.REGISTRATION_CODE && !process.env.HW_API_KEY) {
  console.warn('REGISTRATION_CODE not set, using "demo-registration-code". Set your own before sharing the server.');
}
if (!process.env.AT_API_KEY) console.warn('AT_API_KEY not set: SMS will be logged (dry-run), not sent.');

if (!process.env.OSM_USER_AGENT) {
  console.warn('OSM_USER_AGENT not set. OpenStreetMap asks for a User-Agent with your contact email, e.g. "VaccineReminderKE/2.0 (you@example.com)".');
}

const db = openDb(process.env.DB_PATH || 'vaccine.db');
const osm = createOsmClient();
const server = createApp(db, { registrationCode, log: true, osm });
server.listen(port, () => console.log(`Vaccine reminder API on port ${port}`));

const HOUR = 3600000;
let lastRunDay = null;
const timer = setInterval(async () => {
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
