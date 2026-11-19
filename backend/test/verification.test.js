// Vaccination verification: caregivers send evidence, health workers approve or reject it.
// All names and numbers here are made up.
import test from 'node:test';
import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import zlib from 'node:zlib';
import { DatabaseSync } from 'node:sqlite';
import { openDb } from '../src/db.js';
import { createApp, runReminders } from '../src/app.js';
import { createDocumentStore, detectImageType, documentProblem, imageSize, loadDocumentKey } from '../src/documents.js';

const CODE = 'test-registration-code';
const today = '2026-10-25';

// A real PNG with noise in it, so it does not compress below the minimum size.
function png(width = 240, height = 240, seed = 1) {
  const chunk = (type, data) => {
    const len = Buffer.alloc(4);
    len.writeUInt32BE(data.length);
    const body = Buffer.concat([Buffer.from(type, 'latin1'), data]);
    const crc = Buffer.alloc(4);
    crc.writeUInt32BE(zlib.crc32(body));
    return Buffer.concat([len, body, crc]);
  };
  const ihdr = Buffer.alloc(13);
  ihdr.writeUInt32BE(width, 0);
  ihdr.writeUInt32BE(height, 4);
  ihdr[8] = 8; // bit depth
  ihdr[9] = 2; // RGB
  let x = seed;
  const rows = Buffer.alloc((width * 3 + 1) * height);
  for (let i = 0; i < rows.length; i++) {
    x = (x * 1103515245 + 12345) & 0x7fffffff;
    rows[i] = i % (width * 3 + 1) === 0 ? 0 : x & 0xff;
  }
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    chunk('IHDR', ihdr),
    chunk('IDAT', zlib.deflateSync(rows)),
    chunk('IEND', Buffer.alloc(0)),
  ]);
}

// A JPEG header (APP0 then a start-of-frame with the size) followed by filler: enough for the
// server's checks, which look at the signature and the size, not at the picture itself.
function jpeg(width = 800, height = 600, bytes = 4096) {
  const app0 = Buffer.from([0xff, 0xe0, 0x00, 0x10, 0x4a, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00]);
  const sof = Buffer.from([0xff, 0xc0, 0x00, 0x11, 0x08, height >> 8, height & 0xff, width >> 8, width & 0xff, 0x03, 1, 0x22, 0, 2, 0x11, 1, 3, 0x11, 1]);
  const head = Buffer.concat([Buffer.from([0xff, 0xd8]), app0, sof]);
  return Buffer.concat([head, crypto.randomBytes(bytes - head.length - 2), Buffer.from([0xff, 0xd9])]);
}

const nurse = { name: 'Achieng Otieno', phone: '0722000111', facility: 'Kangemi Health Centre', password: 'secret123', registrationCode: CODE };

async function setup() {
  const db = openDb(':memory:');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vr-docs-'));
  const documents = createDocumentStore({ dir, key: crypto.randomBytes(32) });
  const server = createApp(db, { registrationCode: CODE, clock: () => today, documents });
  await new Promise((r) => server.listen(0, r));
  const base = `http://127.0.0.1:${server.address().port}`;
  const call = async (method, p, body, token) => {
    const res = await fetch(base + p, {
      method,
      headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
      body: body ? JSON.stringify(body) : undefined,
    });
    return { status: res.status, json: await res.json() };
  };
  const worker = (await call('POST', '/auth/register', nurse)).json.token;
  const register = async (fields) => {
    const r = await call(
      'POST',
      '/children',
      { caregiverName: 'Wanjiru Kamau', language: 'en', facility: 'Kangemi Health Centre', consent: true, ...fields },
      worker
    );
    assert.equal(r.status, 201);
    return r.json;
  };
  const upload = async (child, kind, buf, type = 'image/png', who = {}) => {
    const res = await fetch(`${base}/caregiver/verification-documents?childId=${who.childId ?? child.id}&kind=${kind}`, {
      method: 'POST',
      headers: {
        'Content-Type': type,
        'X-Caregiver-Phone': who.phone ?? child.phone,
        'X-Child-Dob': who.dob ?? child.dob,
        'X-Child-Name': encodeURIComponent(who.name ?? child.childName),
      },
      body: buf,
    });
    return { status: res.status, json: await res.json() };
  };
  const submit = (child, fields, who = {}) =>
    call('POST', '/caregiver/verifications', {
      phone: who.phone ?? child.phone,
      childDob: who.dob ?? child.dob,
      childName: child.childName,
      childId: child.id,
      hospital: 'Mama Lucy Kibaki Hospital',
      doctorName: 'Dr. Test Mwangi',
      ...fields,
    });
  const close = () => {
    server.close();
    fs.rmSync(dir, { recursive: true, force: true });
  };
  return { db, dir, base, call, worker, register, upload, submit, close };
}

