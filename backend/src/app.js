import http from 'node:http';
import { SCHEDULE, BY_CODE, milestoneOf, previousInSeries, parseDate, daysBetween, addDays, todayStr } from './schedule.js';
import { buildMessage, KINDS, kindFor, nearbyClinicsText, verificationMessage } from './messages.js';
import { autoReminders, normalizePhone, refreshQueuedSms, sendSms, smsError, smsMode } from './sms.js';
import { replanChild, purgeExpiredAreas, AREA_DAYS } from './db.js';
import { createOsmClient, OsmError, OSM_ATTRIBUTION, MAX_SEARCH_KM } from './facilities.js';
import { createFacilityIndex, loadFacilityList } from './facilityList.js';
import { hashPassword, verifyPassword, passwordProblem, newToken, tokenHash, safeEqual, makeLimiter, SESSION_DAYS } from './auth.js';
import { DOCUMENT_TYPES, MAX_DOCUMENT_BYTES, documentProblem, sha256 } from './documents.js';

class HttpError extends Error {
  constructor(status, message, { code, field } = {}) {
    super(message);
    this.status = status;
    this.code = code;
    this.field = field;
  }
}

const invalid = (message, field) => new HttpError(400, message, { code: 'invalid', field });
const PHONE_HINT = 'Enter a valid Kenyan phone number, e.g. 0712 345 678';
const MAX_NOTIFICATION_LENGTH = 500;
// Pictures uploaded but never attached to a request are deleted after this long.
const UNUSED_DOCUMENT_HOURS = 24;
const MAX_UNUSED_DOCUMENTS = 10;

// The words of a name, for comparing what a caregiver types with what the clinic registered:
// case, accents, punctuation and extra spaces do not matter.
function nameWords(value) {
  return String(value ?? '')
    .toLowerCase()
    .normalize('NFKD')
    .replace(/[\u0300-\u036f]/g, '')
    .replace(/[^\p{L}\p{N}\s]/gu, ' ')
    .split(/\s+/)
    .filter(Boolean);
}

// True when every word the caregiver typed is part of the registered name, in any order: "Amani"
// or "otieno amani" find "Amani Otieno", but "Amani Kamau" does not.
export function nameMatches(typed, registered) {
  const words = nameWords(typed);
  const known = new Set(nameWords(registered));
  return words.length > 0 && words.every((w) => known.has(w));
}

function clean(value, max) {
  return String(value ?? '').trim().replace(/\s+/g, ' ').slice(0, max);
}

function transaction(db, fn) {
  db.exec('BEGIN');
  try {
    const out = fn();
    db.exec('COMMIT');
    return out;
  } catch (e) {
    db.exec('ROLLBACK');
    throw e;
  }
}

function doseStatus(row, today) {
  if (row.given_date) return 'given';
  const diff = daysBetween(today, row.due_date);
  if (diff < 0) return 'overdue';
  if (diff === 0) return 'due';
  return 'upcoming';
}

function areaView(child) {
  if (child.area_lat == null || child.area_lon == null || !child.area_saved_at) return null;
  const savedAt = Date.parse(child.area_saved_at.replace(' ', 'T') + 'Z');
  return {
    label: child.area_label || '',
    lat: child.area_lat,
    lon: child.area_lon,
    expiresOn: new Date(savedAt + AREA_DAYS * 86400000).toISOString().slice(0, 10),
  };
}

function withoutClinics(message) {
  return String(message).split('\n')[0];
}

async function clinicsSuffix(osm, child, lang, state) {
  if (!osm || state.osmDown || child.area_lat == null || child.area_lon == null) return '';
  try {
    const { facilities, kind } = await osm.nearbyFacilities(child.area_lat, child.area_lon, 2);
    return nearbyClinicsText(lang, facilities, kind);
  } catch {
    state.osmDown = true;
    return '';
  }
}

function coordinate(value, max) {
  const n = typeof value === 'number' ? value : typeof value === 'string' && value.trim() !== '' ? Number(value) : NaN;
  return Number.isFinite(n) && Math.abs(n) <= max ? n : null;
}

function childView(db, child, today, { history = false, area = false } = {}) {
  // The evidence sent for each dose, shown under that dose in the schedule. Only health workers
  // (history views) get the document ids, which they need to show the pictures.
  const evidence = new Map(
    db
      .prepare('SELECT * FROM vaccination_verifications WHERE child_id = ?')
      .all(child.id)
      .map((v) => [
        v.dose_code,
        {
          id: v.id,
          status: v.status,
          rejectionReason: v.rejection_reason || null,
          vaccinationDate: v.vaccination_date,
          hospital: v.hospital_name,
          submittedAt: v.submitted_at,
          hasCard: v.card_document_id != null,
          hasNote: v.note_document_id != null,
          ...(history
            ? {
                documents: [
                  v.card_document_id != null && { id: v.card_document_id, kind: 'card' },
                  v.note_document_id != null && { id: v.note_document_id, kind: 'note' },
                ].filter(Boolean),
              }
            : {}),
        },
      ])
  );
  const doses = db
    .prepare('SELECT code, name, due_date, given_date FROM doses WHERE child_id = ? ORDER BY id')
    .all(child.id)
    .map((d) => ({
      code: d.code,
      name: d.name,
      milestone: milestoneOf(BY_CODE.get(d.code)),
      dueDate: d.due_date,
      givenDate: d.given_date,
      status: doseStatus(d, today),
      verification: evidence.get(d.code) || null,
    }));
  const pending = doses.filter((d) => d.status !== 'given');
  const nextDueDate = pending.reduce((min, d) => (!min || d.dueDate < min ? d.dueDate : min), null);
  const view = {
    id: child.id,
    childName: child.child_name,
    caregiverName: child.caregiver_name,
    phone: child.phone,
    dob: child.dob,
    language: child.language,
    facility: child.facility,
    nearestHospital: child.nearest_hospital || '',
    consent: child.consent === 1,
    nextDueDate,
    nextVaccines: pending.filter((d) => d.dueDate === nextDueDate).map((d) => d.name),
    overdueCount: doses.filter((d) => d.status === 'overdue').length,
    givenCount: doses.length - pending.length,
    totalCount: doses.length,
    doses,
  };
  if (history) {
    view.smsHistory = db
      .prepare('SELECT kind, status, message, created_at FROM sms_log WHERE child_id = ? ORDER BY id DESC LIMIT 10')
      .all(child.id)
      .map((r) => ({ kind: r.kind || '', status: r.status, message: withoutClinics(r.message), sentAt: r.created_at }));
    view.appNotifications = db
      .prepare(
        `SELECT n.message, n.created_at, n.delivered_at, w.name AS worker_name
         FROM app_notifications n LEFT JOIN workers w ON w.id = n.worker_id
         WHERE n.child_id = ? ORDER BY n.id DESC LIMIT 10`
      )
      .all(child.id)
      .map(notificationView);
    view.appSeenAt = child.app_seen_at || null;
    // Brothers, sisters and twins: other children registered with the same parent's phone number.
    view.siblings = db
      .prepare('SELECT id, child_name, dob FROM children WHERE phone = ? AND id != ? ORDER BY dob, id')
      .all(child.phone, child.id)
      .map((r) => ({ id: r.id, childName: r.child_name, dob: r.dob, twin: r.dob === child.dob }));
  }
  if (area) view.savedArea = areaView(child);
  return view;
}

function notificationView(r) {
  return { message: r.message, sentAt: r.created_at, deliveredAt: r.delivered_at || null, sentBy: r.worker_name || null };
}

function summaryView(r) {
  return {
    id: r.id,
    childName: r.child_name,
    caregiverName: r.caregiver_name,
    phone: r.phone,
    dob: r.dob,
    language: r.language,
    facility: r.facility,
    consent: r.consent === 1,
    nextDueDate: r.next_due,
    overdueCount: r.overdue_count,
    givenCount: r.given_count,
    totalCount: r.total_count,
  };
}

