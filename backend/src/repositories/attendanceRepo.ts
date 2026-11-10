import type { PoolClient } from 'pg';
import { query, queryOne, withTransaction } from '../database/pool';

export interface AttendanceRow {
  id: string;
  gym_id: string;
  member_id: string;
  attendance_date: string;
  marked_by: string | null;
  marked_at: Date;
  undo_expires_at: Date;
  source: string;
  status: 'active' | 'undone' | 'corrected';
  undone_at: Date | null;
  undone_by: string | null;
  corrected_at: Date | null;
  corrected_by: string | null;
  note: string | null;
  created_at: Date;
}

export interface SessionRow {
  id: string;
  gym_id: string;
  member_id: string;
  attendance_id: string;
  checked_in_at: Date;
  expires_at: Date;
  checked_out_at: Date | null;
  checked_out_by: string | null;
  status: 'active' | 'expired' | 'checked_out' | 'voided';
  created_at: Date;
}

export interface AttendanceMemberRow {
  id: string;
  email: string;
  full_name: string;
  phone: string | null;
  avatar_object_key: string | null;
  avatar_version: number;
  gender: string | null;
  shift_id: string | null;
  shift_name: string | null;
  plan_id: string | null;
  plan_name: string | null;
  start_date: string | null;
  end_date: string | null;
  attendance_id: string | null;
  marked_at: Date | null;
  undo_expires_at: Date | null;
  session_id: string | null;
  session_status: SessionRow['status'] | null;
  checked_in_at: Date | null;
  expires_at: Date | null;
  checked_out_at: Date | null;
  score: number;
}

export type AttendanceTab = 'all' | 'present' | 'absent';

export interface AttendanceFilter {
  tab: AttendanceTab;
  search?: string;
  shiftId?: string;
  gender?: string;
  planId?: string;
}

export interface AttendanceContext {
  gymId: string;
  date: string;
  scoreFrom: string;
  scoreTo: string;
}

const ELIGIBLE_FROM = `
  from users u
  join member_profiles mp on mp.user_id = u.id
  left join gym_shifts sh on sh.id = mp.shift_id
  join lateral (
    select m.id, m.plan_id, m.start_date, m.end_date
      from memberships m
     where m.member_id = u.id
       and m.status in ('active', 'superseded')
       and m.start_date <= $2::date
       and m.end_date >= $2::date
     order by m.end_date desc
     limit 1
  ) cm on true
  left join membership_plans p on p.id = cm.plan_id
  left join attendance a on a.member_id = u.id and a.attendance_date = $2::date and a.status = 'active'
  left join gym_sessions s on s.attendance_id = a.id
  left join scored sc on sc.member_id = u.id
`;

const ELIGIBLE_SELECT = `
  select u.id, u.email::text as email, u.full_name, u.phone, u.avatar_object_key, u.avatar_version,
         mp.gender, sh.id as shift_id, sh.name as shift_name,
         cm.plan_id, p.name as plan_name, cm.start_date, cm.end_date,
         a.id as attendance_id, a.marked_at, a.undo_expires_at,
         s.id as session_id, s.status as session_status,
         s.checked_in_at, s.expires_at, s.checked_out_at,
         coalesce(sc.score, 0)::int as score
`;

function buildFilters(context: AttendanceContext, filter: AttendanceFilter) {
  const params: unknown[] = [context.gymId, context.date, context.scoreFrom, context.scoreTo];
  const arg = (value: unknown): string => {
    params.push(value);
    return `$${params.length}`;
  };
  const conditions: string[] = [
    'u.gym_id = $1',
    "u.role = 'member'",
    "u.status = 'active'",
    'mp.archived_at is null',
  ];

  if (filter.search) {
    const pattern = arg(`%${filter.search.trim()}%`);
    conditions.push(
      `(u.full_name ilike ${pattern} or u.email::text ilike ${pattern} or coalesce(u.phone, '') ilike ${pattern})`,
    );
  }
  if (filter.shiftId) conditions.push(`mp.shift_id = ${arg(filter.shiftId)}::uuid`);
  if (filter.gender) conditions.push(`mp.gender = ${arg(filter.gender)}`);
  if (filter.planId) conditions.push(`cm.plan_id = ${arg(filter.planId)}::uuid`);
  if (filter.tab === 'present') conditions.push('a.id is not null');
  if (filter.tab === 'absent') conditions.push('a.id is null');

  return { params, whereSql: conditions.join(' and ') };
}

