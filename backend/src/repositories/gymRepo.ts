import type { PoolClient } from 'pg';
import { query, queryOne, withTransaction } from '../database/pool';

export interface GymRow {
  id: string;
  name: string;
  slug: string;
  timezone: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  logo_key: string | null;
  expiring_soon_days: number;
  archive_after_days: number;
  in_gym_window_minutes: number;
  attendance_undo_seconds: number;
  renewal_undo_seconds: number;
  created_at: Date;
  updated_at: Date;
}

export interface ShiftRow {
  id: string;
  gym_id: string;
  name: string;
  start_time: string;
  end_time: string;
  sort_order: number;
  is_active: boolean;
}

export interface OperatingDayRow {
  gym_id: string;
  day_of_week: number;
  is_open: boolean;
  opens_at: string;
  closes_at: string;
}

export interface PlanRow {
  id: string;
  gym_id: string;
  name: string;
  duration_days: number;
  price_cents: number;
  currency: string;
  description: string | null;
  is_active: boolean;
  sort_order: number;
  created_at: Date;
  updated_at: Date;
}

export interface GymSettings {
  id: string;
  name: string;
  timezone: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  expiringSoonDays: number;
  archiveAfterDays: number;
  inGymWindowMinutes: number;
  attendanceUndoSeconds: number;
  renewalUndoSeconds: number;
}

export function toGymSettings(gym: GymRow): GymSettings {
  return {
    id: gym.id,
    name: gym.name,
    timezone: gym.timezone,
    address: gym.address,
    phone: gym.phone,
    email: gym.email,
    expiringSoonDays: gym.expiring_soon_days,
    archiveAfterDays: gym.archive_after_days,
    inGymWindowMinutes: gym.in_gym_window_minutes,
    attendanceUndoSeconds: gym.attendance_undo_seconds,
    renewalUndoSeconds: gym.renewal_undo_seconds,
  };
}

export async function getGym(gymId: string): Promise<GymRow | null> {
  return queryOne<GymRow>('select * from gyms where id = $1', [gymId]);
}

export async function requireGym(gymId: string): Promise<GymRow> {
  const gym = await getGym(gymId);
  if (!gym) throw new Error(`Gym ${gymId} not found`);
  return gym;
}

export interface GymSettingsPatch {
  name?: string;
  timezone?: string;
  address?: string | null;
  phone?: string | null;
  email?: string | null;
  expiringSoonDays?: number;
  archiveAfterDays?: number;
  inGymWindowMinutes?: number;
  attendanceUndoSeconds?: number;
  renewalUndoSeconds?: number;
}

const COLUMN_BY_SETTING: Record<keyof GymSettingsPatch, string> = {
  name: 'name',
  timezone: 'timezone',
  address: 'address',
  phone: 'phone',
  email: 'email',
  expiringSoonDays: 'expiring_soon_days',
  archiveAfterDays: 'archive_after_days',
  inGymWindowMinutes: 'in_gym_window_minutes',
  attendanceUndoSeconds: 'attendance_undo_seconds',
  renewalUndoSeconds: 'renewal_undo_seconds',
};

export async function updateGymSettings(gymId: string, patch: GymSettingsPatch): Promise<GymRow> {
  const assignments: string[] = [];
  const params: unknown[] = [gymId];
  for (const [key, column] of Object.entries(COLUMN_BY_SETTING) as Array<[keyof GymSettingsPatch, string]>) {
    const value = patch[key];
    if (value === undefined) continue;
    params.push(value);
    assignments.push(`${column} = $${params.length}`);
  }
  if (assignments.length === 0) {
    return requireGym(gymId);
  }
  const row = await queryOne<GymRow>(
    `update gyms set ${assignments.join(', ')} where id = $1 returning *`,
    params,
  );
  if (!row) throw new Error(`Gym ${gymId} not found`);
  return row;
}

export async function getOperatingDays(gymId: string): Promise<OperatingDayRow[]> {
  return query<OperatingDayRow>(
    'select * from gym_operating_days where gym_id = $1 order by day_of_week',
    [gymId],
  );
}

export async function replaceOperatingDays(
  gymId: string,
  days: Array<{ dayOfWeek: number; isOpen: boolean; opensAt?: string; closesAt?: string }>,
): Promise<OperatingDayRow[]> {
  return withTransaction(async (client: PoolClient) => {
    for (const day of days) {
      await client.query(
        `insert into gym_operating_days (gym_id, day_of_week, is_open, opens_at, closes_at)
         values ($1, $2, $3, coalesce($4::time, '05:00'), coalesce($5::time, '23:00'))
         on conflict (gym_id, day_of_week) do update
           set is_open = excluded.is_open,
               opens_at = coalesce($4::time, gym_operating_days.opens_at),
               closes_at = coalesce($5::time, gym_operating_days.closes_at)`,
        [gymId, day.dayOfWeek, day.isOpen, day.opensAt ?? null, day.closesAt ?? null],
      );
    }
    const result = await client.query<OperatingDayRow>(
      'select * from gym_operating_days where gym_id = $1 order by day_of_week',
      [gymId],
    );
    return result.rows;
  });
}