function workerView(w) {
  return { id: w.id, name: w.name, phone: w.phone, facility: w.facility };
}

export async function runReminders(db, today = todayStr(), { osm = null } = {}) {
  purgeExpiredAreas(db);
  const rows = db
    .prepare(
      `SELECT c.id AS child_id, c.child_name, c.phone, c.language, c.facility, c.nearest_hospital, c.area_lat, c.area_lon, d.due_date, d.name AS dose_name
       FROM doses d JOIN children c ON c.id = d.child_id
       WHERE d.given_date IS NULL AND c.consent = 1 AND d.due_date BETWEEN ? AND ?
       ORDER BY c.id, d.due_date, d.id`
    )
    .all(addDays(today, Math.min(...KINDS.map((k) => k.from))), addDays(today, Math.max(...KINDS.map((k) => k.to))));

  const byChild = new Map();
  for (const r of rows) {
    if (!byChild.has(r.child_id)) byChild.set(r.child_id, new Map());
    const groups = byChild.get(r.child_id);
    if (!groups.has(r.due_date)) groups.set(r.due_date, { ...r, vaccines: [] });
    groups.get(r.due_date).vaccines.push(r.dose_name);
  }

  const existing = db.prepare('SELECT status FROM reminders WHERE child_id = ? AND due_date = ? AND kind = ?');
  const remindedToday = db.prepare("SELECT 1 FROM reminders WHERE child_id = ? AND run_date = ? AND status != 'failed'");
  const manualRecently = db.prepare(
    "SELECT 1 FROM sms_log WHERE child_id = ? AND kind = 'manual' AND status != 'failed' AND created_at > datetime('now', '-12 hours')"
  );
  const save = db.prepare(
    `INSERT INTO reminders (child_id, due_date, kind, status, run_date) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(child_id, due_date, kind) DO UPDATE SET status = excluded.status, run_date = excluded.run_date, sent_at = CURRENT_TIMESTAMP`
  );

  const results = [];
  const osmState = { osmDown: false };
  // Wait for the SMS phone to confirm each message, until it leaves one waiting: then it is off or
  // asleep, and waiting again for every child would only slow the run down.
  let phoneAnswers = true;
  for (const [childId, groups] of byChild) {
    // One SMS per child per day: several messages at once are confusing and cost more.
    if (remindedToday.get(childId, today) || manualRecently.get(childId)) continue;
    let pick = null;
    for (const g of groups.values()) {
      const k = kindFor(daysBetween(today, g.due_date));
      if (!k) continue;
      const prior = existing.get(childId, g.due_date, k.kind);
      if (prior && prior.status !== 'failed') continue;
      const rank = KINDS.indexOf(k);
      if (!pick || rank < pick.rank) pick = { g, kind: k.kind, rank };
    }
    if (!pick) continue;

    const { g, kind } = pick;
    const message =
      buildMessage(kind, g.language, g.child_name, g.vaccines, g.due_date, g.facility, g.nearest_hospital) +
      (await clinicsSuffix(osm, g, g.language, osmState));
    const out = await sendSms(db, g.phone, message, { childId, kind, confirm: phoneAnswers });
    if (out.status === 'queued') phoneAnswers = false;
    save.run(childId, g.due_date, kind, out.status, today);
    results.push({ childId, childName: g.child_name, kind, dueDate: g.due_date, status: out.status });
  }
  return results;
}

// The body as bytes, for picture uploads. Refuses anything over `max` before reading it when the
// client says how big it is.
async function readRaw(req, max) {
  const tooBig = () => new HttpError(413, `The file is larger than ${max / 1024 / 1024} MB`, { code: 'file_too_large' });
  if (Number(req.headers['content-length']) > max) throw tooBig();
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > max) throw tooBig();
    chunks.push(c);
  }
  return Buffer.concat(chunks);
}

async function readBody(req) {
  const chunks = [];
  let size = 0;
  for await (const c of req) {
    size += c.length;
    if (size > 100_000) throw new HttpError(413, 'Body too large');
    chunks.push(c);
  }
  if (!chunks.length) return {};
  try {
    const body = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    if (!body || typeof body !== 'object' || Array.isArray(body)) throw new Error('not an object');
    return body;
  } catch {
    throw new HttpError(400, 'Invalid JSON');
  }
}

