import { pathToFileURL } from 'node:url';
import { openDb, replanChild } from './db.js';
import { SCHEDULE, addDays, planDueDates, todayStr } from './schedule.js';
import { hashPassword } from './auth.js';
import { buildMessage } from './messages.js';
import { normalizePhone } from './sms.js';

export const DEMO_PASSWORD = 'Demo1234';

export const DEMO_WORKERS = [
  { name: 'Achieng Otieno', phone: '0700000001', facility: 'Kangemi Health Centre' },
  { name: 'Joseph Mwangi', phone: '0700000002', facility: 'Riruta Health Centre' },
];

const BIRTH = ['BCG', 'OPV0'];
const WEEK6 = ['OPV1', 'PENTA1', 'PCV1', 'ROTA1'];
const WEEK10 = ['OPV2', 'PENTA2', 'PCV2', 'ROTA2'];

export const DEMO_CHILDREN = [
  {
    childName: 'Neema Wambui',
    caregiverName: 'Grace Wambui',
    phone: '0711000101',
    ageDays: 3,
    language: 'en',
    given: [[BIRTH, 0]],
    shows: 'Newborn. Birth vaccines given, next visit at 6 weeks.',
  },
  {
    childName: 'Amani Kamau',
    caregiverName: 'Mary Kamau',
    phone: '0711000102',
    ageDays: 42,
    language: 'sw',
    given: [[BIRTH, 0]],
    shows: '6-week vaccines due today. Gets a "visit day" SMS in Kiswahili.',
  },
  {
    childName: 'Baraka Ochieng',
    caregiverName: 'Peter Ochieng',
    phone: '0711000103',
    ageDays: 39,
    language: 'en',
    given: [[BIRTH, 0]],
    shows: '6-week vaccines due in 3 days. Gets a "before" SMS.',
  },
  {
    childName: 'Zawadi Njeri',
    caregiverName: 'Lucy Njeri',
    phone: '0711000104',
    ageDays: 47,
    language: 'sw',
    given: [[BIRTH, 0]],
    history: [['before', 8], ['due', 5]],
    shows: '6-week vaccines 5 days overdue. Already had 2 SMS; next run sends the "missed" SMS.',
  },
  {
    childName: 'Imani Mutua',
    caregiverName: 'Ann Mutua',
    phone: '0711000105',
    ageDays: 63,
    language: 'sw',
    worker: 1,
    given: [],
    history: [['before', 24], ['due', 21], ['overdue', 18]],
    shows: 'Missed 3 weeks, birth vaccines never recorded. Try "Record past vaccines". Next run sends the second reminder.',
  },
  {
    childName: 'Jabari Kiprop',
    caregiverName: 'Ruth Kiprop',
    phone: '0711000106',
    ageDays: 120,
    language: 'en',
    given: [[BIRTH, 0], [WEEK6, 63], [WEEK10, 95]],
    shows: 'Pentavalent 1 given 3 weeks late, so doses 2 and 3 moved back (dose 3 due in 3 days). IPV has no series, so it kept its date and is overdue.',
  },
  {
    childName: 'Wanjiku Kariuki',
    caregiverName: 'Esther Kariuki',
    phone: '0711000107',
    ageDays: 300,
    language: 'en',
    onTime: true,
    shows: 'Up to date at 10 months. Next is Measles-Rubella 2 at 18 months.',
  },
  {
    childName: 'Otieno Odhiambo',
    caregiverName: 'Brian Odhiambo',
    phone: '0711000108',
    ageDays: 75,
    language: 'en',
    consent: false,
    given: [[BIRTH, 0], [WEEK6, 42]],
    shows: 'SMS consent withdrawn. 10-week vaccines overdue, shown in Follow-up with "No SMS consent"; never messaged.',
  },
  {
    childName: 'Faith Akinyi',
    caregiverName: 'Sarah Akinyi',
    phone: '0711000109',
    ageDays: 70,
    language: 'sw',
    given: [[BIRTH, 0], [WEEK6, 42]],
    shows: 'Twin. Same phone and date of birth as Hope, so one caregiver lookup shows both. 10-week vaccines due today.',
  },
  {
    childName: 'Hope Akinyi',
    caregiverName: 'Sarah Akinyi',
    phone: '0711000109',
    ageDays: 70,
    language: 'sw',
    given: [[BIRTH, 0], [WEEK6, 42]],
    shows: 'Twin of Faith.',
  },
  {
    childName: 'Kioko Musyoka',
    caregiverName: 'Janet Musyoka',
    phone: '0711000110',
    ageDays: 40,
    language: 'sw',
    given: [[BIRTH, 0]],
    area: { label: 'Mombasa', lat: -4.044, lon: 39.668 },
    shows: 'Travelling caregiver who saved Mombasa for reminders. Due in 2 days; the SMS lists 2 clinics near Mombasa.',
  },
  {
    childName: 'Malaika Chebet',
    caregiverName: 'Joyce Chebet',
    phone: '0711000111',
    ageDays: 760,
    language: 'en',
    worker: 1,
    onTime: true,
    shows: 'Two years old, every vaccine given.',
  },
];

