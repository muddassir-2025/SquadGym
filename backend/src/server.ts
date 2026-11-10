import { createApp } from './app';
import { env } from './config/env';
import { closePool, pool } from './database/pool';
import { runMigrations } from './database/migrate';
import { startScheduler, type SchedulerHandle } from './services/scheduler';
import { logger } from './utils/logger';

/**
 * Entry point.
 *
 * Migrations run on boot (tracked + advisory-locked, so concurrent instances are
 * safe). Set `SKIP_MIGRATIONS=true` to manage them out-of-band in CI.
 */
async function main(): Promise<void> {
  if (process.env.SKIP_MIGRATIONS !== 'true') {
    const { applied } = await runMigrations();
    if (applied.length > 0) logger.info('Migrations applied on boot', { count: applied.length });
  }

  const app = createApp();
  const server = app.listen(env.PORT, () => {
    logger.info('SquadGym API listening', {
      port: env.PORT,
      env: env.NODE_ENV,
      baseUrl: env.API_BASE_URL,
    });
  });

  let scheduler: SchedulerHandle | null = null;
  if (!env.isTest) scheduler = startScheduler();

  const shutdown = async (signal: string): Promise<void> => {
    logger.info('Shutting down', { signal });
    scheduler?.stop();
    server.close(() => undefined);
    await closePool().catch(() => undefined);
    setTimeout(() => process.exit(0), 250).unref();
  };

  process.on('SIGTERM', () => void shutdown('SIGTERM'));
  process.on('SIGINT', () => void shutdown('SIGINT'));
  process.on('unhandledRejection', (reason) => {
    logger.error('Unhandled promise rejection', {
      message: reason instanceof Error ? reason.message : String(reason),
    });
  });
  process.on('uncaughtException', (error) => {
    logger.error('Uncaught exception', { message: error.message, stack: error.stack });
  });

  // Fail fast if the database is unreachable at boot.
  try {
    await pool.query('select 1');
    logger.info('Database connection verified');
  } catch (error) {
    logger.error('Database connection failed', { message: (error as Error).message });
    process.exitCode = 1;
    await closePool().catch(() => undefined);
  }
}

void main().catch(async (error: unknown) => {
  logger.error('Fatal startup error', { message: (error as Error).message, stack: (error as Error).stack });
  await closePool().catch(() => undefined);
  process.exit(1);
});