function scoredCte(): string {
  return `with scored as (
    select a.member_id, count(*)::int as score
      from attendance a
     where a.gym_id = $1 and a.status = 'active'
       and a.attendance_date between $3::date and $4::date
     group by a.member_id
  )`;
}

export async function listAttendanceMembers(
  context: AttendanceContext,
  filter: AttendanceFilter,
  page: { limit: number; offset: number },
): Promise<{ items: AttendanceMemberRow[]; total: number }> {
  const built = buildFilters(context, filter);

  const countRow = await queryOne<{ total: number }>(
    `${scoredCte()} select count(*)::int as total ${ELIGIBLE_FROM} where ${built.whereSql}`,
    built.params,
  );

  const params = [...built.params, page.limit, page.offset];
  const orderSql =
    filter.tab === 'present'
      ? 'a.marked_at desc, u.full_name asc'
      : 'u.full_name asc, u.id asc';

  const items = await query<AttendanceMemberRow>(
    `${scoredCte()} ${ELIGIBLE_SELECT} ${ELIGIBLE_FROM}
      where ${built.whereSql}
      order by ${orderSql}
      limit $${params.length - 1} offset $${params.length}`,
    params,
  );

  return { items, total: countRow?.total ?? 0 };
}

export interface AttendanceSummaryRow {
  total: number;
  present: number;
  absent: number;
}

export async function attendanceSummary(
  context: AttendanceContext,
  filter: AttendanceFilter,
): Promise<AttendanceSummaryRow> {
  const built = buildFilters(context, { ...filter, tab: 'all' });
  const row = await queryOne<{ total: number; present: number }>(
    `${scoredCte()}
     select count(*)::int as total, count(a.id)::int as present
     ${ELIGIBLE_FROM} where ${built.whereSql}`,
    built.params,
  );
  const total = row?.total ?? 0;
  const present = row?.present ?? 0;
  return { total, present, absent: Math.max(0, total - present) };
}

export async function getActiveAttendance(
  gymId: string,
  memberId: string,
  date: string,
): Promise<AttendanceRow | null> {
  return queryOne<AttendanceRow>(
    `select * from attendance
      where gym_id = $1 and member_id = $2 and attendance_date = $3::date and status = 'active'`,
    [gymId, memberId, date],
  );
}

export async function getAttendanceById(id: string): Promise<AttendanceRow | null> {
  return queryOne<AttendanceRow>('select * from attendance where id = $1', [id]);
}

export interface InsertAttendanceResult {
  attendance: AttendanceRow;
  created: boolean;
}

/**
 * Idempotent mark-present. The partial unique index arbitrates concurrency: the
 * loser of the race reads the winner's row instead of creating a duplicate.
 */
export async function insertAttendance(input: {
  gymId: string;
  memberId: string;
  date: string;
  markedBy: string;
  undoExpiresAt: Date;
  source?: 'manual' | 'correction';
}): Promise<InsertAttendanceResult> {
  const inserted = await queryOne<AttendanceRow>(
    `insert into attendance (gym_id, member_id, attendance_date, marked_by, undo_expires_at, source)
     values ($1, $2, $3::date, $4, $5, $6)
     on conflict do nothing
     returning *`,
    [input.gymId, input.memberId, input.date, input.markedBy, input.undoExpiresAt, input.source ?? 'manual'],
  );
  if (inserted) return { attendance: inserted, created: true };

  const existing = await getActiveAttendance(input.gymId, input.memberId, input.date);
  if (!existing) {
    throw new Error('Attendance insert conflicted but no active record exists');
  }
  return { attendance: existing, created: false };
}

