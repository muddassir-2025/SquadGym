import { query, queryOne } from '../database/pool';
import { eachDate } from '../utils/datetime';
import { isOpenDay } from '../services/scoring';

/** Occupancy snapshots + dashboard aggregates (spec §4.A, §4.B, §8). */

export interface SnapshotRow {
  active_count: number;
  attendance_count: number;
  captured_at: Date;
}

export interface DashboardCounts {
  inGymNow: number;
  totalMembers: number;
  todayAttendance: number;
  yesterdayMembers: number;
  yesterdayAttendance: number;
}

export async function dashboardCounts(
  gymId: string,
  today: string,
  yesterday: string,
  timezone: string,
): Promise<DashboardCounts> {
  const row = await queryOne<{
    in_gym_now: number;
    total_members: number;
    today_attendance: number;
    yesterday_members: number;
    yesterday_attendance: number;
  }>(
    `select
       (select count(*)::int
          from gym_sessions s
          join users u on u.id = s.member_id
          join member_profiles mp on mp.user_id = u.id
         where s.gym_id = $1 and s.status = 'active' and s.checked_out_at is null
           and s.expires_at > now() and u.status = 'active' and mp.archived_at is null) as in_gym_now,
       (select count(*)::int
          from users u join member_profiles mp on mp.user_id = u.id
         where u.gym_id = $1 and u.role = 'member' and u.status = 'active' and mp.archived_at is null) as total_members,
       (select count(distinct a.member_id)::int
          from attendance a
         where a.gym_id = $1 and a.attendance_date = $2::date and a.status = 'active') as today_attendance,
       (select count(*)::int
          from users u join member_profiles mp on mp.user_id = u.id
         where u.gym_id = $1 and u.role = 'member' and u.status <> 'inactive'
           and mp.joined_on <= $3::date
           and (mp.archived_at is null or (mp.archived_at at time zone $4)::date > $3::date)) as yesterday_members,
       (select count(distinct a.member_id)::int
          from attendance a
         where a.gym_id = $1 and a.attendance_date = $3::date and a.status = 'active') as yesterday_attendance`,
    [gymId, today, yesterday, timezone],
  );

  return {
    inGymNow: row?.in_gym_now ?? 0,
    totalMembers: row?.total_members ?? 0,
    todayAttendance: row?.today_attendance ?? 0,
    yesterdayMembers: row?.yesterday_members ?? 0,
    yesterdayAttendance: row?.yesterday_attendance ?? 0,
  };
}

/** Closest snapshot to a target instant, within `toleranceMinutes`. */
export async function findSnapshotNear(
  gymId: string,
  target: Date,
  toleranceMinutes = 15,
): Promise<SnapshotRow | null> {
  return queryOne<SnapshotRow>(
    `select active_count, attendance_count, captured_at
       from occupancy_snapshots
      where gym_id = $1
        and captured_at between $2::timestamptz - ($3 || ' minutes')::interval
                           and $2::timestamptz + ($3 || ' minutes')::interval
      order by abs(extract(epoch from (captured_at - $2::timestamptz)))
      limit 1`,
    [gymId, target.toISOString(), toleranceMinutes],
  );
}

export async function occupancySnapshots(
  gymId: string,
  date: string,
): Promise<Array<{ captured_at: Date; active_count: number; attendance_count: number }>> {
  return query<{ captured_at: Date; active_count: number; attendance_count: number }>(
    `select captured_at, active_count, attendance_count
       from occupancy_snapshots
      where gym_id = $1 and snapshot_date = $2::date
      order by captured_at`,
    [gymId, date],
  );
}

export async function captureSnapshot(input: {
  gymId: string;
  activeCount: number;
  attendanceCount: number;
  snapshotDate: string;
  bucketStart: Date;
}): Promise<void> {
  await query(
    `insert into occupancy_snapshots (gym_id, bucket_start, active_count, attendance_count, snapshot_date)
     values ($1, $2, $3, $4, $5)
     on conflict (gym_id, bucket_start) do update
       set active_count = excluded.active_count,
           attendance_count = excluded.attendance_count`,
    [input.gymId, input.bucketStart.toISOString(), input.activeCount, input.attendanceCount, input.snapshotDate],
  );
}

export type ChartMetric = 'attendance' | 'new_members' | 'total_members';

export interface ChartPointRow {
  date: string;
  value: number;
}

export async function chartSeries(input: {
  gymId: string;
  metric: ChartMetric;
  from: string;
  to: string;
  timezone: string;
}): Promise<ChartPointRow[]> {
  const { gymId, metric, from, to, timezone } = input;

  if (metric === 'attendance') {
    return query<ChartPointRow>(
      `select d::date::text as date, count(distinct a.member_id)::int as value
         from generate_series($2::date, $3::date, interval '1 day') d
         left join attendance a
           on a.gym_id = $1 and a.attendance_date = d::date and a.status = 'active'
        group by d
        order by d`,
      [gymId, from, to],
    );
  }

  if (metric === 'new_members') {
    return query<ChartPointRow>(
      `select d::date::text as date, count(mp.user_id)::int as value
         from generate_series($2::date, $3::date, interval '1 day') d
         left join member_profiles mp on mp.gym_id = $1 and mp.joined_on = d::date
        group by d
        order by d`,
      [gymId, from, to],
    );
  }

  return query<ChartPointRow>(
    `select d::date::text as date,
            (select count(*)::int
               from users u join member_profiles mp on mp.user_id = u.id
              where u.gym_id = $1 and u.role = 'member' and u.status <> 'inactive'
                and mp.joined_on <= d::date
                and (mp.archived_at is null or (mp.archived_at at time zone $4)::date > d::date)) as value
       from generate_series($2::date, $3::date, interval '1 day') d
      order by d`,
    [gymId, from, to, timezone],
  );
}

/** Gym-open flags for a date range, so charts can mark closed days. */
export function openDayMap(
  operatingDays: Array<{ day_of_week: number; is_open: boolean }>,
  from: string,
  to: string,
): Map<string, boolean> {
  const openWeekdays = new Set(
    operatingDays.filter((day) => day.is_open).map((day) => day.day_of_week),
  );
  const map = new Map<string, boolean>();
  for (const date of eachDate(from, to)) {
    map.set(date, isOpenDay(date, openWeekdays));
  }
  return map;
}
