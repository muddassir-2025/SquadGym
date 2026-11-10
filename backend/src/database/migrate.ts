import { createHash } from 'node:crypto';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve } from 'node:path';
import { closePool, pool, withTransaction } from './pool';
import { logger } from '../utils/logger';

/**
 * Minimal, deterministic SQL migration runner.
 *
 * * Files are applied in filename order inside `migrations/`.
 * * Each file runs in its own transaction and is recorded with a checksum.
 * * Re-running is a no-op; editing an already-applied file is an error (so
 *   environments cannot silently drift).
 */

export function resolveMigrationsDir(): string {
  const candidates = [
    resolve(process.cwd(), 'migrations'),
    resolve(__dirname, '../../migrations'),
    resolve(__dirname, '../../../migrations'),
  ];
  const found = candidates.find((candidate) => existsSync(candidate));
  if (!found) throw new Error(`Could not locate the migrations directory (tried: ${candidates.join(', ')})`);
  return found;
}

const CREATE_TRACKING_TABLE = `
create table if not exists schema_migrations (
  version text primary key,
  checksum text not null,
  applied_at timestamptz not null default now(),
  duration_ms integer not null default 0
)`;

export async function runMigrations(): Promise<{ applied: string[]; skipped: string[] }> {
  // Serialise concurrent boots (Render rolling deploys, several instances).
  await pool.query('select pg_advisory_lock($1)', [MIGRATION_LOCK_KEY]);
  try {
    return await applyMigrations();
  } finally {
    await pool.query('select pg_advisory_unlock($1)', [MIGRATION_LOCK_KEY]).catch(() => undefined);
  }
}

const MIGRATION_LOCK_KEY = 727_272_001;

async function applyMigrations(): Promise<{ applied: string[]; skipped: string[] }> {
  const directory = resolveMigrationsDir();
  const files = readdirSync(directory)
    .filter((file) => file.endsWith('.sql'))
    .sort();

  await pool.query(CREATE_TRACKING_TABLE);
  const existing = await pool.query<{ version: string; checksum: string }>(
    'select version, checksum from schema_migrations',
  );
  const appliedChecksums = new Map(existing.rows.map((row) => [row.version, row.checksum]));

  const applied: string[] = [];
  const skipped: string[] = [];

  for (const file of files) {
    const sql = readFileSync(resolve(directory, file), 'utf8');
    const checksum = createHash('sha256').update(sql).digest('hex');
    const previous = appliedChecksums.get(file);

    if (previous) {
      if (previous !== checksum) {
        throw new Error(
          `Migration ${file} changed after it was applied. Add a new migration instead of editing this one.`,
        );
      }
      skipped.push(file);
      continue;
    }

    const started = Date.now();
    await withTransaction(async (client) => {
      await client.query(sql);
      await client.query(
        'insert into schema_migrations (version, checksum, duration_ms) values ($1, $2, $3)',
        [file, checksum, Date.now() - started],
      );
    });
    applied.push(file);
    logger.info(`Migration applied: ${file}`, { durationMs: Date.now() - started });
  }

  return { applied, skipped };
}

async function main(): Promise<void> {
  const { applied, skipped } = await runMigrations();
  logger.info('Migrations complete', { applied: applied.length, skipped: skipped.length });
  await closePool();
}

if (require.main === module) {
  main().catch(async (error) => {
    logger.error('Migration failed', { message: (error as Error).message });
    await closePool().catch(() => undefined);
    process.exitCode = 1;
  });
}
