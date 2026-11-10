import bcrypt from 'bcryptjs';
import { env } from '../config/env';

/**
 * Password hashing (bcrypt, cost from env).
 *
 * bcrypt truncates at 72 bytes — passwords are validated to 8..72 characters in
 * the request schemas so the truncation can never happen silently.
 */

export const PASSWORD_MIN_LENGTH = 8;
export const PASSWORD_MAX_LENGTH = 72;

export async function hashPassword(plain: string): Promise<string> {
  return bcrypt.hash(plain, env.BCRYPT_ROUNDS);
}

export async function verifyPassword(plain: string, hash: string | null): Promise<boolean> {
  if (!hash) return false;
  try {
    return await bcrypt.compare(plain, hash);
  } catch {
    return false;
  }
}
