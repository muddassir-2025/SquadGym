import { env, gymTimezone } from '../config/env';
import { SUPER_ADMIN_PERMISSIONS } from '../config/constants';
import { logger } from '../utils/logger';
import { hashPassword } from '../utils/password';
import { todayIn } from '../utils/datetime';
import { pool, query, queryOne } from './pool';

/**
 * Idempotent first-run setup: gym, schedule, shifts, plans and the single Super
 * Admin account. Safe to run repeatedly (deploy hooks, local reset, tests).
 */

export interface GymRecord {
  id: string;
  name: string;
  slug: string;
  timezone: string;
  expiring_soon_days: number;
  archive_after_days: number;
  in_gym_window_minutes: number;
  attendance_undo_seconds: number;
  renewal_undo_seconds: number;
}

export const GYM_SLUG = 'squad-gym';

export async function ensureGym(): Promise<GymRecord> {
  const existing = await queryOne<GymRecord>('select * from gyms where slug = $1', [GYM_SLUG]);
  if (existing) return existing;

  const created = await queryOne<GymRecord>(
    `insert into gyms (
       name, slug, timezone, expiring_soon_days, archive_after_days,
       in_gym_window_minutes, attendance_undo_seconds, renewal_undo_seconds
     ) values ($1, $2, $3, $4, $5, $6, $7, $8)
     returning *`,
    [
      env.GYM_NAME,
      GYM_SLUG,
      gymTimezone,
      env.DEFAULT_EXPIRING_SOON_DAYS,
      env.DEFAULT_ARCHIVE_AFTER_DAYS,
      env.DEFAULT_IN_GYM_WINDOW_MINUTES,
      env.DEFAULT_ATTENDANCE_UNDO_SECONDS,
      env.DEFAULT_RENEWAL_UNDO_SECONDS,
    ],
  );
  if (!created) throw new Error('Failed to create the gym row');
  logger.info('Gym created', { gymId: created.id, name: created.name });
  return created;
}

export async function ensureGymDefaults(gymId: string): Promise<void> {
  // Operating days: all seven open by default; the Super Admin can close any day.
  for (let day = 1; day <= 7; day += 1) {
    await pool.query(
      `insert into gym_operating_days (gym_id, day_of_week, is_open, opens_at, closes_at)
       values ($1, $2, true, '05:00', '23:00')
       on conflict (gym_id, day_of_week) do nothing`,
      [gymId, day],
    );
  }

  // Default shifts. Labels are editable per gym (e.g. a gym can add "Night").
  await pool.query(
    `insert into gym_shifts (gym_id, name, start_time, end_time, sort_order)
     values ($1, 'Morning', '06:00', '12:00', 0), ($1, 'Evening', '17:00', '23:00', 1)
     on conflict (gym_id, lower(name)) do nothing`,
    [gymId],
  );

  await pool.query(
    `insert into membership_plans (gym_id, name, duration_days, price_cents, sort_order, description)
     values
       ($1, 'Monthly', 30, 0, 0, '30 days'),
       ($1, 'Quarterly', 90, 0, 1, '90 days'),
       ($1, 'Half-Yearly', 180, 0, 2, '180 days'),
       ($1, 'Yearly', 365, 0, 3, '365 days')
     on conflict (gym_id, lower(name)) do nothing`,
    [gymId],
  );
}

export interface EnsureSuperAdminResult {
  userId: string;
  created: boolean;
  email: string;
}

export async function ensureSuperAdmin(gymId: string): Promise<EnsureSuperAdminResult> {
  const email = env.SUPER_ADMIN_EMAIL.toLowerCase();
  const existing = await queryOne<{ id: string }>('select id from users where email = $1', [email]);

  if (existing) {
    await query(
      `update users
          set role = 'super_admin',
              status = 'active',
              permissions = $2,
              gym_id = $3
        where id = $1`,
      [existing.id, SUPER_ADMIN_PERMISSIONS, gymId],
    );
    return { userId: existing.id, created: false, email };
  }

  const passwordHash = await hashPassword(env.SUPER_ADMIN_PASSWORD);
  const created = await queryOne<{ id: string }>(
    `insert into users (gym_id, email, password_hash, role, status, full_name, permissions)
     values ($1, $2, $3, 'super_admin', 'active', $4, $5)
     returning id`,
    [gymId, email, passwordHash, env.SUPER_ADMIN_NAME, SUPER_ADMIN_PERMISSIONS],
  );
  if (!created) throw new Error('Failed to create the Super Admin account');
  logger.info('Super Admin created', { email });
  return { userId: created.id, created: true, email };
}

/** Convenience used by `npm run bootstrap` and the server's optional auto-bootstrap. */
export async function bootstrap(): Promise<GymRecord & { superAdminEmail: string; superAdminCreated: boolean }> {
  const gym = await ensureGym();
  await ensureGymDefaults(gym.id);
  const superAdmin = await ensureSuperAdmin(gym.id);
  logger.info('Bootstrap complete', {
    gym: gym.name,
    timezone: gym.timezone,
    today: todayIn(gym.timezone),
    superAdmin: superAdmin.email,
    superAdminCreated: superAdmin.created,
  });
  return { ...gym, superAdminEmail: superAdmin.email, superAdminCreated: superAdmin.created };
}
