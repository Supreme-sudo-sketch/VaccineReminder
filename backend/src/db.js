import { DatabaseSync } from 'node:sqlite';
import { BY_CODE, planDueDates } from './schedule.js';

// 3 added the vaccination verification tables. They are created with IF NOT EXISTS, so upgrading an
// older database only adds them and leaves every existing row as it was.
const SCHEMA_VERSION = 3;

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
    -- Notifications a health worker sends to the caregiver's app. The app collects them when it
    -- next checks in (there is no push service), and delivered_at records when that happened.
    CREATE TABLE IF NOT EXISTS app_notifications (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      worker_id INTEGER REFERENCES workers(id) ON DELETE SET NULL,
      message TEXT NOT NULL,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      delivered_at TEXT
    );
    CREATE INDEX IF NOT EXISTS idx_app_notifications_child ON app_notifications(child_id, id);
    -- Evidence from a caregiver that a dose was given somewhere else, waiting for a health worker
    -- to check it. One row per child and dose: a resubmission after a rejection updates the row,
    -- and verification_events keeps every step. "Not submitted" is simply the absence of a row.
    CREATE TABLE IF NOT EXISTS vaccination_verifications (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      dose_code TEXT NOT NULL,
      vaccination_date TEXT NOT NULL,
      hospital_name TEXT NOT NULL,
      doctor_name TEXT NOT NULL,
      card_document_id INTEGER REFERENCES verification_documents(id) ON DELETE SET NULL,
      note_document_id INTEGER REFERENCES verification_documents(id) ON DELETE SET NULL,
      status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'verified', 'rejected')),
      submitted_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
      submission_count INTEGER NOT NULL DEFAULT 1,
      reviewed_by INTEGER REFERENCES workers(id) ON DELETE SET NULL,
      reviewed_at TEXT,
      rejection_reason TEXT,
      UNIQUE(child_id, dose_code)
    );
    CREATE INDEX IF NOT EXISTS idx_verifications_status ON vaccination_verifications(status, submitted_at);
    -- Uploaded pictures. The file itself is encrypted on disk under storage_name; ref is the random
    -- id the caregiver's app uses before the picture is attached to a request.
    CREATE TABLE IF NOT EXISTS verification_documents (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      ref TEXT NOT NULL UNIQUE,
      child_id INTEGER NOT NULL REFERENCES children(id) ON DELETE CASCADE,
      verification_id INTEGER REFERENCES vaccination_verifications(id) ON DELETE CASCADE,
      kind TEXT NOT NULL CHECK (kind IN ('card', 'note')),
      mime_type TEXT NOT NULL,
      size INTEGER NOT NULL,
      sha256 TEXT NOT NULL,
      storage_name TEXT NOT NULL UNIQUE,
      uploaded_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_verification_documents_child ON verification_documents(child_id, verification_id);
    CREATE INDEX IF NOT EXISTS idx_verification_documents_hash ON verification_documents(sha256);
    -- Audit trail: submitted, resubmitted, approved, rejected. worker_id is null for caregiver steps.
    CREATE TABLE IF NOT EXISTS verification_events (
      id INTEGER PRIMARY KEY AUTOINCREMENT,
      verification_id INTEGER NOT NULL REFERENCES vaccination_verifications(id) ON DELETE CASCADE,
      action TEXT NOT NULL,
      worker_id INTEGER REFERENCES workers(id) ON DELETE SET NULL,
      detail TEXT,
      created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
    );
    CREATE INDEX IF NOT EXISTS idx_verification_events ON verification_events(verification_id, id);
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
  // Last time a caregiver's app checked for notifications for this child, so health workers can
  // tell whether a notification will reach anyone.
  addColumn(db, 'children', 'app_seen_at', 'TEXT');
  // Demo children have made-up phone numbers that may belong to real people: they never get real SMS.
  addColumn(db, 'children', 'is_demo', 'INTEGER NOT NULL DEFAULT 0');
  // The hospital nearest the family's home, named in SMS next to the clinic the child is registered at.
  addColumn(db, 'children', 'nearest_hospital', "TEXT NOT NULL DEFAULT ''");
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