const dose = (child, code) => child.doses.find((d) => d.code === code);

test('pictures are checked by their content, not by what the upload claims', () => {
  assert.equal(detectImageType(png()), 'image/png');
  assert.equal(detectImageType(jpeg()), 'image/jpeg');
  assert.equal(detectImageType(Buffer.from('%PDF-1.7 hello')), null);
  assert.deepEqual(imageSize(png(300, 220), 'image/png'), { width: 300, height: 220 });
  assert.deepEqual(imageSize(jpeg(1024, 768), 'image/jpeg'), { width: 1024, height: 768 });
  assert.equal(documentProblem(png(), 'image/png'), null);
  assert.match(documentProblem(png(), 'image/jpeg'), /does not match/);
  assert.match(documentProblem(png(50, 50), 'image/png'), /too small to read/);
  assert.match(documentProblem(Buffer.alloc(10), 'image/png'), /too small/);
  assert.match(documentProblem(Buffer.concat([Buffer.from('GIF89a'), Buffer.alloc(4000)]), 'image/png'), /JPEG or PNG/);
});

test('documents are encrypted on disk and tampering is detected', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vr-store-'));
  try {
    const store = createDocumentStore({ dir, key: crypto.randomBytes(32) });
    const picture = png();
    const name = store.save(picture);
    const onDisk = fs.readFileSync(path.join(dir, name));
    assert.equal(onDisk.includes(picture.subarray(0, 64)), false, 'the file on disk must not contain the picture');
    assert.deepEqual(store.read(name), picture);
    onDisk[onDisk.length - 1] ^= 1;
    fs.writeFileSync(path.join(dir, name), onDisk);
    assert.throws(() => store.read(name));
    assert.throws(() => store.read('../../etc/passwd'), /Bad document name/);

    const keyFile = path.join(dir, 'document.key');
    const key = loadDocumentKey(keyFile, {});
    assert.deepEqual(loadDocumentKey(keyFile, {}), key, 'the key file is reused');
    assert.throws(() => loadDocumentKey(keyFile, { DOCUMENT_KEY: 'short' }), /64 hexadecimal/);
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('upgrading an existing database keeps every record and adds the verification tables', () => {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'vr-migrate-'));
  const file = path.join(dir, 'old.db');
  try {
    // Build a database as the previous version left it: data in it, no verification tables, version 2.
    let db = openDb(file);
    db.prepare("INSERT INTO children (child_name, caregiver_name, phone, dob) VALUES ('Baraka Test', 'Mama Test', '+254712000999', '2026-05-01')").run();
    db.prepare("INSERT INTO doses (child_id, code, name, due_date, given_date) VALUES (1, 'BCG', 'BCG', '2026-05-01', '2026-05-02')").run();
    db.prepare("INSERT INTO workers (name, phone, password_hash) VALUES ('Nurse Test', '+254722000999', 'scrypt$00$00')").run();
    db.prepare("INSERT INTO sms_log (phone, message, status, child_id) VALUES ('+254712000999', 'hello', 'sent', 1)").run();
    db.exec('DROP TABLE verification_events; DROP TABLE verification_documents; DROP TABLE vaccination_verifications; PRAGMA user_version = 2');
    const dump = (d) =>
      ['children', 'doses', 'workers', 'sms_log'].map((t) => d.prepare(`SELECT * FROM ${t} ORDER BY id`).all().map((r) => ({ ...r })));
    const before = dump(db);
    db.close();

    db = openDb(file);
    assert.deepEqual(dump(db), before);
    assert.equal(db.prepare('PRAGMA user_version').get().user_version, 3);
    const tables = db.prepare("SELECT name FROM sqlite_master WHERE type = 'table'").all().map((t) => t.name);
    for (const t of ['vaccination_verifications', 'verification_documents', 'verification_events']) assert.ok(tables.includes(t), t);
    db.close();
    // Opening again changes nothing.
    db = new DatabaseSync(file);
    assert.equal(db.prepare('SELECT COUNT(*) AS n FROM children').get().n, 1);
    db.close();
  } finally {
    fs.rmSync(dir, { recursive: true, force: true });
  }
});

