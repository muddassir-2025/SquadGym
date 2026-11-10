import { Pool, types, type PoolClient, type QueryResultRow } from 'pg';
import { env } from '../config/env';
import { logger } from '../utils/logger';

/**
 * Postgres `date` (OID 1082) must stay a plain `YYYY-MM-DD` string.
 *
 * The default pg parser turns a DATE into a JS Date at *server-local* midnight,
 * which silently shifts gym-local calendar days. Every date in this product is a
 * gym-local calendar day, so the string is the only safe representation.
 */
types.setTypeParser(1082, (value: string) => value);

/**
 * Single shared connection pool.
 *
 * Neon requires TLS; the pooled hostname (`-pooler`) plus `sslmode=require`
 * works with pg's `ssl` option. We keep the pool small because Neon's free tier
 * limits concurrent connections and the API is stateless.
 */
const needsSsl =
  env.isProduction ||
  /sslmode=(require|verify-ca|verify-full)/.test(env.DATABASE_URL) ||
  env.DATABASE_URL.includes('neon.tech');

export const pool = new Pool({
  connectionString: env.DATABASE_URL,
  max: env.isProduction ? 10 : 5,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 15_000,
  ...(needsSsl ? { ssl: { rejectUnauthorized: false } } : {}),
});

pool.on('error', (error) => {
  logger.error('Unexpected idle Postgres client error', { message: error.message });
});

export async function query<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params: readonly unknown[] = [],
): Promise<T[]> {
  const started = Date.now();
  try {
    const result = await pool.query<T>(text, params as unknown[]);
    const duration = Date.now() - started;
    if (duration > 750) logger.warn('Slow query', { duration, sql: text.slice(0, 120) });
    return result.rows;
  } catch (error) {
    logger.error('Query failed', { message: (error as Error).message, sql: text.slice(0, 200) });
    throw error;
  }
}

export async function queryOne<T extends QueryResultRow = QueryResultRow>(
  text: string,
  params: readonly unknown[] = [],
): Promise<T | null> {
  const rows = await query<T>(text, params);
  return rows[0] ?? null;
}

/**
 * Run `work` inside a transaction. Any thrown error rolls back.
 * `isolationLevel` defaults to READ COMMITTED; money/ledger style operations can
 * ask for SERIALIZABLE.
 */
export async function withTransaction<T>(
  work: (client: PoolClient) => Promise<T>,
  options: { isolationLevel?: 'READ COMMITTED' | 'REPEATABLE READ' | 'SERIALIZABLE' } = {},
): Promise<T> {
  const client = await pool.connect();
  try {
    await client.query(`BEGIN ISOLATION LEVEL ${options.isolationLevel ?? 'READ COMMITTED'}`);
    const result = await work(client);
    await client.query('COMMIT');
    return result;
  } catch (error) {
    try {
      await client.query('ROLLBACK');
    } catch {
      /* connection already broken */
    }
    throw error;
  } finally {
    client.release();
  }
}

/** Postgres `unique_violation`. */
export function isUniqueViolation(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && (error as { code?: string }).code === '23505');
}

export function isForeignKeyViolation(error: unknown): boolean {
  return Boolean(error && typeof error === 'object' && (error as { code?: string }).code === '23503');
}

export async function closePool(): Promise<void> {
  await pool.end();
}
