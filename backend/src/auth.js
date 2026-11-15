import crypto from 'node:crypto';
import { promisify } from 'node:util';

const scrypt = promisify(crypto.scrypt);
const KEY_LEN = 64;
export const SESSION_DAYS = 30;

// Stored as "scrypt$<salt hex>$<hash hex>" so the format can change later without breaking old rows.
export async function hashPassword(password) {
  const salt = crypto.randomBytes(16);
  const hash = await scrypt(password, salt, KEY_LEN);
  return `scrypt$${salt.toString('hex')}$${hash.toString('hex')}`;
}

export async function verifyPassword(password, stored) {
  const [scheme, saltHex, hashHex] = String(stored || '').split('$');
  if (scheme !== 'scrypt' || !saltHex || !hashHex) return false;
  const expected = Buffer.from(hashHex, 'hex');
  const actual = await scrypt(password, Buffer.from(saltHex, 'hex'), expected.length);
  return crypto.timingSafeEqual(expected, actual);
}

// Returns a problem description, or null when the password is acceptable.
export function passwordProblem(password) {
  if (typeof password !== 'string' || password.length < 8) return 'Password must be at least 8 characters';
  if (password.length > 128) return 'Password is too long';
  if (!/[A-Za-z]/.test(password) || !/\d/.test(password)) return 'Password must contain letters and numbers';
  return null;
}

export function newToken() {
  return crypto.randomBytes(32).toString('base64url');
}

// Only the hash of a session token is stored, so a copy of the database does not let anyone log in.
export function tokenHash(token) {
  return crypto.createHash('sha256').update(String(token)).digest('hex');
}

export function safeEqual(a, b) {
  const x = crypto.createHash('sha256').update(String(a)).digest();
  const y = crypto.createHash('sha256').update(String(b)).digest();
  return crypto.timingSafeEqual(x, y);
}

// Sliding-window counter. `hit` returns false once `max` events happened inside `windowMs`.
export function makeLimiter(max, windowMs) {
  const hits = new Map();
  let calls = 0;
  return {
    hit(key) {
      const now = Date.now();
      if (++calls % 200 === 0) {
        for (const [k, arr] of hits) if (!arr.length || now - arr[arr.length - 1] >= windowMs) hits.delete(k);
      }
      const arr = (hits.get(key) || []).filter((t) => now - t < windowMs);
      arr.push(now);
      hits.set(key, arr);
      return arr.length <= max;
    },
    count(key) {
      const now = Date.now();
      return (hits.get(key) || []).filter((t) => now - t < windowMs).length;
    },
    reset(key) {
      hits.delete(key);
    },
  };
}