test('a caregiver submits evidence and a health worker approves it', async () => {
  const t = await setup();
  try {
    // 10 weeks old: the 6-week doses are overdue.
    const child = await t.register({ childName: 'Neema Test', phone: '0711000201', dob: '2026-08-16' });
    const before = dose(child, 'PENTA2').dueDate;

    const card = png();
    const up = await t.upload(child, 'card', card);
    assert.equal(up.status, 201);
    assert.match(up.json.document.ref, /^[\w-]{20,}$/);
    const note = await t.upload(child, 'note', jpeg(), 'image/jpeg');
    assert.equal(note.status, 201);

    const sent = await t.submit(child, {
      doseCode: 'PENTA1',
      vaccinationDate: '2026-10-10',
      cardDocument: up.json.document.ref,
      noteDocument: note.json.document.ref,
    });
    assert.equal(sent.status, 201);
    assert.equal(sent.json.verification.status, 'pending');
    assert.equal(sent.json.verification.hasCard, true);
    const id = sent.json.verification.id;

    // Submitting is not proof: the dose stays not given until a health worker approves.
    // The schedule shows the evidence under its dose: with the pictures' ids for health workers,
    // without them for the caregiver.
    let record = (await t.call('GET', `/children/${child.id}`, null, t.worker)).json;
    assert.equal(dose(record, 'PENTA1').givenDate, null);
    const shown = dose(record, 'PENTA1').verification;
    assert.equal(shown.id, id);
    assert.equal(shown.status, 'pending');
    assert.equal(shown.hospital, 'Mama Lucy Kibaki Hospital');
    assert.equal(shown.vaccinationDate, '2026-10-10');
    assert.deepEqual(shown.documents.map((d) => d.kind), ['card', 'note']);
    const ownView = (await t.call('POST', '/caregiver/lookup', { phone: child.phone, childDob: child.dob, childName: child.childName })).json.children[0];
    const forCaregiver = dose(ownView, 'PENTA1').verification;
    assert.equal(forCaregiver.status, 'pending');
    assert.equal(forCaregiver.hasCard, true);
    assert.equal(forCaregiver.hasNote, true);
    assert.equal('documents' in forCaregiver, false);

    const status = await t.call('POST', '/caregiver/verifications/status', { phone: child.phone, childDob: child.dob, childName: child.childName });
    assert.equal(status.status, 200);
    assert.equal(status.json.verifications[0].status, 'pending');
    assert.equal(status.json.verifications[0].doseName, 'Pentavalent 1');
    assert.equal('documents' in status.json.verifications[0], false, 'caregivers never get the pictures back');
    assert.equal(status.json.children[0].id, child.id);

    assert.equal((await t.call('GET', '/stats', null, t.worker)).json.pendingVerifications, 1);
    const list = await t.call('GET', '/verifications?status=pending', null, t.worker);
    assert.equal(list.json.verifications.length, 1);
    assert.equal(list.json.verifications[0].child.childName, 'Neema Test');
    assert.equal(list.json.verifications[0].documentCount, 2);
    assert.deepEqual(list.json.counts, { pending: 1, reviewed: 0 });

    const detail = (await t.call('GET', `/verifications/${id}`, null, t.worker)).json.verification;
    assert.equal(detail.hospital, 'Mama Lucy Kibaki Hospital');
    assert.equal(detail.doctorName, 'Dr. Test Mwangi');
    assert.equal(detail.vaccinationDate, '2026-10-10');
    assert.deepEqual(detail.documents.map((d) => d.kind), ['card', 'note']);
    assert.deepEqual(detail.history.map((h) => h.action), ['submitted']);
    assert.deepEqual(detail.warnings, []);

    const res = await fetch(`${t.base}/verifications/${id}/documents/${detail.documents[0].id}`, {
      headers: { Authorization: `Bearer ${t.worker}` },
    });
    assert.equal(res.status, 200);
    assert.equal(res.headers.get('content-type'), 'image/png');
    assert.equal(res.headers.get('cache-control'), 'no-store');
    assert.deepEqual(Buffer.from(await res.arrayBuffer()), card);

    const approved = await t.call('POST', `/verifications/${id}/approve`, {}, t.worker);
    assert.equal(approved.status, 200);
    assert.equal(approved.json.verification.status, 'verified');
    assert.equal(approved.json.verification.reviewedBy, 'Achieng Otieno');
    assert.deepEqual(approved.json.verification.history.map((h) => h.action), ['submitted', 'approved']);

    // Approval records the dose with the evidence's date and re-plans the series from it.
    record = (await t.call('GET', `/children/${child.id}`, null, t.worker)).json;
    assert.equal(dose(record, 'PENTA1').givenDate, '2026-10-10');
    assert.equal(dose(record, 'PENTA1').status, 'given');
    assert.equal(dose(record, 'PENTA1').verification.status, 'verified');
    assert.notEqual(dose(record, 'PENTA2').dueDate, before);
    assert.equal(dose(record, 'PENTA2').dueDate, '2026-11-07');

    const stats = (await t.call('GET', '/stats', null, t.worker)).json;
    assert.equal(stats.pendingVerifications, 0);
    assert.equal(stats.givenLast30Days, 1);

    // The caregiver's app is told.
    const inbox = await t.call('POST', '/caregiver/notifications', { phone: child.phone, childDob: child.dob, childName: child.childName });
    assert.match(inbox.json.notifications[0].message, /^Verified: Neema Test's Pentavalent 1 given on 2026-10-10/);

    assert.equal((await t.call('POST', `/verifications/${id}/approve`, {}, t.worker)).json.code, 'not_pending');
    assert.equal((await t.call('POST', `/verifications/${id}/reject`, { reason: 'Changed my mind' }, t.worker)).json.code, 'not_pending');
    const again = await t.upload(child, 'card', png(240, 240, 7));
    const dup = await t.submit(child, { doseCode: 'PENTA1', vaccinationDate: '2026-10-10', cardDocument: again.json.document.ref });
    assert.equal(dup.status, 409);
    assert.equal(dup.json.code, 'already_given');
    assert.equal((await t.call('GET', '/verifications?status=reviewed', null, t.worker)).json.verifications[0].status, 'verified');
  } finally {
    t.close();
  }
});

test('a rejected request keeps the dose as it was and can be sent again', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Zuri Test', phone: '0711000202', dob: '2026-08-16', language: 'sw' });
    const first = await t.upload(child, 'card', png());
    const sent = await t.submit(child, { doseCode: 'OPV1', vaccinationDate: '2026-09-28', cardDocument: first.json.document.ref });
    const id = sent.json.verification.id;

    const noReason = await t.call('POST', `/verifications/${id}/reject`, { reason: '  ' }, t.worker);
    assert.equal(noReason.status, 400);
    assert.equal(noReason.json.code, 'reason_required');

    const rejected = await t.call('POST', `/verifications/${id}/reject`, { reason: 'The card photo is blurred' }, t.worker);
    assert.equal(rejected.status, 200);
    assert.equal(rejected.json.verification.status, 'rejected');
    assert.equal(rejected.json.verification.rejectionReason, 'The card photo is blurred');

    const record = (await t.call('GET', `/children/${child.id}`, null, t.worker)).json;
    assert.equal(dose(record, 'OPV1').givenDate, null);
    assert.equal(dose(record, 'OPV1').status, 'overdue');

    const status = (await t.call('POST', '/caregiver/verifications/status', { phone: child.phone, childDob: child.dob, childName: child.childName })).json;
    assert.equal(status.verifications[0].status, 'rejected');
    assert.equal(status.verifications[0].rejectionReason, 'The card photo is blurred');
    const inbox = await t.call('POST', '/caregiver/notifications', { phone: child.phone, childDob: child.dob, childName: child.childName });
    assert.match(inbox.json.notifications[0].message, /^Hatukuweza kuthibitisha .* Sababu: The card photo is blurred\. Fungua/);

    // A picture already used cannot be attached again; a new one can.
    const reused = await t.call('POST', `/caregiver/verifications/${id}/resubmit`, {
      phone: child.phone,
      childDob: child.dob,
      childName: child.childName,
      vaccinationDate: '2026-09-28',
      hospital: 'Mama Lucy Kibaki Hospital',
      doctorName: 'Dr. Test Mwangi',
      cardDocument: first.json.document.ref,
    });
    assert.equal(reused.json.code, 'bad_document');
    const clearer = await t.upload(child, 'card', png(320, 320, 9));
    const again = await t.call('POST', `/caregiver/verifications/${id}/resubmit`, {
      phone: child.phone,
      childDob: child.dob,
      childName: child.childName,
      vaccinationDate: '2026-09-29',
      hospital: 'Mama Lucy Kibaki Hospital',
      doctorName: 'Dr. Test Mwangi',
      cardDocument: clearer.json.document.ref,
    });
    assert.equal(again.status, 200);
    assert.equal(again.json.verification.status, 'pending');
    assert.equal(again.json.verification.submissionCount, 2);
    assert.equal(again.json.verification.rejectionReason, null);
    assert.equal(again.json.verification.id, id, 'the same request is reopened');

    const notRejected = await t.call('POST', `/caregiver/verifications/${id}/resubmit`, {
      phone: child.phone,
      childDob: child.dob,
      childName: child.childName,
    });
    assert.equal(notRejected.json.code, 'not_rejected');

    const detail = (await t.call('GET', `/verifications/${id}`, null, t.worker)).json.verification;
    assert.equal(detail.vaccinationDate, '2026-09-29');
    assert.deepEqual(
      detail.history.map((h) => [h.action, h.detail]),
      [
        ['submitted', null],
        ['rejected', 'The card photo is blurred'],
        ['resubmitted', null],
      ]
    );
    assert.equal(detail.history[1].by, 'Achieng Otieno');
    assert.equal(detail.documents.length, 1);
    // The first, rejected picture is kept for the audit trail but is no longer the current one.
    assert.equal(t.db.prepare('SELECT COUNT(*) AS n FROM verification_documents WHERE verification_id = ?').get(id).n, 2);
  } finally {
    t.close();
  }
});

