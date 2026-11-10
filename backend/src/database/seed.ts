import { env } from '../config/env';
import { DEFAULT_ADMIN_PERMISSIONS } from '../config/constants';
import { addDays, eachDate, todayIn, timeToMinutes } from '../utils/datetime';
import { logger } from '../utils/logger';
import { hashPassword } from '../utils/password';
import { bootstrap, type GymRecord } from './setup';
import { closePool, pool, queryOne } from './pool';

/**
 * `npm run seed` — DEVELOPMENT ONLY.
 *
 * Creates one Admin and twelve members with realistic membership + attendance
 * history so the dashboards, leaderboard, calendars and "expiring/expired/
 * archived" tabs have something meaningful to show. Never runs in production.
 */

const DEMO_PASSWORD = 'Member@12345';
const DEMO_ADMIN_PASSWORD = 'Admin@12345';

interface DemoMember {
  fullName: string;
  email: string;
  phone: string;
  gender: 'male' | 'female' | 'other';
  shift: 'Morning' | 'Evening';
  plan: 'Monthly' | 'Quarterly' | 'Half-Yearly' | 'Yearly';
  /** Days from today until membership end. Negative = already expired. */
  endsInDays: number;
  /** Rough share of gym days attended, 0..1. */
  attendanceRate: number;
  archived?: boolean;
  presentToday?: boolean;
}

const DEMO_MEMBERS: DemoMember[] = [
  { fullName: 'Aarav Sharma', email: 'aarav@example.com', phone: '+91 98765 11001', gender: 'male', shift: 'Morning', plan: 'Quarterly', endsInDays: 41, attendanceRate: 0.95, presentToday: true },
  { fullName: 'Priya Nair', email: 'priya@example.com', phone: '+91 98765 11002', gender: 'female', shift: 'Morning', plan: 'Monthly', endsInDays: 12, attendanceRate: 0.9, presentToday: true },
  { fullName: 'Rohan Mehta', email: 'rohan@example.com', phone: '+91 98765 11003', gender: 'male', shift: 'Evening', plan: 'Yearly', endsInDays: 210, attendanceRate: 0.85, presentToday: true },
  { fullName: 'Sneha Iyer', email: 'sneha@example.com', phone: '+91 98765 11004', gender: 'female', shift: 'Evening', plan: 'Half-Yearly', endsInDays: 96, attendanceRate: 0.8, presentToday: false },
  { fullName: 'Vikram Singh', email: 'vikram@example.com', phone: '+91 98765 11005', gender: 'male', shift: 'Morning', plan: 'Monthly', endsInDays: 5, attendanceRate: 0.75, presentToday: false },
  { fullName: 'Ananya Rao', email: 'ananya@example.com', phone: '+91 98765 11006', gender: 'female', shift: 'Morning', plan: 'Quarterly', endsInDays: 64, attendanceRate: 0.7, presentToday: false },
  { fullName: 'Kabir Khan', email: 'kabir@example.com', phone: '+91 98765 11007', gender: 'male', shift: 'Evening', plan: 'Monthly', endsInDays: 3, attendanceRate: 0.6, presentToday: false },
  { fullName: 'Meera Joshi', email: 'meera@example.com', phone: '+91 98765 11008', gender: 'female', shift: 'Evening', plan: 'Quarterly', endsInDays: 73, attendanceRate: 0.55, presentToday: false },
  { fullName: 'Arjun Patel', email: 'arjun@example.com', phone: '+91 98765 11009', gender: 'male', shift: 'Morning', plan: 'Half-Yearly', endsInDays: 121, attendanceRate: 0.5, presentToday: false },
  { fullName: 'Diya Kapoor', email: 'diya@example.com', phone: '+91 98765 11010', gender: 'female', shift: 'Morning', plan: 'Monthly', endsInDays: -4, attendanceRate: 0.35 },
  { fullName: 'Ishaan Verma', email: 'ishaan@example.com', phone: '+91 98765 11011', gender: 'male', shift: 'Evening', plan: 'Monthly', endsInDays: -100, attendanceRate: 0.2, archived: true },
  { fullName: 'Nisha Gupta', email: 'nisha@example.com', phone: '+91 98765 11012', gender: 'female', shift: 'Evening', plan: 'Yearly', endsInDays: 180, attendanceRate: 0.65, presentToday: true },
];

