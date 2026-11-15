import { DatabaseSync } from 'node:sqlite';
import { BY_CODE, planDueDates } from './schedule.js';

const SCHEMA_VERSION = 2;

export function openDb(path = 'vaccine.db') {
  const db = new DatabaseSync(path);
  db.exec(`
    PRAGMA foreign_keys = ON;
    PRAGMA busy_timeout = 5000;
    CREATE TABLE IF NOT EXISTS children (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      child_name TEXT NOT NULL,
      caregiver_name TEXT NOT NULL,
      phone TEXT NOT NULL,
      dob TEXT NOT NULL,
      language TEXT NOT NULL DEFAULT 'en',
      facility TEXT NOT NULL DEFAULT '',
      consent INTEGER NOT NULL DEFAULT 1,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_children_phone ON children(phone);
    CREATE TABLE IF NOT EXISTS doses (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      code TEXT NOT NULL,
      name TEXT NOT NULL,
      due_date TEXT NOT NULL,
      given_date TEXT,
      UNIQUE(child_id, code)
    );
    CREATE INDEX IF NOT EXISTS idx_doses_pending ON doses(given_date, due_date);
    CREATE TABLE IF NOT EXISTS reminders (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      due_date TEXT NOT NULL,
      kind TEXT NOT NULL,
      status TEXT NOT NULL,
      sent_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      UNIQUE(child_id, due_date, kind)
    );
    CREATE TABLE IF NOT EXISTS sms_log (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      phone TEXT NOT NULL,
      message TEXT NOT NULL,
      status TEXT NOT NULL,
      response TEXT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE TABLE IF NOT EXISTS workers (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      name TEXT NOT NULL,
      phone TEXT NOT NULL UNIQUE,
      facility TEXT NOT NULL DEFAULT '',
      password_hash TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      last_login_at TEXT
    );
    CREATE TABLE IF NOT EXISTS sessions (
      token_hash TEXT PRIMARY KEY,
      worker_id INTEGER NOT NULL REFERENCES workers(id) ON DELETE CASCADE,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      expires_at TEXT NOT NULL
    );
  `);

  // Columns added after the first version. Adding them here upgrades an existing vaccine.db in place.
  addColumn(db, 'children', 'registered_by', 'INTEGER REFERENCES workers(id) ON DELETE SET NULL');
  addColumn(db, 'children', 'updated_at', 'TEXT');
  addColumn(db, 'sms_log', 'child_id', 'INTEGER REFERENCES children(id) ON DELETE SET NULL');
  addColumn(db, 'sms_log', 'kind', 'TEXT');
  addColumn(db, 'reminders', 'run_date', 'TEXT');
  addColumn(db, 'children', 'area_lat', 'REAL');
  addColumn(db, 'children', 'area_lon', 'REAL');
  addColumn(db, 'children', 'area_label', 'TEXT');
  addColumn(db, 'children', 'area_saved_at', 'TEXT');
  purgeExpiredAreas(db);
  db.exec('CREATE INDEX IF NOT EXISTS idx_sms_child ON sms_log(child_id, created_at)');

  const version = db.prepare('PRAGMA user_version').get().user_version;
  if (version < 2) {
    // Version 2 spaces doses in a series by a minimum interval, so re-plan existing children once.
    for (const { id } of db.prepare('SELECT id FROM children').all()) replanChild(db, id);
  }
  if (version < SCHEMA_VERSION) db.exec(`PRAGMA user_version = ${SCHEMA_VERSION}`);
  return db;
}

export const AREA_DAYS = 14;

export function purgeExpiredAreas(db) {
  db.prepare(
    `UPDATE children SET area_lat = NULL, area_lon = NULL, area_label = NULL, area_saved_at = NULL
     WHERE area_saved_at IS NOT NULL AND area_saved_at <= datetime('now', '-${AREA_DAYS} days')`
  ).run();
}

function addColumn(db, table, column, definition) {
  const exists = db.prepare(`PRAGMA table_info(${table})`).all().some((c) => c.name === column);
  if (!exists) db.exec(`ALTER TABLE ${table} ADD COLUMN ${column} ${definition}`);
}

// Recomputes the due dates of a child's doses that are not yet given, from the date of birth and
// the doses already given. Call after anything that changes those.
export function replanChild(db, childId) {
  const child = db.prepare('SELECT dob FROM children WHERE id = ?').get(childId);
  if (!child) return;
  const rows = db.prepare('SELECT id, code, due_date, given_date FROM doses WHERE child_id = ?').all(childId);
  const given = new Map(rows.filter((r) => r.given_date).map((r) => [r.code, r.given_date]));
  const plan = planDueDates(child.dob, given);
  const update = db.prepare('UPDATE doses SET due_date = ? WHERE id = ?');
  for (const r of rows) {
    if (!BY_CODE.has(r.code)) continue;
    const due = plan.get(r.code);
    if (due !== r.due_date) update.run(due, r.id);
  }
}