export async function markAttendanceUndone(
  attendanceId: string,
  actorId: string,
  client?: PoolClient,
): Promise<AttendanceRow | null> {
  const sql = `update attendance
                  set status = 'undone', undone_at = now(), undone_by = $2
                where id = $1 and status = 'active'
                returning *`;
  if (client) {
    const result = await client.query<AttendanceRow>(sql, [attendanceId, actorId]);
    return result.rows[0] ?? null;
  }
  return queryOne<AttendanceRow>(sql, [attendanceId, actorId]);
}

export async function markAttendanceCorrected(
  attendanceId: string,
  actorId: string,
  note: string,
  client?: PoolClient,
): Promise<AttendanceRow | null> {
  const sql = `update attendance
                  set status = 'corrected', corrected_at = now(), corrected_by = $2, note = $3
                where id = $1 and status = 'active'
                returning *`;
  if (client) {
    const result = await client.query<AttendanceRow>(sql, [attendanceId, actorId, note]);
    return result.rows[0] ?? null;
  }
  return queryOne<AttendanceRow>(sql, [attendanceId, actorId, note]);
}

// ── Gym sessions ────────────────────────────────────────────────────────────

export async function createSession(input: {
  gymId: string;
  memberId: string;
  attendanceId: string;
  checkedInAt?: Date;
  expiresAt: Date;
}): Promise<SessionRow | null> {
  return queryOne<SessionRow>(
    `insert into gym_sessions (gym_id, member_id, attendance_id, checked_in_at, expires_at)
     values ($1, $2, $3, coalesce($4::timestamptz, now()), $5)
     on conflict (attendance_id) do nothing
     returning *`,
    [input.gymId, input.memberId, input.attendanceId, input.checkedInAt ?? null, input.expiresAt],
  );
}

export async function getSessionByAttendance(attendanceId: string): Promise<SessionRow | null> {
  return queryOne<SessionRow>('select * from gym_sessions where attendance_id = $1', [attendanceId]);
}

export async function voidSessionForAttendance(
  attendanceId: string,
  actorId: string,
  client?: PoolClient,
): Promise<void> {
  const sql = `update gym_sessions
                  set status = 'voided', checked_out_at = now(), checked_out_by = $2
                where attendance_id = $1 and status = 'active'`;
  if (client) await client.query(sql, [attendanceId, actorId]);
  else await query(sql, [attendanceId, actorId]);
}

/** Archiving a member must immediately remove them from the occupancy count. */
export async function voidActiveSessionsForMember(memberId: string, actorId: string): Promise<void> {
  await query(
    `update gym_sessions
        set status = 'voided', checked_out_at = now(), checked_out_by = $2
      where member_id = $1 and status = 'active'`,
    [memberId, actorId],
  );
}

export interface ActiveSessionRow {
  session_id: string;
  member_id: string;
  full_name: string;
  email: string;
  avatar_object_key: string | null;
  avatar_version: number;
  shift_name: string | null;
  checked_in_at: Date;
  expires_at: Date;
  attendance_date: string;
}

export async function listActiveSessions(gymId: string): Promise<ActiveSessionRow[]> {
  return query<ActiveSessionRow>(
    `select s.id as session_id, u.id as member_id, u.full_name, u.email::text as email,
            u.avatar_object_key, u.avatar_version, sh.name as shift_name,
            s.checked_in_at, s.expires_at, a.attendance_date
       from gym_sessions s
       join users u on u.id = s.member_id
       join attendance a on a.id = s.attendance_id
       join member_profiles mp on mp.user_id = u.id
       left join gym_shifts sh on sh.id = mp.shift_id
      where s.gym_id = $1
        and s.status = 'active'
        and s.checked_out_at is null
        and s.expires_at > now()
        and mp.archived_at is null
      order by s.checked_in_at desc`,
    [gymId],
  );
}

export async function checkoutSession(
  gymId: string,
  sessionId: string,
  actorId: string,
): Promise<SessionRow | null> {
  return queryOne<SessionRow>(
    `update gym_sessions
        set status = 'checked_out', checked_out_at = now(), checked_out_by = $3
      where gym_id = $1 and id = $2 and status = 'active' and checked_out_at is null
      returning *`,
    [gymId, sessionId, actorId],
  );
}

