import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

// Pictures of vaccination cards and doctor's notes that caregivers send as evidence. They are
// medical records, so they are encrypted on disk (AES-256-GCM), stored under random names, and only
// ever read back through the health worker API.

export const MAX_DOCUMENT_BYTES = 5 * 1024 * 1024;
export const MIN_DOCUMENT_BYTES = 1024;
export const DOCUMENT_TYPES = ['image/jpeg', 'image/png'];

const SIGNATURES = [
  { type: 'image/jpeg', bytes: [0xff, 0xd8, 0xff] },
  { type: 'image/png', bytes: [0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a] },
];

// The real type of an image from its first bytes, whatever the upload claims. Null when it is
// neither a JPEG nor a PNG.
export function detectImageType(buf) {
  const sig = SIGNATURES.find((s) => buf.length >= s.bytes.length && s.bytes.every((b, i) => buf[i] === b));
  return sig ? sig.type : null;
}

// Width and height from the image header, or null when the header cannot be read. Used to refuse
// files that only pretend to be pictures.
export function imageSize(buf, type) {
  if (type === 'image/png') {
    if (buf.length < 24 || buf.toString('latin1', 12, 16) !== 'IHDR') return null;
    return { width: buf.readUInt32BE(16), height: buf.readUInt32BE(20) };
  }
  // JPEG: walk the segments until a start-of-frame marker, which holds the size.
  let i = 2;
  while (i + 9 < buf.length) {
    if (buf[i] !== 0xff) return null;
    const marker = buf[i + 1];
    if (marker === 0xff) {
      i++;
      continue;
    }
    const length = buf.readUInt16BE(i + 2);
    const isFrame = marker >= 0xc0 && marker <= 0xcf && ![0xc4, 0xc8, 0xcc].includes(marker);
    if (isFrame) return { width: buf.readUInt16BE(i + 7), height: buf.readUInt16BE(i + 5) };
    if (length < 2) return null;
    i += 2 + length;
  }
  return null;
}

// Returns null when the upload is an acceptable picture, otherwise a short reason.
export function documentProblem(buf, claimedType) {
  if (buf.length < MIN_DOCUMENT_BYTES) return 'The file is empty or too small to be a photo';
  if (buf.length > MAX_DOCUMENT_BYTES) return `The file is larger than ${MAX_DOCUMENT_BYTES / 1024 / 1024} MB`;
  const type = detectImageType(buf);
  if (!type) return 'Only JPEG or PNG photos can be uploaded';
  if (claimedType && claimedType !== type) return 'The file type does not match its content';
  const size = imageSize(buf, type);
  if (!size || size.width < 200 || size.height < 200) return 'The photo is too small to read. Take it closer or use a larger picture';
  if (size.width > 12000 || size.height > 12000) return 'The photo is too large';
  return null;
}

// The key comes from DOCUMENT_KEY (64 hex characters) or, failing that, from `keyFile`, which is
// created on first use. Losing the key makes the stored documents unreadable.
export function loadDocumentKey(keyFile, env = process.env) {
  const fromEnv = String(env.DOCUMENT_KEY || '').trim();
  if (fromEnv) {
    if (!/^[0-9a-fA-F]{64}$/.test(fromEnv)) throw new Error('DOCUMENT_KEY must be 64 hexadecimal characters (32 bytes)');
    return Buffer.from(fromEnv, 'hex');
  }
  try {
    const saved = fs.readFileSync(keyFile, 'utf8').trim();
    if (!/^[0-9a-fA-F]{64}$/.test(saved)) throw new Error(`${keyFile} does not hold a valid document key`);
    return Buffer.from(saved, 'hex');
  } catch (e) {
    if (e.code !== 'ENOENT') throw e;
  }
  const key = crypto.randomBytes(32);
  fs.writeFileSync(keyFile, key.toString('hex') + '\n', { mode: 0o600, flag: 'wx' });
  console.warn(`Created ${keyFile} to encrypt uploaded documents. Keep it private and back it up with the database.`);
  return key;
}

export function createDocumentStore({ dir, key }) {
  if (!Buffer.isBuffer(key) || key.length !== 32) throw new Error('Document key must be 32 bytes');
  fs.mkdirSync(dir, { recursive: true, mode: 0o700 });
  const fileOf = (name) => {
    if (!/^[0-9a-f]{32}$/.test(String(name))) throw new Error('Bad document name');
    return path.join(dir, name);
  };
  return {
    // Encrypts and writes the bytes; returns the random name to store in the database.
    save(buf) {
      const name = crypto.randomBytes(16).toString('hex');
      const iv = crypto.randomBytes(12);
      const cipher = crypto.createCipheriv('aes-256-gcm', key, iv);
      const sealed = Buffer.concat([cipher.update(buf), cipher.final()]);
      fs.writeFileSync(fileOf(name), Buffer.concat([iv, cipher.getAuthTag(), sealed]), { mode: 0o600, flag: 'wx' });
      return name;
    },
    // Throws when the file is missing or was changed on disk (the GCM tag no longer matches).
    read(name) {
      const raw = fs.readFileSync(fileOf(name));
      const decipher = crypto.createDecipheriv('aes-256-gcm', key, raw.subarray(0, 12));
      decipher.setAuthTag(raw.subarray(12, 28));
      return Buffer.concat([decipher.update(raw.subarray(28)), decipher.final()]);
    },
    remove(name) {
      try {
        fs.unlinkSync(fileOf(name));
      } catch (e) {
        if (e.code !== 'ENOENT') throw e;
      }
    },
  };
}

export function sha256(buf) {
  return crypto.createHash('sha256').update(buf).digest('hex');
}