async function ensureDemoAdmin(gym: GymRecord): Promise<void> {
  const email = 'admin@squadgym.app';
  const existing = await queryOne<{ id: string }>('select id from users where email = $1', [email]);
  if (existing) return;
  const passwordHash = await hashPassword(DEMO_ADMIN_PASSWORD);
  await pool.query(
    `insert into users (gym_id, email, password_hash, role, status, full_name, phone, permissions)
     values ($1, $2, $3, 'admin', 'active', 'Riya Admin', '+91 98765 10000', $4)`,
    [gym.id, email, passwordHash, DEFAULT_ADMIN_PERMISSIONS],
  );
}

async function seedMembers(gym: GymRecord): Promise<void> {
  const today = todayIn(gym.timezone);
  const memberPasswordHash = await hashPassword(DEMO_PASSWORD);

  const shifts = await pool.query<{ id: string; name: string }>(
    'select id, name from gym_shifts where gym_id = $1',
    [gym.id],
  );
  const plans = await pool.query<{ id: string; name: string; duration_days: number }>(
    'select id, name, duration_days from membership_plans where gym_id = $1',
    [gym.id],
  );

  const shiftByName = new Map(shifts.rows.map((row) => [row.name.toLowerCase(), row.id]));
  const planByName = new Map(plans.rows.map((row) => [row.name.toLowerCase(), row]));

  const historyDays = 28;
  const historyStart = addDays(today, -historyDays);
  const calendar = eachDate(historyStart, today);

  for (const [index, demo] of DEMO_MEMBERS.entries()) {
    const shiftId = shiftByName.get(demo.shift.toLowerCase()) ?? null;
    const plan = planByName.get(demo.plan.toLowerCase());
    if (!plan) throw new Error(`Seed plan missing: ${demo.plan}`);

    const joinedOn = addDays(today, -Math.max(30, Math.abs(demo.endsInDays) + plan.duration_days));
    const existing = await queryOne<{ id: string }>('select id from users where email = $1', [demo.email]);

    let memberId = existing?.id;
    if (!memberId) {
      const inserted = await queryOne<{ id: string }>(
        `insert into users (gym_id, email, password_hash, role, status, full_name, phone, permissions)
         values ($1, $2, $3, 'member', 'active', $4, $5, '{}')
         returning id`,
        [gym.id, demo.email, memberPasswordHash, demo.fullName, demo.phone],
      );
      if (!inserted) throw new Error(`Failed to seed member ${demo.email}`);
      memberId = inserted.id;
      await pool.query(
        `insert into member_profiles
           (user_id, gym_id, gender, shift_id, joined_on, archived_at, emergency_contact_name, emergency_contact_phone)
         values ($1, $2, $3, $4, $5, $6, 'Emergency Contact', '+91 90000 00000')`,
        [
          memberId,
          gym.id,
          demo.gender,
          shiftId,
          joinedOn,
          demo.archived ? new Date().toISOString() : null,
        ],
      );
    }

    const membershipEnd = addDays(today, demo.endsInDays);
    const membershipStart = addDays(membershipEnd, -(plan.duration_days - 1));
    const hasMembership = await queryOne<{ id: string }>(
      'select id from memberships where member_id = $1 limit 1',
      [memberId],
    );
    if (!hasMembership) {
      await pool.query(
        `insert into memberships (gym_id, member_id, plan_id, start_date, end_date, status)
         values ($1, $2, $3, $4, $5, 'active')`,
        [gym.id, memberId, plan.id, membershipStart, membershipEnd],
      );
    }

    // Attendance history: deterministic pseudo-random pattern per member/day.
    const attendanceDates: string[] = [];
    for (const [dayIndex, date] of calendar.entries()) {
      const hash = (index * 31 + dayIndex * 17) % 100;
      const attended = hash / 100 < demo.attendanceRate;
      if (!attended) continue;
      if (date < membershipStart || date > membershipEnd) continue;
      attendanceDates.push(date);
    }

    for (const date of attendanceDates) {
      const checkInMinute = timeToMinutes(demo.shift === 'Morning' ? '06:30' : '18:15') + ((index * 7) % 40);
      const markedAt = new Date(`${date}T00:00:00.000Z`);
      markedAt.setUTCMinutes(checkInMinute);
      await pool.query(
        `insert into attendance (gym_id, member_id, attendance_date, marked_at, undo_expires_at, status, source)
         values ($1, $2, $3, $4, $4, 'active', 'manual')
         on conflict do nothing`,
        [gym.id, memberId, date, markedAt.toISOString()],
      );
    }

    if (demo.presentToday && !demo.archived) {
      const checkIn = new Date(Date.now() - (10 + index * 3) * 60_000);
      const record = await queryOne<{ id: string }>(
        `insert into attendance (gym_id, member_id, attendance_date, marked_at, undo_expires_at, status)
         values ($1, $2, $3, $4, $4, 'active')
         on conflict do nothing
         returning id`,
        [gym.id, memberId, today, checkIn.toISOString()],
      );
      if (record) {
        const expiresAt = new Date(checkIn.getTime() + gym.in_gym_window_minutes * 60_000);
        if (expiresAt.getTime() > Date.now()) {
          await pool.query(
            `insert into gym_sessions (gym_id, member_id, attendance_id, checked_in_at, expires_at, status)
             values ($1, $2, $3, $4, $5, 'active')
             on conflict (attendance_id) do nothing`,
            [gym.id, memberId, record.id, checkIn.toISOString(), expiresAt.toISOString()],
          );
        }
      }
    }
  }
}