test('uploads that are not acceptable pictures are refused', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Imani Test', phone: '0711000203', dob: '2026-08-16' });
    const other = await t.register({ childName: 'Jabari Test', phone: '0711000204', dob: '2026-07-01' });
    const expect = async (promise, status, code) => {
      const r = await promise;
      assert.equal(r.status, status, JSON.stringify(r.json));
      if (code) assert.equal(r.json.code, code);
    };
    await expect(t.upload(child, 'card', Buffer.from('not a picture at all'.repeat(100)), 'text/plain'), 415, 'bad_file');
    await expect(t.upload(child, 'card', crypto.randomBytes(5000), 'image/jpeg'), 400, 'bad_file');
    await expect(t.upload(child, 'card', png(), 'image/jpeg'), 400, 'bad_file');
    await expect(t.upload(child, 'card', png(120, 120)), 400, 'bad_file');
    await expect(t.upload(child, 'card', Buffer.alloc(0)), 400, 'bad_file');
    await expect(t.upload(child, 'card', jpeg(800, 600, 6 * 1024 * 1024), 'image/jpeg'), 413, 'file_too_large');
    await expect(t.upload(child, 'photo', png()), 400, 'invalid');
    // Wrong date of birth, or a child that belongs to someone else.
    await expect(t.upload(child, 'card', png(), 'image/png', { dob: '2026-08-17' }), 404, 'not_found');
    await expect(t.upload(child, 'card', png(), 'image/png', { childId: other.id }), 404, 'not_found');
    await expect(t.upload(child, 'card', png(), 'image/png', { name: 'Someone Else' }), 404, 'not_found');
    assert.equal(t.db.prepare('SELECT COUNT(*) AS n FROM verification_documents').get().n, 0);
    assert.deepEqual(fs.readdirSync(t.dir), [], 'nothing refused is stored');
  } finally {
    t.close();
  }
});