function givenDates(dob, child, today) {
  const given = new Map();
  if (child.onTime) {
    for (const item of SCHEDULE) {
      const due = planDueDates(dob, given).get(item.code);
      if (due <= today) given.set(item.code, due);
    }
    return given;
  }
  for (const [codes, day] of child.given || []) for (const code of codes) given.set(code, addDays(dob, day));
  return given;
}

export async function seedDemo(db, today = todayStr()) {
  const hash = await hashPassword(DEMO_PASSWORD);
  const workerIds = DEMO_WORKERS.map((w) =>
    Number(
      db.prepare('INSERT INTO workers (name, phone, facility, password_hash) VALUES (?, ?, ?, ?)')
        .run(w.name, normalizePhone(w.phone), w.facility, hash).lastInsertRowid
    )
  );

  const created = [];
  db.exec('BEGIN');
  try {
    for (const c of DEMO_CHILDREN) {
      const worker = DEMO_WORKERS[c.worker || 0];
      const dob = addDays(today, -c.ageDays);
      const id = Number(
        db.prepare(
          `INSERT INTO children (child_name, caregiver_name, phone, dob, language, facility, consent, registered_by, created_at)
           VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)`
        ).run(
          c.childName,
          c.caregiverName,
          normalizePhone(c.phone),
          dob,
          c.language,
          worker.facility,
          c.consent === false ? 0 : 1,
          workerIds[c.worker || 0],
          `${dob} 09:00:00`
        ).lastInsertRowid
      );
      const insertDose = db.prepare('INSERT INTO doses (child_id, code, name, due_date) VALUES (?, ?, ?, ?)');
      for (const item of SCHEDULE) insertDose.run(id, item.code, item.name, '');
      const setGiven = db.prepare('UPDATE doses SET given_date = ? WHERE child_id = ? AND code = ?');
      for (const [code, date] of givenDates(dob, c, today)) setGiven.run(date, id, code);
      replanChild(db, id);

      if (c.area) {
        db.prepare('UPDATE children SET area_lat = ?, area_lon = ?, area_label = ?, area_saved_at = CURRENT_TIMESTAMP WHERE id = ?')
          .run(c.area.lat, c.area.lon, c.area.label, id);
      }

      for (const [kind, daysAgo] of c.history || []) {
        const pending = db
          .prepare('SELECT name, due_date FROM doses WHERE child_id = ? AND given_date IS NULL ORDER BY due_date, id')
          .all(id)
          .filter((d) => d.due_date <= today && d.due_date >= addDays(today, -60));
        const dueDate = pending.length ? pending[pending.length - 1].due_date : today;
        const vaccines = pending.filter((d) => d.due_date === dueDate).map((d) => d.name);
        const sentOn = addDays(today, -daysAgo);
        const message = buildMessage(kind, c.language, c.childName, vaccines, dueDate, worker.facility);
        db.prepare(
          "INSERT INTO sms_log (phone, message, status, response, child_id, kind, created_at) VALUES (?, ?, 'dry-run', 'demo data', ?, ?, ?)"
        ).run(normalizePhone(c.phone), message, id, kind, `${sentOn} 05:00:00`);
        db.prepare(
          "INSERT INTO reminders (child_id, due_date, kind, status, run_date, sent_at) VALUES (?, ?, ?, 'dry-run', ?, ?)"
        ).run(id, dueDate, kind, sentOn, `${sentOn} 05:00:00`);
      }
      created.push({ ...c, id, dob });
    }
    db.exec('COMMIT');
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
  return { workers: DEMO_WORKERS, children: created };
}

function clearAll(db) {
  db.exec('BEGIN');
  try {
    for (const table of ['sessions', 'reminders', 'sms_log', 'doses', 'children', 'workers']) db.exec(`DELETE FROM ${table}`);
    db.exec('COMMIT');
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}

async function main() {
  if (process.env.AT_API_KEY && process.env.AT_USERNAME !== 'sandbox') {
    console.error('Refusing to load demo data while live SMS keys are set: the server would text these made-up numbers.');
    process.exit(1);
  }
  const path = process.env.DB_PATH || 'vaccine.db';
  const db = openDb(path);
  const existing = db.prepare('SELECT (SELECT COUNT(*) FROM children) + (SELECT COUNT(*) FROM workers) AS n').get().n;
  if (existing > 0 && !process.argv.includes('--reset')) {
    console.error(`${path} already has data. Run "npm run seed -- --reset" to delete everything in it and load the demo data.`);
    process.exit(1);
  }
  if (existing > 0) clearAll(db);

  const { workers, children } = await seedDemo(db);
  db.close();

  console.log(`Demo data loaded into ${path} for ${todayStr()}. All names and phone numbers are made up.\n`);
  console.log(`Health worker logins (password for both: ${DEMO_PASSWORD})`);
  for (const w of workers) console.log(`  ${w.phone}  ${w.name}, ${w.facility}`);
  console.log('\nCaregiver lookups (phone number + child date of birth)');
  for (const c of children) {
    console.log(`  ${c.phone}  ${c.dob}  ${c.childName.padEnd(16)} ${c.shows}`);
  }
  console.log('\nThe dates are relative to today. Run "npm run seed -- --reset" again on another day to refresh them.');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main().catch((e) => {
    console.error(e);
    process.exit(1);
  });
}