export async function listShifts(gymId: string, includeInactive = false): Promise<ShiftRow[]> {
  return query<ShiftRow>(
    includeInactive
      ? 'select * from gym_shifts where gym_id = $1 order by sort_order, name'
      : 'select * from gym_shifts where gym_id = $1 and is_active = true order by sort_order, name',
    [gymId],
  );
}

export async function getShift(gymId: string, shiftId: string): Promise<ShiftRow | null> {
  return queryOne<ShiftRow>('select * from gym_shifts where gym_id = $1 and id = $2', [gymId, shiftId]);
}

export async function createShift(
  gymId: string,
  input: { name: string; startTime: string; endTime: string; sortOrder?: number },
): Promise<ShiftRow> {
  const row = await queryOne<ShiftRow>(
    `insert into gym_shifts (gym_id, name, start_time, end_time, sort_order)
     values ($1, $2, $3, $4, $5) returning *`,
    [gymId, input.name, input.startTime, input.endTime, input.sortOrder ?? 0],
  );
  if (!row) throw new Error('Failed to create shift');
  return row;
}

export async function updateShift(
  gymId: string,
  shiftId: string,
  patch: { name?: string; startTime?: string; endTime?: string; sortOrder?: number; isActive?: boolean },
): Promise<ShiftRow | null> {
  const row = await queryOne<ShiftRow>(
    `update gym_shifts
        set name = coalesce($3, name),
            start_time = coalesce($4::time, start_time),
            end_time = coalesce($5::time, end_time),
            sort_order = coalesce($6, sort_order),
            is_active = coalesce($7, is_active)
      where gym_id = $1 and id = $2
      returning *`,
    [
      gymId,
      shiftId,
      patch.name ?? null,
      patch.startTime ?? null,
      patch.endTime ?? null,
      patch.sortOrder ?? null,
      patch.isActive ?? null,
    ],
  );
  return row;
}

export async function listPlans(gymId: string, includeInactive = false): Promise<PlanRow[]> {
  return query<PlanRow>(
    includeInactive
      ? 'select * from membership_plans where gym_id = $1 order by sort_order, name'
      : 'select * from membership_plans where gym_id = $1 and is_active = true order by sort_order, name',
    [gymId],
  );
}

export async function getPlan(gymId: string, planId: string): Promise<PlanRow | null> {
  return queryOne<PlanRow>('select * from membership_plans where gym_id = $1 and id = $2', [gymId, planId]);
}

export async function createPlan(
  gymId: string,
  input: {
    name: string;
    durationDays: number;
    priceCents?: number;
    currency?: string;
    description?: string | null;
    sortOrder?: number;
  },
): Promise<PlanRow> {
  const row = await queryOne<PlanRow>(
    `insert into membership_plans (gym_id, name, duration_days, price_cents, currency, description, sort_order)
     values ($1, $2, $3, $4, $5, $6, $7) returning *`,
    [
      gymId,
      input.name,
      input.durationDays,
      input.priceCents ?? 0,
      input.currency ?? 'INR',
      input.description ?? null,
      input.sortOrder ?? 0,
    ],
  );
  if (!row) throw new Error('Failed to create plan');
  return row;
}

export async function updatePlan(
  gymId: string,
  planId: string,
  patch: {
    name?: string;
    durationDays?: number;
    priceCents?: number;
    currency?: string;
    description?: string | null;
    isActive?: boolean;
    sortOrder?: number;
  },
): Promise<PlanRow | null> {
  return queryOne<PlanRow>(
    `update membership_plans
        set name = coalesce($3, name),
            duration_days = coalesce($4, duration_days),
            price_cents = coalesce($5, price_cents),
            currency = coalesce($6, currency),
            description = coalesce($7, description),
            is_active = coalesce($8, is_active),
            sort_order = coalesce($9, sort_order)
      where gym_id = $1 and id = $2
      returning *`,
    [
      gymId,
      planId,
      patch.name ?? null,
      patch.durationDays ?? null,
      patch.priceCents ?? null,
      patch.currency ?? null,
      patch.description ?? null,
      patch.isActive ?? null,
      patch.sortOrder ?? null,
    ],
  );
}