test('submissions with missing or wrong details are refused, and duplicates are prevented', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Wanjiku Test', phone: '0711000205', dob: '2026-08-16' });
    const other = await t.register({ childName: 'Malaika Test', phone: '0711000206', dob: '2026-07-01' });
    const ref = (await t.upload(child, 'card', png())).json.document.ref;
    const othersRef = (await t.upload(other, 'card', png(240, 240, 3))).json.document.ref;
    const base = { doseCode: 'PCV1', vaccinationDate: '2026-10-01', cardDocument: ref };
    const check = async (fields, status, code, field) => {
      const r = await t.submit(child, { ...base, ...fields });
      assert.equal(r.status, status, JSON.stringify(r.json));
      assert.equal(r.json.code, code);
      if (field) assert.equal(r.json.field, field);
    };
    await check({ cardDocument: undefined }, 400, 'no_document', 'documents');
    await check({ hospital: '' }, 400, 'invalid', 'hospital');
    await check({ doctorName: ' ' }, 400, 'invalid', 'doctorName');
    await check({ vaccinationDate: '2026-10-26' }, 400, 'invalid', 'vaccinationDate');
    await check({ vaccinationDate: '2026-08-15' }, 400, 'invalid', 'vaccinationDate');
    await check({ vaccinationDate: 'yesterday' }, 400, 'invalid', 'vaccinationDate');
    await check({ doseCode: 'COVID' }, 400, 'invalid', 'doseCode');
    await check({ cardDocument: 'made-up-reference' }, 400, 'bad_document', 'card');
    await check({ cardDocument: othersRef }, 400, 'bad_document', 'card');
    await check({ cardDocument: undefined, noteDocument: ref }, 400, 'bad_document', 'note');
    const wrongDob = await t.submit(child, base, { dob: '2026-08-17' });
    assert.equal(wrongDob.status, 404);

    assert.equal((await t.submit(child, base)).status, 201);
    const second = (await t.upload(child, 'card', png(240, 240, 5))).json.document.ref;
    await check({ cardDocument: second }, 409, 'already_pending', 'doseCode');
    assert.equal(t.db.prepare('SELECT COUNT(*) AS n FROM vaccination_verifications').get().n, 1);

    // A dose the health worker already recorded needs no evidence.
    await t.call('POST', `/children/${child.id}/doses/BCG/given`, { date: '2026-08-16' }, t.worker);
    await check({ doseCode: 'BCG', cardDocument: second }, 409, 'already_given', 'doseCode');
  } finally {
    t.close();
  }
});