async function seedOccupancySnapshot(gym: GymRecord): Promise<void> {
  const exists = await queryOne<{ count: string }>(
    'select count(*)::text as count from occupancy_snapshots where gym_id = $1',
    [gym.id],
  );
  if (exists && Number(exists.count) > 0) return;

  const yesterday = new Date(Date.now() - 86_400_000);
  yesterday.setUTCSeconds(0, 0);
  yesterday.setUTCMinutes(yesterday.getUTCMinutes() - (yesterday.getUTCMinutes() % 5));
  await pool.query(
    `insert into occupancy_snapshots (gym_id, bucket_start, captured_at, active_count, attendance_count, snapshot_date)
     values ($1, $2, $2, 7, 14, $3)
     on conflict (gym_id, bucket_start) do nothing`,
    [gym.id, yesterday.toISOString(), todayIn(gym.timezone, yesterday)],
  );
}

async function main(): Promise<void> {
  if (env.isProduction) {
    throw new Error('Refusing to seed demo data with NODE_ENV=production');
  }

  const gym = await bootstrap();
  await ensureDemoAdmin(gym);
  await seedMembers(gym);
  await seedOccupancySnapshot(gym);

  console.log(
    [
      '',
      '──────────────────────────────────────────────',
      ' SquadGym demo data seeded',
      '──────────────────────────────────────────────',
      ` Gym          : ${gym.name}`,
      ' Super Admin  : ' + gym.superAdminEmail,
      ' Admin        : admin@squadgym.app / ' + DEMO_ADMIN_PASSWORD,
      ' Member       : aarav@example.com / ' + DEMO_PASSWORD,
      ` Members      : ${DEMO_MEMBERS.length} (1 expired, 1 archived, 2 expiring soon)`,
      '──────────────────────────────────────────────',
      '',
    ].join('\n'),
  );
  logger.info('Seed complete');
}

main()
  .catch((error) => {
    logger.error('Seed failed', { message: (error as Error).message });
    process.exitCode = 1;
  })
  .finally(() => closePool());
