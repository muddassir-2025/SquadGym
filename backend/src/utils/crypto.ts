import { createHash, randomBytes, timingSafeEqual } from 'node:crypto';

/** URL-safe opaque token (default 48 bytes → 64 chars). */
export function randomToken(bytes = 48): string {
  return randomBytes(bytes).toString('base64url');
}

/** Tokens are stored hashed; the plaintext only ever lives in the email/response. */
export function sha256(value: string): string {
  return createHash('sha256').update(value).digest('hex');
}

export function safeEqual(left: string, right: string): boolean {
  const a = Buffer.from(left);
  const b = Buffer.from(right);
  if (a.length !== b.length) return false;
  return timingSafeEqual(a, b);
}

/** Deterministic, human-friendly booking reference (used in audit metadata). */
export function shortId(prefix = ''): string {
  const token = randomBytes(6).toString('hex');
  return prefix ? `${prefix}_${token}` : token;
}
