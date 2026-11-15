import http from 'node:http';
import { SCHEDULE, BY_CODE, milestoneOf, previousInSeries, parseDate, daysBetween, addDays, todayStr } from './schedule.js';
import { buildMessage, KINDS, kindFor, nearbyClinicsText } from './messages.js';
import { normalizePhone, sendSms, smsMode } from './sms.js';
import { replanChild, purgeExpiredAreas, AREA_DAYS } from './db.js';
import { createOsmClient, OsmError, OSM_ATTRIBUTION, MAX_SEARCH_KM } from './facilities.js';
import { hashPassword, verifyPassword, passwordProblem, newToken, tokenHash, safeEqual, makeLimiter, SESSION_DAYS } from './auth.js';

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
  }
  if (area) view.savedArea = areaView(child);
  return view;
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
      `SELECT c.id AS child_id, c.child_name, c.phone, c.language, c.facility, c.area_lat, c.area_lon, d.due_date, d.name AS dose_name
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
      buildMessage(kind, g.language, g.child_name, g.vaccines, g.due_date, g.facility) + (await clinicsSuffix(osm, g, g.language, osmState));
    const out = await sendSms(db, g.phone, message, { childId, kind });
    save.run(childId, g.due_date, kind, out.status, today);
    results.push({ childId, childName: g.child_name, kind, dueDate: g.due_date, status: out.status });
  }
  return results;
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

export function createApp(db, { registrationCode, clock = todayStr, log = false, osm = createOsmClient() } = {}) {
  const lookupLimiter = makeLimiter(20, 60_000);
  const osmLimiter = makeLimiter(30, 60_000);
  const failedLookups = makeLimiter(10, 3_600_000);
  const authLimiter = makeLimiter(10, 60_000);
  const failedLogins = makeLimiter(5, 15 * 60_000);

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

  async function verifyCaregiver(req, ip) {
    limit(lookupLimiter, ip);
    const b = await readBody(req);
    const phone = normalizePhone(b.phone);
    if (!phone) throw invalid(PHONE_HINT, 'phone');
    if (!parseDate(b.childDob)) throw invalid("Choose the child's date of birth", 'childDob');
    if (failedLookups.count(phone) >= 10) {
      throw new HttpError(429, 'Too many attempts for this phone number. Try again in an hour', { code: 'rate_limited' });
    }
    purgeExpiredAreas(db);
    const kids = db.prepare('SELECT * FROM children WHERE phone = ? AND dob = ? ORDER BY id').all(phone, b.childDob);
    if (!kids.length) {
      failedLookups.hit(phone);
      throw new HttpError(404, 'No child found with that phone number and date of birth', { code: 'not_found' });
    }
    return { b, phone, kids };
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

  function givenMap(childId) {
    const rows = db.prepare('SELECT code, given_date FROM doses WHERE child_id = ? AND given_date IS NOT NULL').all(childId);
    return new Map(rows.map((r) => [r.code, r.given_date]));
  }

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
      const { b, phone, kids } = await verifyCaregiver(req, ip);
      if (b.lat === null) {
        db.prepare(
          'UPDATE children SET area_lat = NULL, area_lon = NULL, area_label = NULL, area_saved_at = NULL WHERE phone = ? AND dob = ?'
        ).run(phone, b.childDob);
        return { savedArea: null, updated: kids.length };
      }
      const lat = coordinate(b.lat, 90);
      const lon = coordinate(b.lon, 180);
      if (lat === null || lon === null) {
        throw invalid('Send valid lat and lon, or lat null to delete the saved area', lat === null ? 'lat' : 'lon');
      }
      const round = (n) => Math.round(n * 1000) / 1000;
      db.prepare(
        'UPDATE children SET area_lat = ?, area_lon = ?, area_label = ?, area_saved_at = CURRENT_TIMESTAMP WHERE phone = ? AND dob = ?'
      ).run(round(lat), round(lon), clean(b.label, 80) || null, phone, b.childDob);
      const saved = db.prepare('SELECT * FROM children WHERE id = ?').get(kids[0].id);
      return { savedArea: areaView(saved), updated: kids.length };
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
        children: one('SELECT COUNT(*) FROM children'),
        overdueChildren: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date < ?', today),
        dueToday: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date = ?', today),
        dueThisWeek: one('SELECT COUNT(DISTINCT child_id) FROM doses WHERE given_date IS NULL AND due_date BETWEEN ? AND ?', today, week),
        givenLast30Days: one('SELECT COUNT(*) FROM doses WHERE given_date >= ?', addDays(today, -30)),
        smsLast7Days: one("SELECT COUNT(*) FROM sms_log WHERE status != 'failed' AND created_at >= datetime('now', '-7 days')"),
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
            'INSERT INTO children (child_name, caregiver_name, phone, dob, language, facility, consent, registered_by) VALUES (?, ?, ?, ?, ?, ?, 1, ?)'
          )
          .run(f.child_name, f.caregiver_name, f.phone, f.dob, f.language, f.facility, worker.id);
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
      if (method === 'GET') return childView(db, child, today, { history: true });

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
        db.prepare('DELETE FROM children WHERE id = ?').run(child.id);
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
      const pending = db
        .prepare('SELECT name, due_date FROM doses WHERE child_id = ? AND given_date IS NULL ORDER BY due_date, id')
        .all(child.id);
      if (!pending.length) throw new HttpError(409, 'All vaccines are already given', { code: 'all_given' });
      const recent = db
        .prepare(
          "SELECT 1 FROM sms_log WHERE child_id = ? AND kind = 'manual' AND status != 'failed' AND created_at > datetime('now', '-12 hours')"
        )
        .get(child.id);
      if (recent) {
        throw new HttpError(429, 'A reminder was already sent to this caregiver in the last 12 hours', { code: 'recently_reminded' });
      }
      const first = pending[0].due_date;
      const same = pending.filter((p) => p.due_date === first).map((p) => p.name);
      const diff = daysBetween(today, first);
      const kind = diff < 0 ? 'overdue' : diff === 0 ? 'due' : 'before';
      const message =
        buildMessage(kind, child.language, child.child_name, same, first, child.facility) +
        (await clinicsSuffix(osm, child, child.language, { osmDown: false }));
      const out = await sendSms(db, child.phone, message, { childId: child.id, kind: 'manual' });
      if (out.status === 'failed') throw new HttpError(502, 'The SMS could not be sent. Try again later', { code: 'sms_failed' });
      return { kind, message: withoutClinics(message), smsStatus: out.status };
    }

    throw new HttpError(404, 'Not found', { code: 'not_found' });
  }

  return http.createServer(async (req, res) => {
    const started = Date.now();
    let status = 200;
    let body;
    try {
      const out = await route(req);
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