test('only signed-in health workers can see or review requests, and never for their own child', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Hope Test', phone: '0711000207', dob: '2026-08-16' });
    const ref = (await t.upload(child, 'card', png())).json.document.ref;
    const id = (await t.submit(child, { doseCode: 'ROTA1', vaccinationDate: '2026-10-01', cardDocument: ref })).json.verification.id;
    const docId = t.db.prepare('SELECT id FROM verification_documents').get().id;

    for (const [method, p] of [
      ['GET', '/verifications'],
      ['GET', `/verifications/${id}`],
      ['GET', `/verifications/${id}/documents/${docId}`],
      ['POST', `/verifications/${id}/approve`],
      ['POST', `/verifications/${id}/reject`],
    ]) {
      const none = await fetch(t.base + p, { method });
      assert.equal(none.status, 401, `${method} ${p} without a session`);
      assert.match(none.headers.get('content-type'), /json/, 'no document bytes without a session');
      assert.equal((await fetch(t.base + p, { method, headers: { Authorization: 'Bearer made-up-token' } })).status, 401);
    }
    // There is no caregiver route that returns the pictures or approves anything: without a health
    // worker session such a path is refused like any other unknown one.
    assert.equal((await t.call('POST', `/caregiver/verifications/${id}/approve`, { phone: child.phone, childDob: child.dob, childName: child.childName })).status, 401);
    assert.equal(t.db.prepare('SELECT status FROM vaccination_verifications WHERE id = ?').get(id).status, 'pending');
    assert.equal((await t.call('GET', `/verifications/${id}/documents/${docId + 1}`, null, t.worker)).status, 404);

    // A health worker who is also this child's caregiver cannot review it.
    const parent = await t.call('POST', '/auth/register', {
      name: 'Hope Parent',
      phone: child.phone,
      facility: 'Kangemi Health Centre',
      password: 'secret123',
      registrationCode: CODE,
    });
    const own = await t.call('POST', `/verifications/${id}/approve`, {}, parent.json.token);
    assert.equal(own.status, 403);
    assert.equal(own.json.code, 'own_child');
    const seen = (await t.call('GET', `/verifications/${id}`, null, parent.json.token)).json.verification;
    assert.ok(seen.warnings.some((w) => w.code === 'own_child'));
    assert.equal(t.db.prepare('SELECT status FROM vaccination_verifications WHERE id = ?').get(id).status, 'pending');
  } finally {
    t.close();
  }
});

