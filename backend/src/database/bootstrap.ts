import { closePool } from './pool';
import { bootstrap } from './setup';
import { logger } from '../utils/logger';
import { env } from '../config/env';

/**
 * `npm run bootstrap`
 *
 * Creates/updates the single Super Admin account and the gym's default
 * configuration. Run this once after `npm run migrate` on a fresh Neon database.
 */
async function main(): Promise<void> {
  const result = await bootstrap();
  const lines = [
    '',
    '──────────────────────────────────────────────',
    ' SquadGym bootstrap complete',
    '──────────────────────────────────────────────',
    ` Gym          : ${result.name} (${result.timezone})`,
    ` Super Admin  : ${result.superAdminEmail}`,
    ` Account      : ${result.superAdminCreated ? 'created' : 'already existed — password unchanged'}`,
    '',
    result.superAdminCreated && !env.isProduction
      ? ` Password     : ${env.SUPER_ADMIN_PASSWORD}  (from .env, change it after first login)`
      : ' Password     : set in .env / rotate through the API',
    '──────────────────────────────────────────────',
    '',
  ];
  console.log(lines.join('\n'));
}

main()
  .catch((error) => {
    logger.error('Bootstrap failed', { message: (error as Error).message });
    process.exitCode = 1;
  })
  .finally(() => closePool());