export function createApp(
  db,
  { registrationCode, clock = todayStr, log = false, osm = createOsmClient(), facilityList = loadFacilityList(), documents = null } = {}
) {
  const facilityIndex = createFacilityIndex(facilityList);
  const lookupLimiter = makeLimiter(20, 60_000);
  const osmLimiter = makeLimiter(30, 60_000);
  // Searched as the health worker types, so more generous than the OpenStreetMap endpoints.
  const facilityListLimiter = makeLimiter(120, 60_000);
  const failedLookups = makeLimiter(10, 3_600_000);
  const authLimiter = makeLimiter(10, 60_000);
  const failedLogins = makeLimiter(5, 15 * 60_000);
  const uploadLimiter = makeLimiter(30, 60_000);
  // Per child, so one caregiver cannot flood the review list.
  const submitLimiter = makeLimiter(10, 3_600_000);

  function limit(limiter, key) {
    if (!limiter.hit(key)) throw new HttpError(429, 'Too many requests, try again in a minute', { code: 'rate_limited' });
  }

  function findChild(id) {
    const child = db.prepare('SELECT * FROM children WHERE id = ?').get(id);
    if (!child) throw new HttpError(404, 'Child not found', { code: 'not_found' });
    return child;
  }

  function authWorker(req) {
    const m = /^Bearer\s+(\S+)$/i.exec(req.headers.authorization || '');
    if (!m) throw new HttpError(401, 'Please log in first', { code: 'auth_required' });
    const hash = tokenHash(m[1]);
    const worker = db
      .prepare(
        `SELECT w.* FROM sessions s JOIN workers w ON w.id = s.worker_id
         WHERE s.token_hash = ? AND s.expires_at > datetime('now')`
      )
      .get(hash);
    if (!worker) throw new HttpError(401, 'Your session has expired. Please log in again', { code: 'session_expired' });
    return { worker, tokenHash: hash };
  }

  function startSession(worker) {
    const token = newToken();
    db.prepare("DELETE FROM sessions WHERE expires_at <= datetime('now')").run();
    db.prepare(`INSERT INTO sessions (token_hash, worker_id, expires_at) VALUES (?, ?, datetime('now', '+${SESSION_DAYS} days'))`).run(
      tokenHash(token),
      worker.id
    );
    return { token, worker: workerView(worker) };
  }

  // Validates the child fields present in `b`. With `partial`, missing fields are left out.
  function childFields(b, today, partial) {
    const has = (k) => !partial || b[k] !== undefined;
    const out = {};
    if (has('childName')) {
      out.child_name = clean(b.childName, 80);
      if (!out.child_name) throw invalid("Enter the child's name", 'childName');
    }
    if (has('dob')) {
      if (!parseDate(b.dob)) throw invalid('Date of birth must be YYYY-MM-DD', 'dob');
      if (b.dob > today) throw invalid('Date of birth cannot be in the future', 'dob');
      if (b.dob < `${+today.slice(0, 4) - 5}${today.slice(4)}`) throw invalid('Date of birth must be within the last 5 years', 'dob');
      out.dob = b.dob;
    }
    if (has('caregiverName')) {
      out.caregiver_name = clean(b.caregiverName, 80);
      if (!out.caregiver_name) throw invalid("Enter the parent or caregiver's name", 'caregiverName');
    }
    if (has('phone')) {
      out.phone = normalizePhone(b.phone);
      if (!out.phone) throw invalid(PHONE_HINT, 'phone');
    }
    if (has('language')) out.language = b.language === 'sw' ? 'sw' : 'en';
    if (b.facility !== undefined) out.facility = clean(b.facility, 120);
    if (b.nearestHospital !== undefined) out.nearest_hospital = clean(b.nearestHospital, 120);
    if (partial && b.consent !== undefined) {
      if (typeof b.consent !== 'boolean') throw invalid('Consent must be true or false', 'consent');
      out.consent = b.consent ? 1 : 0;
    }
    return out;
  }

  function assertNotDuplicate(phone, dob, childName, exceptId = 0) {
    const dup = db
      .prepare('SELECT id FROM children WHERE phone = ? AND dob = ? AND lower(child_name) = lower(?) AND id != ?')
      .get(phone, dob, childName, exceptId);
    if (dup) throw new HttpError(409, 'This child is already registered', { code: 'duplicate_child' });
  }

  // Checks a date a dose was given against the date of birth, today, and the other doses in its series.
  function checkGivenDate(child, code, date, given, today) {
    const item = BY_CODE.get(code);
    const label = item ? item.name : code;
    if (!parseDate(date)) throw invalid('Date must be YYYY-MM-DD', 'date');
    if (date > today) throw invalid('Date given cannot be in the future', 'date');
    if (date < child.dob) throw invalid('Date given cannot be before date of birth', 'date');
    const prev = previousInSeries(code);
    if (prev && given.get(prev.code) && date < given.get(prev.code)) {
      throw invalid(`${label} cannot be given before ${prev.name} (given ${given.get(prev.code)})`, 'date');
    }
    const next = SCHEDULE.find((s) => previousInSeries(s.code)?.code === code);
    if (next && given.get(next.code) && date > given.get(next.code)) {
      throw invalid(`${label} cannot be given after ${next.name} (given ${given.get(next.code)})`, 'date');
    }
  }

  // Caregivers prove who they are with the phone number, the child's name and the child's date of
  // birth, the details the clinic registered. Wrong guesses count against the phone number. Twins
  // share a phone number and date of birth, so each is found by their own name.
  function checkCaregiver(rawPhone, childDob, childName) {
    const phone = normalizePhone(rawPhone);
    if (!phone) throw invalid(PHONE_HINT, 'phone');
    if (!nameWords(childName).length) throw invalid("Enter the child's name", 'childName');
    if (!parseDate(childDob)) throw invalid("Choose the child's date of birth", 'childDob');
    if (failedLookups.count(phone) >= 10) {
      throw new HttpError(429, 'Too many attempts for this phone number. Try again in an hour', { code: 'rate_limited' });
    }
    purgeExpiredAreas(db);
    const kids = db
      .prepare('SELECT * FROM children WHERE phone = ? AND dob = ? ORDER BY id')
      .all(phone, childDob)
      .filter((k) => nameMatches(childName, k.child_name));
    if (!kids.length) {
      failedLookups.hit(phone);
      throw new HttpError(404, 'No child found with that phone number, name and date of birth', { code: 'not_found' });
    }
    return { phone, kids };
  }

  async function verifyCaregiver(req, ip) {
    limit(lookupLimiter, ip);
    const b = await readBody(req);
    return { b, ...checkCaregiver(b.phone, b.childDob, b.childName) };
  }

  // One of the caregiver's own children, or 404.
  function caregiverChild(kids, id) {
    const child = kids.find((k) => k.id === Number(id));
    if (!child) throw new HttpError(404, 'Child not found', { code: 'not_found' });
    return child;
  }

  async function fromOsm(call) {
    try {
      return await call();
    } catch (e) {
      if (e instanceof OsmError) {
        throw new HttpError(502, 'Clinic search is not available right now. Try again later', { code: 'osm_unavailable' });
      }
      throw e;
    }
  }

  // The reminder a health worker sends by hand: the earliest vaccines not yet given, worded for how
  // far away (or late) they are. Null when every vaccine is given.
  function manualReminder(child, today) {
    const pending = db
      .prepare('SELECT name, due_date FROM doses WHERE child_id = ? AND given_date IS NULL ORDER BY due_date, id')
      .all(child.id);
    if (!pending.length) return null;
    const first = pending[0].due_date;
    const same = pending.filter((p) => p.due_date === first).map((p) => p.name);
    const diff = daysBetween(today, first);
    const kind = diff < 0 ? 'overdue' : diff === 0 ? 'due' : 'before';
    return { kind, message: buildMessage(kind, child.language, child.child_name, same, first, child.facility, child.nearest_hospital) };
  }

  function givenMap(childId) {
    const rows = db.prepare('SELECT code, given_date FROM doses WHERE child_id = ? AND given_date IS NOT NULL').all(childId);
    return new Map(rows.map((r) => [r.code, r.given_date]));
  }

  // ----- Vaccination verification -----

  function requireDocuments() {
    if (!documents) throw new HttpError(503, 'Document storage is not set up on this server', { code: 'no_storage' });
    return documents;
  }

  // Deletes pictures that were uploaded but never sent with a request (the caregiver gave up).
  function purgeUnusedDocuments() {
    if (!documents) return;
    const old = db
      .prepare(
        `SELECT id, storage_name FROM verification_documents
         WHERE verification_id IS NULL AND uploaded_at <= datetime('now', '-${UNUSED_DOCUMENT_HOURS} hours')`
      )
      .all();
    for (const d of old) {
      db.prepare('DELETE FROM verification_documents WHERE id = ?').run(d.id);
      documents.remove(d.storage_name);
    }
  }

  const VERIFICATION_SELECT = `SELECT v.*, c.child_name, c.dob, c.caregiver_name, c.phone, c.facility AS child_facility, c.consent,
      w.name AS reviewer_name, w.facility AS reviewer_facility
    FROM vaccination_verifications v JOIN children c ON c.id = v.child_id LEFT JOIN workers w ON w.id = v.reviewed_by`;

  function verificationRow(id) {
    const row = db.prepare(`${VERIFICATION_SELECT} WHERE v.id = ?`).get(id);
    if (!row) throw new HttpError(404, 'Verification request not found', { code: 'not_found' });
    return row;
  }

  // What the caregiver sees about their own request. The pictures themselves are never sent back.
  function caregiverVerificationView(r) {
    return {
      id: r.id,
      childId: r.child_id,
      childName: r.child_name,
      doseCode: r.dose_code,
      doseName: BY_CODE.get(r.dose_code)?.name || r.dose_code,
      vaccinationDate: r.vaccination_date,
      hospital: r.hospital_name,
      doctorName: r.doctor_name,
      status: r.status,
      submittedAt: r.submitted_at,
      submissionCount: r.submission_count,
      reviewedAt: r.reviewed_at || null,
      rejectionReason: r.rejection_reason || null,
      hasCard: r.card_document_id != null,
      hasNote: r.note_document_id != null,
    };
  }

  function workerVerificationView(r) {
    return {
      ...caregiverVerificationView(r),
      milestone: milestoneOf(BY_CODE.get(r.dose_code)),
      reviewedBy: r.reviewer_name || null,
      documentCount: (r.card_document_id != null) + (r.note_document_id != null),
      child: {
        id: r.child_id,
        childName: r.child_name,
        dob: r.dob,
        caregiverName: r.caregiver_name,
        phone: r.phone,
        facility: r.child_facility,
        consent: r.consent === 1,
      },
    };
  }

  // Everything a health worker needs to decide: the request, the pictures, the dose as recorded now,
  // anything that looks wrong, and the history of the request.
  function verificationDetail(id, today, viewer) {
    const r = verificationRow(id);
    const view = workerVerificationView(r);
    const child = findChild(r.child_id);
    const dose = db.prepare('SELECT * FROM doses WHERE child_id = ? AND code = ?').get(r.child_id, r.dose_code);
    view.dose = dose ? { dueDate: dose.due_date, givenDate: dose.given_date, status: doseStatus(dose, today) } : null;
    view.documents = db
      .prepare('SELECT id, kind, mime_type, size, uploaded_at FROM verification_documents WHERE id IN (?, ?) ORDER BY kind')
      .all(r.card_document_id ?? -1, r.note_document_id ?? -1)
      .map((d) => ({ id: d.id, kind: d.kind, mimeType: d.mime_type, size: d.size, uploadedAt: d.uploaded_at }));
    view.history = db
      .prepare(
        `SELECT e.action, e.detail, e.created_at, w.name AS worker_name FROM verification_events e
         LEFT JOIN workers w ON w.id = e.worker_id WHERE e.verification_id = ? ORDER BY e.id`
      )
      .all(r.id)
      .map((e) => ({ action: e.action, at: e.created_at, by: e.worker_name || null, detail: e.detail || null }));

    const warnings = [];
    if (r.status === 'pending') {
      if (viewer && viewer.phone === r.phone) {
        warnings.push({ code: 'own_child', text: 'This child is registered with your own phone number, so another health worker must review it' });
      }
      if (dose && dose.given_date) {
        warnings.push({ code: 'already_given', text: `${view.doseName} is already recorded as given on ${dose.given_date}` });
      } else if (dose) {
        const given = givenMap(r.child_id);
        try {
          checkGivenDate(child, r.dose_code, r.vaccination_date, given, today);
          if (r.vaccination_date < dose.due_date) {
            warnings.push({ code: 'early', text: `The date is before the recommended date for this dose (${dose.due_date})` });
          }
        } catch (e) {
          if (!(e instanceof HttpError)) throw e;
          warnings.push({ code: 'date_conflict', text: e.message });
        }
      }
      const reused = db
        .prepare(
          `SELECT 1 FROM verification_documents
           WHERE sha256 IN (SELECT sha256 FROM verification_documents WHERE id IN (?, ?)) AND child_id != ? LIMIT 1`
        )
        .get(r.card_document_id ?? -1, r.note_document_id ?? -1, r.child_id);
      if (reused) warnings.push({ code: 'reused_document', text: 'The same picture was also sent for another child' });
    }
    view.warnings = warnings;
    return view;
  }

  // Saves a caregiver's evidence for one dose, or replaces the evidence of a rejected request. The
  // dose itself is not touched: only a health worker's approval records it as given.
  function submitEvidence(child, code, b, existing, today) {
    const dose = BY_CODE.has(code) ? db.prepare('SELECT * FROM doses WHERE child_id = ? AND code = ?').get(child.id, code) : null;
    if (!dose) throw invalid('Choose the vaccine and dose', 'doseCode');
    if (dose.given_date) {
      throw new HttpError(409, `${dose.name} is already recorded as given`, { code: 'already_given', field: 'doseCode' });
    }
    if (existing && existing.status === 'pending') {
      throw new HttpError(409, 'Evidence for this vaccine is already waiting for review', { code: 'already_pending', field: 'doseCode' });
    }
    const date = b.vaccinationDate;
    if (!parseDate(date)) throw invalid('Choose the date the vaccine was given', 'vaccinationDate');
    if (date > today) throw invalid('The vaccination date cannot be in the future', 'vaccinationDate');
    if (date < child.dob) throw invalid('The vaccination date cannot be before the date of birth', 'vaccinationDate');
    const hospital = clean(b.hospital, 120);
    if (hospital.length < 2) throw invalid('Enter the hospital or clinic where the vaccine was given', 'hospital');
    const doctor = clean(b.doctorName, 80);
    if (doctor.length < 2) throw invalid("Enter the doctor's or health worker's name", 'doctorName');

    const refs = { card: b.cardDocument, note: b.noteDocument };
    if (!refs.card && !refs.note) {
      throw new HttpError(400, "Add a photo of the vaccination card or a doctor's note", { code: 'no_document', field: 'documents' });
    }
    const docs = [];
    for (const kind of ['card', 'note']) {
      if (!refs[kind]) continue;
      const doc = db.prepare('SELECT * FROM verification_documents WHERE ref = ?').get(String(refs[kind]));
      if (!doc || doc.child_id !== child.id || doc.kind !== kind || doc.verification_id !== null) {
        throw new HttpError(400, 'An uploaded photo was not found. Please upload it again', { code: 'bad_document', field: kind });
      }
      docs.push(doc);
    }
    limit(submitLimiter, `child:${child.id}`);

    const cardId = docs.find((d) => d.kind === 'card')?.id ?? null;
    const noteId = docs.find((d) => d.kind === 'note')?.id ?? null;
    return transaction(db, () => {
      let id;
      if (existing) {
        id = existing.id;
        db.prepare(
          `UPDATE vaccination_verifications SET vaccination_date = ?, hospital_name = ?, doctor_name = ?, card_document_id = ?,
             note_document_id = ?, status = 'pending', submitted_at = CURRENT_TIMESTAMP, submission_count = submission_count + 1,
             reviewed_by = NULL, reviewed_at = NULL, rejection_reason = NULL
           WHERE id = ?`
        ).run(date, hospital, doctor, cardId, noteId, id);
      } else {
        id = Number(
          db
            .prepare(
              `INSERT INTO vaccination_verifications (child_id, dose_code, vaccination_date, hospital_name, doctor_name, card_document_id, note_document_id)
               VALUES (?, ?, ?, ?, ?, ?, ?)`
            )
            .run(child.id, code, date, hospital, doctor, cardId, noteId).lastInsertRowid
        );
      }
      const attach = db.prepare('UPDATE verification_documents SET verification_id = ? WHERE id = ?');
      for (const d of docs) attach.run(id, d.id);
      db.prepare('INSERT INTO verification_events (verification_id, action) VALUES (?, ?)').run(id, existing ? 'resubmitted' : 'submitted');
      return verificationRow(id);
    });
  }

  purgeUnusedDocuments();

  async function route(req) {
    const url = new URL(req.url, 'http://x');
    const path = url.pathname.replace(/\/+$/, '') || '/';
    // Android's HttpURLConnection cannot send PATCH, so the app sends POST with this header instead.
    const override = String(req.headers['x-http-method-override'] || '').toUpperCase();
    const method = req.method === 'POST' && ['PATCH', 'DELETE'].includes(override) ? override : req.method;
    const ip = req.socket.remoteAddress || 'unknown';
    const today = clock();
    let m;

    if (method === 'GET' && path === '/health') return { ok: true, today, smsMode: smsMode() };

    if (method === 'POST' && path === '/caregiver/lookup') {
      const { kids } = await verifyCaregiver(req, ip);
      return { children: kids.map((k) => childView(db, k, today, { area: true })) };
    }

    if (method === 'POST' && path === '/caregiver/location') {
      const { b, kids } = await verifyCaregiver(req, ip);
      const ids = kids.map((k) => k.id);
      const marks = ids.map(() => '?').join(',');
      if (b.lat === null) {
        db.prepare(
          `UPDATE children SET area_lat = NULL, area_lon = NULL, area_label = NULL, area_saved_at = NULL WHERE id IN (${marks})`
        ).run(...ids);
        return { savedArea: null, updated: kids.length };
      }
      const lat = coordinate(b.lat, 90);
      const lon = coordinate(b.lon, 180);
      if (lat === null || lon === null) {
        throw invalid('Send valid lat and lon, or lat null to delete the saved area', lat === null ? 'lat' : 'lon');
      }
      const round = (n) => Math.round(n * 1000) / 1000;
      db.prepare(
        `UPDATE children SET area_lat = ?, area_lon = ?, area_label = ?, area_saved_at = CURRENT_TIMESTAMP WHERE id IN (${marks})`
      ).run(round(lat), round(lon), clean(b.label, 80) || null, ...ids);
      const saved = db.prepare('SELECT * FROM children WHERE id = ?').get(kids[0].id);
      return { savedArea: areaView(saved), updated: kids.length };
    }

    // The caregiver's app asks for new notifications every 15 minutes and when it opens, checked the
    // same way as the lookup. `after` is the highest id it already has; anything older than a week is
    // left out so a phone that was off for a while is not flooded.
    if (method === 'POST' && path === '/caregiver/notifications') {
      const { b, kids } = await verifyCaregiver(req, ip);
      const after = Number.isSafeInteger(b.after) && b.after > 0 ? b.after : 0;
      const ids = kids.map((k) => k.id);
      const marks = ids.map(() => '?').join(',');
      const rows = db
        .prepare(
          `SELECT n.id, n.child_id, n.message, n.created_at, c.child_name, c.facility AS child_facility,
                  w.name AS worker_name, w.facility AS worker_facility
           FROM app_notifications n JOIN children c ON c.id = n.child_id LEFT JOIN workers w ON w.id = n.worker_id
           WHERE n.child_id IN (${marks}) AND n.id > ? AND n.created_at > datetime('now', '-7 days')
           ORDER BY n.id LIMIT 20`
        )
        .all(...ids, after);
      transaction(db, () => {
        db.prepare(`UPDATE children SET app_seen_at = CURRENT_TIMESTAMP WHERE id IN (${marks})`).run(...ids);
        const delivered = db.prepare('UPDATE app_notifications SET delivered_at = CURRENT_TIMESTAMP WHERE id = ? AND delivered_at IS NULL');
        for (const r of rows) delivered.run(r.id);
      });
      return {
        notifications: rows.map((r) => ({
          id: r.id,
          childId: r.child_id,
          childName: r.child_name,
          message: r.message,
          sentAt: r.created_at,
          from: r.worker_facility || r.child_facility || r.worker_name || null,
        })),
      };
    }

    // One picture of a vaccination card or doctor's note, sent as raw JPEG or PNG bytes. The body is
    // the picture, so the phone number and date of birth come in headers. The reply's ref is then
    // sent with the request; pictures never sent with one are deleted after a day.
    if (method === 'POST' && path === '/caregiver/verification-documents') {
      const store = requireDocuments();
      limit(uploadLimiter, ip);
      const declared = String(req.headers['content-type'] || '').split(';')[0].trim().toLowerCase();
      // Read the body before refusing anything, so the app gets the reason rather than a broken connection.
      const buf = await readRaw(req, MAX_DOCUMENT_BYTES);
      // Header values must be plain ASCII, so the app sends the name URL-encoded.
      let headerName = '';
      try {
        headerName = decodeURIComponent(String(req.headers['x-child-name'] || ''));
      } catch {
        headerName = '';
      }
      const { kids } = checkCaregiver(req.headers['x-caregiver-phone'], req.headers['x-child-dob'], headerName);
      const child = caregiverChild(kids, url.searchParams.get('childId'));
      const kind = url.searchParams.get('kind');
      if (!['card', 'note'].includes(kind)) throw invalid('kind must be card or note', 'kind');
      if (!DOCUMENT_TYPES.includes(declared)) throw new HttpError(415, 'Only JPEG or PNG photos can be uploaded', { code: 'bad_file' });
      const problem = documentProblem(buf, declared);
      if (problem) throw new HttpError(400, problem, { code: 'bad_file' });
      purgeUnusedDocuments();
      const waiting = db
        .prepare('SELECT COUNT(*) AS n FROM verification_documents WHERE child_id = ? AND verification_id IS NULL')
        .get(child.id).n;
      if (waiting >= MAX_UNUSED_DOCUMENTS) {
        throw new HttpError(429, 'Too many photos uploaded without sending a request. Try again tomorrow', { code: 'too_many_uploads' });
      }
      const storageName = store.save(buf);
      const ref = newToken();
      try {
        db.prepare(
          'INSERT INTO verification_documents (ref, child_id, kind, mime_type, size, sha256, storage_name) VALUES (?, ?, ?, ?, ?, ?, ?)'
        ).run(ref, child.id, kind, declared, buf.length, sha256(buf), storageName);
      } catch (e) {
        store.remove(storageName);
        throw e;
      }
      return { httpStatus: 201, body: { document: { ref, kind, mimeType: declared, size: buf.length } } };
    }

    if (method === 'POST' && path === '/caregiver/verifications') {
      const { b, kids } = await verifyCaregiver(req, ip);
      const child = caregiverChild(kids, b.childId);
      const code = String(b.doseCode || '');
      // A rejected request for the same dose is reopened instead of adding a second one.
      const existing = db.prepare('SELECT * FROM vaccination_verifications WHERE child_id = ? AND dose_code = ?').get(child.id, code);
      const row = submitEvidence(child, code, b, existing, today);
      return { httpStatus: existing ? 200 : 201, body: { verification: caregiverVerificationView(row) } };
    }

    if ((m = /^\/caregiver\/verifications\/(\d+)\/resubmit$/.exec(path)) && method === 'POST') {
      const { b, kids } = await verifyCaregiver(req, ip);
      const existing = db.prepare('SELECT * FROM vaccination_verifications WHERE id = ?').get(+m[1]);
      const child = existing && kids.find((k) => k.id === existing.child_id);
      if (!child) throw new HttpError(404, 'Verification request not found', { code: 'not_found' });
      if (existing.status !== 'rejected') {
        throw new HttpError(409, 'Only a rejected request can be sent again', { code: 'not_rejected' });
      }
      return { verification: caregiverVerificationView(submitEvidence(child, existing.dose_code, b, existing, today)) };
    }

    // The status of every request for the caregiver's children, with the children's doses so the app
    // can offer the ones that still need evidence.
    if (method === 'POST' && path === '/caregiver/verifications/status') {
      const { kids } = await verifyCaregiver(req, ip);
      const ids = kids.map((k) => k.id);
      const rows = db
        .prepare(`${VERIFICATION_SELECT} WHERE v.child_id IN (${ids.map(() => '?').join(',')}) ORDER BY v.submitted_at DESC, v.id DESC`)
        .all(...ids);
      return { children: kids.map((k) => childView(db, k, today)), verifications: rows.map(caregiverVerificationView) };
    }

    if (method === 'GET' && path === '/facilities/nearby') {
      limit(osmLimiter, ip);
      const lat = coordinate(url.searchParams.get('lat'), 90);
      const lon = coordinate(url.searchParams.get('lon'), 180);
      if (lat === null || lon === null) throw invalid('lat and lon must be valid coordinates', lat === null ? 'lat' : 'lon');
      const { facilities, kind, radiusKm, source } = await fromOsm(() => osm.nearbyFacilities(lat, lon));
      return {
        facilities,
        kind,
        radiusKm,
        maxSearchKm: MAX_SEARCH_KM,
        source,
        attribution: OSM_ATTRIBUTION,
        note: 'Call to confirm that the vaccine is available.',
      };
    }

    // Public, because health workers also choose their facility when creating an account.
    if (method === 'GET' && path === '/facilities/list') {
      limit(facilityListLimiter, ip);
      const inUse = db
        .prepare("SELECT facility FROM workers WHERE facility <> '' UNION SELECT facility FROM children WHERE facility <> ''")
        .all()
        .map((r) => r.facility);
      return { ...facilityIndex.search(clean(url.searchParams.get('q'), 100), inUse), attribution: OSM_ATTRIBUTION };
    }

    if (method === 'GET' && path === '/geocode') {
      limit(osmLimiter, ip);
      const q = clean(url.searchParams.get('q'), 100);
      if (q.length < 2) throw invalid('Enter a town or place name', 'q');
      return { results: await fromOsm(() => osm.geocode(q)), attribution: OSM_ATTRIBUTION };
    }

    if (method === 'POST' && path === '/auth/register') {
      limit(authLimiter, ip);
      const b = await readBody(req);
      const name = clean(b.name, 80);
      const phone = normalizePhone(b.phone);
      const facility = clean(b.facility, 120);
      if (name.length < 2) throw invalid('Enter your full name', 'name');
      if (!phone) throw invalid(PHONE_HINT, 'phone');
      if (!facility) throw invalid('Enter your health facility', 'facility');
      const problem = passwordProblem(b.password);
      if (problem) throw new HttpError(400, problem, { code: 'weak_password', field: 'password' });
      if (!registrationCode || !safeEqual(clean(b.registrationCode, 200), registrationCode)) {
        throw new HttpError(403, 'Registration code is not correct. Ask your facility in-charge for it', {
          code: 'bad_registration_code',
          field: 'registrationCode',
        });
      }
      const taken = new HttpError(409, 'An account with this phone number already exists. Log in instead', {
        code: 'phone_taken',
        field: 'phone',
      });
      if (db.prepare('SELECT 1 FROM workers WHERE phone = ?').get(phone)) throw taken;
      const hash = await hashPassword(b.password);
      let id;
      try {
        id = Number(
          db.prepare('INSERT INTO workers (name, phone, facility, password_hash, last_login_at) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)')
            .run(name, phone, facility, hash).lastInsertRowid
        );
      } catch (e) {
        if (/UNIQUE constraint failed/.test(e.message)) throw taken;
        throw e;
      }
      return { httpStatus: 201, body: startSession(db.prepare('SELECT * FROM workers WHERE id = ?').get(id)) };
    }

    if (method === 'POST' && path === '/auth/login') {
      limit(authLimiter, ip);
      const b = await readBody(req);
      const phone = normalizePhone(b.phone);
      const password = typeof b.password === 'string' ? b.password : '';
      if (!phone) throw invalid(PHONE_HINT, 'phone');
      if (!password) throw invalid('Enter your password', 'password');
      if (failedLogins.count(phone) >= 5) {
        throw new HttpError(429, 'Too many wrong attempts. Wait 15 minutes and try again', { code: 'locked' });
      }
      const worker = db.prepare('SELECT * FROM workers WHERE phone = ?').get(phone);
      // Hash anyway when the account does not exist, so the reply time does not reveal which numbers have accounts.
      const ok = worker ? await verifyPassword(password, worker.password_hash) : (await hashPassword(password), false);
      if (!ok) {
        failedLogins.hit(phone);
        throw new HttpError(401, 'Phone number or password is wrong', { code: 'invalid_credentials' });
      }
      failedLogins.reset(phone);
      db.prepare('UPDATE workers SET last_login_at = CURRENT_TIMESTAMP WHERE id = ?').run(worker.id);
      return startSession(worker);
    }

    const { worker, tokenHash: sessionHash } = authWorker(req);

    if (method === 'GET' && path === '/auth/me') return { worker: workerView(worker) };

    if (method === 'POST' && path === '/auth/logout') {
      db.prepare('DELETE FROM sessions WHERE token_hash = ?').run(sessionHash);
      return { ok: true };
    }

    if (method === 'POST' && path === '/auth/password') {
      const b = await readBody(req);
      if (typeof b.currentPassword !== 'string' || !(await verifyPassword(b.currentPassword, worker.password_hash))) {
        throw new HttpError(400, 'Current password is wrong', { code: 'invalid_credentials', field: 'currentPassword' });
      }
      const problem = passwordProblem(b.newPassword);
      if (problem) throw new HttpError(400, problem, { code: 'weak_password', field: 'newPassword' });
      db.prepare('UPDATE workers SET password_hash = ? WHERE id = ?').run(await hashPassword(b.newPassword), worker.id);
      // Log out every other phone that used the old password.
      db.prepare('DELETE FROM sessions WHERE worker_id = ? AND token_hash != ?').run(worker.id, sessionHash);
      return { ok: true };
    }

    if (method === 'GET' && path === '/stats') {
      const one = (sql, ...p) => Object.values(db.prepare(sql).get(...p))[0];
      const week = addDays(today, 7);
      return {
        today,
        smsMode: smsMode(),
        autoReminders: autoReminders(),
        children: one('SELECT COUNT(*) FROM children'),
        overdueChildren: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date < ?', today),
        dueToday: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date = ?', today),
        dueThisWeek: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date BETWEEN ? AND ?', today, week),
        givenLast30Days: one('SELECT COUNT(*) FROM doses WHERE given_date >= ?', addDays(today, -30)),
        smsLast7Days: one("SELECT COUNT(*) FROM sms_log WHERE status NOT IN ('failed', 'demo') AND created_at >= datetime('now', '-7 days')"),
        pendingVerifications: one("SELECT COUNT(*) FROM vaccination_verifications WHERE status = 'pending'"),
      };
    }

    if (method === 'POST' && path === '/children') {
      const b = await readBody(req);
      const f = childFields(b, today, false);
      if (b.consent !== true) throw invalid('Caregiver consent to receive SMS is required', 'consent');
      if (!f.facility) f.facility = worker.facility;
      assertNotDuplicate(f.phone, f.dob, f.child_name);

      const id = transaction(db, () => {
        const info = db
          .prepare(
            `INSERT INTO children (child_name, caregiver_name, phone, dob, language, facility, nearest_hospital, consent, registered_by)
             VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?)`
          )
          .run(f.child_name, f.caregiver_name, f.phone, f.dob, f.language, f.facility, f.nearest_hospital || '', worker.id);
        const childId = Number(info.lastInsertRowid);
        const ins = db.prepare('INSERT INTO doses (child_id, code, name, due_date) VALUES (?, ?, ?, ?)');
        for (const item of SCHEDULE) ins.run(childId, item.code, item.name, '');
        replanChild(db, childId);
        return childId;
      });
      return { httpStatus: 201, body: childView(db, findChild(id), today, { history: true }) };
    }

    if (method === 'GET' && path === '/children') {
      const q = (url.searchParams.get('q') || '').trim().toLowerCase();
      const filter = url.searchParams.get('filter') || 'all';
      const where = [];
      const params = [];
      if (q) {
        const like = '%' + q.replace(/[\\%_]/g, (ch) => '\\' + ch) + '%';
        const conds = ["lower(c.child_name) LIKE ? ESCAPE '\\'", "lower(c.caregiver_name) LIKE ? ESCAPE '\\'"];
        params.push(like, like);
        // Phones are stored as +2547..., so "0712 345" should still match.
        const digits = q.replace(/[\s\-()+]/g, '');
        const local = /^\d+$/.test(digits) ? digits.replace(/^(254|0)/, '') : '';
        if (local) {
          conds.push('c.phone LIKE ?');
          params.push('%' + local + '%');
        }
        where.push(`(${conds.join(' OR ')})`);
      }
      if (filter === 'overdue') {
        where.push('EXISTS (SELECT 1 FROM doses WHERE child_id = c.id AND given_date IS NULL AND due_date < ?)');
        params.push(today);
      } else if (filter === 'due') {
        where.push('EXISTS (SELECT 1 FROM doses WHERE child_id = c.id AND given_date IS NULL AND due_date BETWEEN ? AND ?)');
        params.push(today, addDays(today, 7));
      }
      const order = filter === 'all' ? 'c.created_at DESC, c.id DESC' : 'next_due, c.id';
      const rows = db
        .prepare(
          `SELECT c.*,
             (SELECT MIN(due_date) FROM doses WHERE child_id = c.id AND given_date IS NULL) AS next_due,
             (SELECT COUNT(*) FROM doses WHERE child_id = c.id AND given_date IS NULL AND due_date < ?) AS overdue_count,
             (SELECT COUNT(*) FROM doses WHERE child_id = c.id AND given_date IS NOT NULL) AS given_count,
             (SELECT COUNT(*) FROM doses WHERE child_id = c.id) AS total_count
           FROM children c ${where.length ? 'WHERE ' + where.join(' AND ') : ''}
           ORDER BY ${order} LIMIT 200`
        )
        .all(today, ...params);
      return { children: rows.map(summaryView) };
    }

    if ((m = /^\/children\/(\d+)$/.exec(path))) {
      const child = findChild(+m[1]);
      if (method === 'GET') {
        await refreshQueuedSms(db, child.id);
        return childView(db, child, today, { history: true });
      }

      if (method === 'PATCH') {
        const b = await readBody(req);
        const f = childFields(b, today, true);
        if (!Object.keys(f).length) throw invalid('Nothing to update');
        const merged = { ...child, ...f };
        if (f.child_name || f.phone || f.dob) assertNotDuplicate(merged.phone, merged.dob, merged.child_name, child.id);
        if (f.dob && f.dob !== child.dob) {
          const early = db.prepare('SELECT name FROM doses WHERE child_id = ? AND given_date < ? LIMIT 1').get(child.id, f.dob);
          if (early) throw invalid(`${early.name} is recorded as given before this date of birth`, 'dob');
        }
        transaction(db, () => {
          const cols = Object.keys(f);
          db.prepare(`UPDATE children SET ${cols.map((c) => `${c} = ?`).join(', ')}, updated_at = CURRENT_TIMESTAMP WHERE id = ?`).run(
            ...cols.map((c) => f[c]),
            child.id
          );
          if (f.dob && f.dob !== child.dob) replanChild(db, child.id);
        });
        return childView(db, findChild(child.id), today, { history: true });
      }

      if (method === 'DELETE') {
        // The rows go with the child (ON DELETE CASCADE); the encrypted files have to be removed here.
        const files = db.prepare('SELECT storage_name FROM verification_documents WHERE child_id = ?').all(child.id);
        db.prepare('DELETE FROM children WHERE id = ?').run(child.id);
        for (const f of files) documents?.remove(f.storage_name);
        return { ok: true };
      }
    }

    if ((m = /^\/children\/(\d+)\/doses$/.exec(path)) && method === 'POST') {
      const child = findChild(+m[1]);
      const b = await readBody(req);
      if (!Array.isArray(b.given) || !b.given.length) throw invalid('Choose at least one vaccine');
      const given = givenMap(child.id);
      const items = [...b.given].sort((x, y) => String(x.date || today).localeCompare(String(y.date || today)));
      for (const g of items) {
        if (!BY_CODE.has(g.code)) throw new HttpError(404, `Unknown vaccine ${g.code}`, { code: 'not_found' });
        const date = g.date || today;
        checkGivenDate(child, g.code, date, given, today);
        given.set(g.code, date);
      }
      transaction(db, () => {
        const upd = db.prepare('UPDATE doses SET given_date = ? WHERE child_id = ? AND code = ?');
        for (const g of items) upd.run(g.date || today, child.id, g.code);
        replanChild(db, child.id);
      });
      return childView(db, child, today, { history: true });
    }

    if ((m = /^\/children\/(\d+)\/doses\/([A-Z0-9]+)\/(given|undo)$/.exec(path)) && method === 'POST') {
      const child = findChild(+m[1]);
      const dose = db.prepare('SELECT * FROM doses WHERE child_id = ? AND code = ?').get(child.id, m[2]);
      if (!dose) throw new HttpError(404, 'Dose not found', { code: 'not_found' });
      if (m[3] === 'given') {
        const b = await readBody(req);
        const date = b.date || today;
        const given = givenMap(child.id);
        given.delete(dose.code);
        checkGivenDate(child, dose.code, date, given, today);
        transaction(db, () => {
          db.prepare('UPDATE doses SET given_date = ? WHERE id = ?').run(date, dose.id);
          replanChild(db, child.id);
        });
      } else {
        transaction(db, () => {
          db.prepare('UPDATE doses SET given_date = NULL WHERE id = ?').run(dose.id);
          replanChild(db, child.id);
        });
      }
      return childView(db, child, today, { history: true });
    }

    if (method === 'GET' && path === '/missed') {
      const rows = db
        .prepare(
          `SELECT c.id, c.child_name, c.caregiver_name, c.phone, c.facility, c.consent,
                  MIN(d.due_date) AS oldest_due, COUNT(*) AS n, group_concat(d.name, ', ') AS vaccines,
                  (SELECT MAX(created_at) FROM sms_log WHERE child_id = c.id AND status != 'failed') AS last_sms_at
           FROM doses d JOIN children c ON c.id = d.child_id
           WHERE d.given_date IS NULL AND d.due_date < ?
           GROUP BY c.id ORDER BY oldest_due, c.id`
        )
        .all(today);
      return {
        children: rows.map((r) => ({
          id: r.id,
          childName: r.child_name,
          caregiverName: r.caregiver_name,
          phone: r.phone,
          facility: r.facility,
          consent: r.consent === 1,
          oldestDueDate: r.oldest_due,
          daysOverdue: daysBetween(r.oldest_due, today),
          missedCount: r.n,
          vaccines: r.vaccines,
          lastSmsAt: r.last_sms_at,
        })),
      };
    }

    if (method === 'GET' && path === '/due') {
      const days = Math.min(Math.max(parseInt(url.searchParams.get('days') || '7', 10) || 7, 0), 60);
      const rows = db
        .prepare(
          `SELECT c.id, c.child_name, c.caregiver_name, c.phone, c.consent, d.due_date, group_concat(d.name, ', ') AS vaccines
           FROM doses d JOIN children c ON c.id = d.child_id
           WHERE d.given_date IS NULL AND d.due_date >= ? AND d.due_date <= ?
           GROUP BY c.id, d.due_date ORDER BY d.due_date, c.id`
        )
        .all(today, addDays(today, days));
      return {
        days,
        items: rows.map((r) => ({
          id: r.id,
          childName: r.child_name,
          caregiverName: r.caregiver_name,
          phone: r.phone,
          consent: r.consent === 1,
          dueDate: r.due_date,
          vaccines: r.vaccines,
        })),
      };
    }

    if (method === 'POST' && path === '/reminders/run') {
      const sent = await runReminders(db, today, { osm });
      return { sent, smsMode: smsMode() };
    }

    if ((m = /^\/children\/(\d+)\/remind$/.exec(path)) && method === 'POST') {
      purgeExpiredAreas(db);
      const child = findChild(+m[1]);
      if (child.consent !== 1) {
        throw new HttpError(409, 'The caregiver has not agreed to receive SMS reminders', { code: 'no_consent' });
      }
      const reminder = manualReminder(child, today);
      if (!reminder) throw new HttpError(409, 'All vaccines are already given', { code: 'all_given' });
      // No limit on how often: the health worker decides, and the app asks for confirmation each time.
      const { kind } = reminder;
      const message = reminder.message + (await clinicsSuffix(osm, child, child.language, { osmDown: false }));
      const out = await sendSms(db, child.phone, message, { childId: child.id, kind: 'manual' });
      if (out.status === 'failed') throw new HttpError(502, `The SMS could not be sent. ${smsError(out.response)}`, { code: 'sms_failed' });
      return { kind, message: withoutClinics(message), smsStatus: out.status };
    }

    // A notification for the caregiver's app, sent whenever the health worker chooses. Unlike SMS it
    // costs nothing, so there is no 12-hour limit, only a guard against tapping Send twice. With no
    // message typed, it carries the same reminder the SMS button would send.
    if ((m = /^\/children\/(\d+)\/notify$/.exec(path)) && method === 'POST') {
      const child = findChild(+m[1]);
      const b = await readBody(req);
      let message = String(b.message ?? '').trim().slice(0, MAX_NOTIFICATION_LENGTH);
      if (!message) {
        const reminder = manualReminder(child, today);
        if (!reminder) {
          throw new HttpError(409, 'All vaccines are already given. Write a message to send instead', { code: 'all_given', field: 'message' });
        }
        message = reminder.message;
      }
      const recent = db
        .prepare("SELECT 1 FROM app_notifications WHERE child_id = ? AND message = ? AND created_at > datetime('now', '-1 minute')")
        .get(child.id, message);
      if (recent) throw new HttpError(429, 'This notification was just sent', { code: 'recently_notified' });
      const id = db
        .prepare('INSERT INTO app_notifications (child_id, worker_id, message) VALUES (?, ?, ?)')
        .run(child.id, worker.id, message).lastInsertRowid;
      const row = db.prepare('SELECT * FROM app_notifications WHERE id = ?').get(id);
      return { notification: notificationView({ ...row, worker_name: worker.name }), appSeenAt: child.app_seen_at || null };
    }

    // ----- Vaccination verification requests (health workers only) -----

    if (method === 'GET' && path === '/verifications') {
      const status = url.searchParams.get('status') || 'pending';
      const where = [];
      const params = [];
      if (status === 'pending') where.push("v.status = 'pending'");
      else if (status === 'reviewed') where.push("v.status IN ('verified', 'rejected')");
      else if (['verified', 'rejected'].includes(status)) where.push(`v.status = '${status}'`);
      else if (status !== 'all') throw invalid('status must be pending, reviewed, verified, rejected or all', 'status');
      const childId = url.searchParams.get('childId');
      if (childId) {
        where.push('v.child_id = ?');
        params.push(Number(childId));
      }
      // Oldest first while waiting, so nobody is forgotten; most recent decision first afterwards.
      const order = status === 'pending' ? 'v.submitted_at, v.id' : 'COALESCE(v.reviewed_at, v.submitted_at) DESC, v.id DESC';
      const rows = db
        .prepare(`${VERIFICATION_SELECT} ${where.length ? 'WHERE ' + where.join(' AND ') : ''} ORDER BY ${order} LIMIT 200`)
        .all(...params);
      const count = (sql) => db.prepare(`SELECT COUNT(*) AS n FROM vaccination_verifications WHERE ${sql}`).get().n;
      return {
        verifications: rows.map(workerVerificationView),
        counts: { pending: count("status = 'pending'"), reviewed: count("status IN ('verified', 'rejected')") },
      };
    }

    if ((m = /^\/verifications\/(\d+)$/.exec(path)) && method === 'GET') {
      return { verification: verificationDetail(+m[1], today, worker) };
    }

    if ((m = /^\/verifications\/(\d+)\/documents\/(\d+)$/.exec(path)) && method === 'GET') {
      const store = requireDocuments();
      const v = verificationRow(+m[1]);
      const doc = db.prepare('SELECT * FROM verification_documents WHERE id = ? AND verification_id = ?').get(+m[2], v.id);
      if (!doc) throw new HttpError(404, 'Document not found', { code: 'not_found' });
      let data;
      try {
        data = store.read(doc.storage_name);
      } catch {
        // Missing, or changed on disk so the encryption check fails. Either way it cannot be trusted.
        throw new HttpError(410, 'This document can no longer be read', { code: 'document_unreadable' });
      }
      return { raw: data, contentType: doc.mime_type };
    }

    if ((m = /^\/verifications\/(\d+)\/(approve|reject)$/.exec(path)) && method === 'POST') {
      const b = await readBody(req);
      const v = verificationRow(+m[1]);
      const child = findChild(v.child_id);
      // Nobody reviews their own evidence: a worker who is also this child's caregiver must ask a colleague.
      if (worker.phone === child.phone) {
        throw new HttpError(403, 'This child is registered with your own phone number. Another health worker must review it', {
          code: 'own_child',
        });
      }
      if (v.status !== 'pending') throw new HttpError(409, 'This request has already been reviewed', { code: 'not_pending' });
      const doseName = BY_CODE.get(v.dose_code)?.name || v.dose_code;
      const stillPending = () => {
        if (db.prepare('SELECT status FROM vaccination_verifications WHERE id = ?').get(v.id).status !== 'pending') {
          throw new HttpError(409, 'This request has already been reviewed', { code: 'not_pending' });
        }
      };
      const notifyCaregiver = (outcome, reason) =>
        db
          .prepare('INSERT INTO app_notifications (child_id, worker_id, message) VALUES (?, ?, ?)')
          .run(child.id, worker.id, verificationMessage(outcome, child.language, child.child_name, doseName, v.vaccination_date, reason));

      if (m[2] === 'approve') {
        const dose = db.prepare('SELECT * FROM doses WHERE child_id = ? AND code = ?').get(child.id, v.dose_code);
        if (!dose) throw new HttpError(404, 'Dose not found', { code: 'not_found' });
        if (dose.given_date) {
          throw new HttpError(409, `${dose.name} is already recorded as given on ${dose.given_date}. Reject this request instead`, {
            code: 'already_given',
          });
        }
        // The same checks as recording a dose by hand.
        const given = givenMap(child.id);
        checkGivenDate(child, v.dose_code, v.vaccination_date, given, today);
        // Recording the dose, re-planning the later doses, closing the request and telling the
        // caregiver happen together or not at all.
        transaction(db, () => {
          stillPending();
          const changed = db
            .prepare('UPDATE doses SET given_date = ? WHERE id = ? AND given_date IS NULL')
            .run(v.vaccination_date, dose.id).changes;
          if (changed !== 1) throw new HttpError(409, `${dose.name} is already recorded as given`, { code: 'already_given' });
          replanChild(db, child.id);
          db.prepare(
            "UPDATE vaccination_verifications SET status = 'verified', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, rejection_reason = NULL WHERE id = ?"
          ).run(worker.id, v.id);
          db.prepare("INSERT INTO verification_events (verification_id, action, worker_id, detail) VALUES (?, 'approved', ?, ?)").run(
            v.id,
            worker.id,
            v.vaccination_date
          );
          notifyCaregiver('verified');
        });
      } else {
        const reason = clean(b.reason, 300);
        if (reason.length < 5) {
          throw new HttpError(400, 'Write the reason for rejecting, so the caregiver knows what to correct', {
            code: 'reason_required',
            field: 'reason',
          });
        }
        transaction(db, () => {
          stillPending();
          db.prepare(
            "UPDATE vaccination_verifications SET status = 'rejected', reviewed_by = ?, reviewed_at = CURRENT_TIMESTAMP, rejection_reason = ? WHERE id = ?"
          ).run(worker.id, reason, v.id);
          db.prepare("INSERT INTO verification_events (verification_id, action, worker_id, detail) VALUES (?, 'rejected', ?, ?)").run(
            v.id,
            worker.id,
            reason
          );
          notifyCaregiver('rejected', reason);
        });
      }
      return { verification: verificationDetail(v.id, today, worker) };
    }

    throw new HttpError(404, 'Not found', { code: 'not_found' });
  }

  return http.createServer(async (req, res) => {
    const started = Date.now();
    let status = 200;
    let body;
    try {
      const out = await route(req);
      if (out && out.raw) {
        // A stored document: sent as it is, never cached, and never guessed as another type.
        res.writeHead(200, {
          'Content-Type': out.contentType,
          'Content-Length': out.raw.length,
          'Cache-Control': 'no-store',
          'X-Content-Type-Options': 'nosniff',
          'Content-Disposition': 'inline',
        });
        res.end(out.raw);
        if (log) console.log(`${req.method} ${req.url.split('?')[0]} 200 ${Date.now() - started}ms`);
        return;
      }
      status = out && out.httpStatus ? out.httpStatus : 200;
      body = out && out.httpStatus ? out.body : out;
    } catch (e) {
      status = e instanceof HttpError ? e.status : 500;
      if (status === 500) console.error(e);
      body = { error: status === 500 ? 'Server error' : e.message };
      if (status !== 500 && e.code) body.code = e.code;
      if (status !== 500 && e.field) body.field = e.field;
    }
    res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(body));
    if (log) console.log(`${req.method} ${req.url.split('?')[0]} ${status} ${Date.now() - started}ms`);
  });
}