export async function expireStaleSessions(gymId: string): Promise<number> {
  const rows = await query<{ id: string }>(
    `update gym_sessions
        set status = 'expired'
      where gym_id = $1 and status = 'active' and expires_at <= now()
      returning id`,
    [gymId],
  );
  return rows.length;
}

export async function countInGymNow(gymId: string): Promise<number> {
  const row = await queryOne<{ count: number }>(
    `select count(*)::int as count
       from gym_sessions s
       join users u on u.id = s.member_id
       join member_profiles mp on mp.user_id = u.id
      where s.gym_id = $1
        and s.status = 'active'
        and s.checked_out_at is null
        and s.expires_at > now()
        and u.status = 'active'
        and mp.archived_at is null`,
    [gymId],
  );
  return row?.count ?? 0;
}

// ── History / reporting ─────────────────────────────────────────────────────

export interface AttendanceHistoryRow extends AttendanceRow {
  marked_by_name: string | null;
  attendance_session_status: string | null;
}

export async function memberAttendanceHistory(
  gymId: string,
  memberId: string,
  from: string,
  to: string,
): Promise<AttendanceHistoryRow[]> {
  return query<AttendanceHistoryRow>(
    `select a.*, actor.full_name as marked_by_name, s.status as attendance_session_status
       from attendance a
       left join users actor on actor.id = a.marked_by
       left join gym_sessions s on s.attendance_id = a.id
      where a.gym_id = $1 and a.member_id = $2 and a.attendance_date between $3::date and $4::date
      order by a.attendance_date desc`,
    [gymId, memberId, from, to],
  );
}

export async function attendanceDates(
  gymId: string,
  memberId: string,
  from: string,
  to: string,
): Promise<string[]> {
  const rows = await query<{ attendance_date: string }>(
    `select attendance_date from attendance
      where gym_id = $1 and member_id = $2 and status = 'active'
        and attendance_date between $3::date and $4::date
      order by attendance_date`,
    [gymId, memberId, from, to],
  );
  return rows.map((row) => row.attendance_date);
}

export async function countAttendanceOn(gymId: string, date: string): Promise<number> {
  const row = await queryOne<{ count: number }>(
    `select count(distinct member_id)::int as count from attendance
      where gym_id = $1 and attendance_date = $2::date and status = 'active'`,
    [gymId, date],
  );
  return row?.count ?? 0;
}

export async function attendanceTotalsByDate(
  gymId: string,
  from: string,
  to: string,
): Promise<Map<string, number>> {
  const rows = await query<{ attendance_date: string; count: number }>(
    `select attendance_date, count(distinct member_id)::int as count
       from attendance
      where gym_id = $1 and status = 'active' and attendance_date between $2::date and $3::date
      group by attendance_date`,
    [gymId, from, to],
  );
  return new Map(rows.map((row) => [row.attendance_date, row.count]));
}

export async function scoresForMembers(
  gymId: string,
  memberIds: string[],
  from: string,
  to: string,
): Promise<Map<string, number>> {
  if (memberIds.length === 0) return new Map();
  const rows = await query<{ member_id: string; score: number }>(
    `select member_id, count(*)::int as score
       from attendance
      where gym_id = $1 and status = 'active' and member_id = any($2::uuid[])
        and attendance_date between $3::date and $4::date
      group by member_id`,
    [gymId, memberIds, from, to],
  );
  return new Map(rows.map((row) => [row.member_id, row.score]));
}

export async function attendanceDatesForMembers(
  gymId: string,
  memberIds: string[],
  from: string,
  to: string,
): Promise<Map<string, string[]>> {
  if (memberIds.length === 0) return new Map();
  const rows = await query<{ member_id: string; attendance_date: string }>(
    `select member_id, attendance_date
       from attendance
      where gym_id = $1 and status = 'active' and member_id = any($2::uuid[])
        and attendance_date between $3::date and $4::date
      order by attendance_date`,
    [gymId, memberIds, from, to],
  );
  const result = new Map<string, string[]>();
  for (const row of rows) {
    const existing = result.get(row.member_id);
    if (existing) existing.push(row.attendance_date);
    else result.set(row.member_id, [row.attendance_date]);
  }
  return result;
}

export { withTransaction };