test('approval follows the same date rules as recording a dose by hand', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Otieno Test', phone: '0711000208', dob: '2026-06-01' });
    await t.call('POST', `/children/${child.id}/doses/PENTA1/given`, { date: '2026-07-20' }, t.worker);
    // Evidence says Pentavalent 2 was given before Pentavalent 1: the worker sees why it cannot be approved.
    const ref = (await t.upload(child, 'card', png())).json.document.ref;
    const id = (await t.submit(child, { doseCode: 'PENTA2', vaccinationDate: '2026-07-10', cardDocument: ref })).json.verification.id;
    const detail = (await t.call('GET', `/verifications/${id}`, null, t.worker)).json.verification;
    assert.ok(detail.warnings.some((w) => w.code === 'date_conflict'), JSON.stringify(detail.warnings));
    const r = await t.call('POST', `/verifications/${id}/approve`, {}, t.worker);
    assert.equal(r.status, 400);
    assert.match(r.json.error, /cannot be given before Pentavalent 1/);
    assert.equal(t.db.prepare("SELECT given_date FROM doses WHERE child_id = ? AND code = 'PENTA2'").get(child.id).given_date, null);
    assert.equal(t.db.prepare('SELECT status FROM vaccination_verifications WHERE id = ?').get(id).status, 'pending');

    // If the worker records the dose directly meanwhile, the request can no longer be approved.
    const ref2 = (await t.upload(child, 'card', png(240, 240, 11))).json.document.ref;
    const id2 = (await t.submit(child, { doseCode: 'BCG', vaccinationDate: '2026-06-01', cardDocument: ref2 })).json.verification.id;
    await t.call('POST', `/children/${child.id}/doses/BCG/given`, { date: '2026-06-02' }, t.worker);
    const late = await t.call('POST', `/verifications/${id2}/approve`, {}, t.worker);
    assert.equal(late.json.code, 'already_given');
    assert.equal(t.db.prepare("SELECT given_date FROM doses WHERE child_id = ? AND code = 'BCG'").get(child.id).given_date, '2026-06-02');
  } finally {
    t.close();
  }
});

test('reminders continue while evidence waits and stop once it is approved', async () => {
  const t = await setup();
  try {
    // Born 6 weeks ago today: the 6-week doses are due today.
    const child = await t.register({ childName: 'Tumaini Test', phone: '0711000209', dob: '2026-09-13' });
    for (const code of ['BCG', 'OPV0']) await t.call('POST', `/children/${child.id}/doses/${code}/given`, { date: '2026-09-13' }, t.worker);
    const ref = (await t.upload(child, 'card', png())).json.document.ref;
    const id = (await t.submit(child, { doseCode: 'PENTA1', vaccinationDate: '2026-10-24', cardDocument: ref })).json.verification.id;

    const asked = await runReminders(t.db, today);
    assert.deepEqual(asked.map((r) => r.kind), ['due'], 'evidence alone does not stop reminders');
    assert.match(t.db.prepare('SELECT message FROM sms_log ORDER BY id DESC').get().message, /Pentavalent 1/);

    await t.call('POST', `/verifications/${id}/approve`, {}, t.worker);
    t.db.exec('DELETE FROM reminders');
    await runReminders(t.db, today);
    assert.doesNotMatch(t.db.prepare('SELECT message FROM sms_log ORDER BY id DESC').get().message, /Pentavalent 1/);
  } finally {
    t.close();
  }
});

test('deleting a child removes its stored pictures, and unreadable files are reported', async () => {
  const t = await setup();
  try {
    const child = await t.register({ childName: 'Kioko Test', phone: '0711000210', dob: '2026-08-16' });
    const ref = (await t.upload(child, 'card', png())).json.document.ref;
    const id = (await t.submit(child, { doseCode: 'PCV1', vaccinationDate: '2026-10-01', cardDocument: ref })).json.verification.id;
    const doc = t.db.prepare('SELECT id, storage_name FROM verification_documents').get();
    const file = path.join(t.dir, doc.storage_name);
    const bytes = fs.readFileSync(file);
    fs.writeFileSync(file, Buffer.concat([bytes.subarray(0, bytes.length - 1), Buffer.from([bytes[bytes.length - 1] ^ 1])]));
    const r = await t.call('GET', `/verifications/${id}/documents/${doc.id}`, null, t.worker);
    assert.equal(r.status, 410);
    assert.equal(r.json.code, 'document_unreadable');

    assert.equal((await t.call('DELETE', `/children/${child.id}`, null, t.worker)).status, 200);
    assert.deepEqual(fs.readdirSync(t.dir), []);
    assert.equal(t.db.prepare('SELECT COUNT(*) AS n FROM vaccination_verifications').get().n, 0);
  } finally {
    t.close();
  }
});
