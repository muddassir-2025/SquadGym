import type { PoolClient } from 'pg';
import { DEFAULT_ADMIN_PERMISSIONS, type Permission, type Role, type UserStatus } from '../config/constants';
import { isUniqueViolation, query, queryOne, withTransaction } from '../database/pool';

export interface UserRow {
  id: string;
  gym_id: string;
  email: string;
  password_hash: string | null;
  role: Role;
  status: UserStatus;
  full_name: string;
  phone: string | null;
  avatar_object_key: string | null;
  avatar_version: number;
  permissions: Permission[];
  last_login_at: Date | null;
  created_at: Date;
  updated_at: Date;
}

export interface MemberRow {
  id: string;
  email: string;
  full_name: string;
  phone: string | null;
  account_status: UserStatus;
  avatar_object_key: string | null;
  avatar_version: number;
  gender: string | null;
  address: string | null;
  joined_on: string;
  archived_at: Date | null;
  emergency_contact_name: string | null;
  emergency_contact_phone: string | null;
  shift_id: string | null;
  shift_name: string | null;
  membership_id: string | null;
  plan_id: string | null;
  plan_name: string | null;
  start_date: string | null;
  end_date: string | null;
  score: number;
}

export type MemberTab = 'all' | 'expiring' | 'expired' | 'archived';
export type MemberSort = 'score_desc' | 'score_asc' | 'newest' | 'oldest' | 'expiry_asc' | 'name_asc';

export interface MemberListFilter {
  tab: MemberTab;
  search?: string;
  gender?: string;
  planId?: string;
  shiftId?: string;
  status?: 'active' | 'expiring' | 'expired' | 'none';
  minScore?: number;
  maxScore?: number;
  expiresFrom?: string;
  expiresTo?: string;
  joinedFrom?: string;
  joinedTo?: string;
  sort?: MemberSort;
}

export interface MemberQueryContext {
  gymId: string;
  today: string;
  expiringSoonDays: number;
  scoreFrom: string;
  scoreTo: string;
}

export async function findUserByEmail(email: string): Promise<UserRow | null> {
  return queryOne<UserRow>('select * from users where email = $1', [email]);
}

export async function findUserById(id: string): Promise<UserRow | null> {
  return queryOne<UserRow>('select * from users where id = $1', [id]);
}

export async function touchLastLogin(userId: string): Promise<void> {
  await query('update users set last_login_at = now() where id = $1', [userId]);
}

export async function setPasswordHash(userId: string, passwordHash: string): Promise<void> {
  await query(
    `update users set password_hash = $2, status = case when status = 'invited' then 'active' else status end
      where id = $1`,
    [userId, passwordHash],
  );
}

export async function setUserStatus(userId: string, status: UserStatus): Promise<UserRow | null> {
  return queryOne<UserRow>('update users set status = $2 where id = $1 returning *', [userId, status]);
}

export async function setUserPermissions(userId: string, permissions: Permission[]): Promise<UserRow | null> {
  return queryOne<UserRow>('update users set permissions = $2 where id = $1 returning *', [userId, permissions]);
}

export async function updateUserProfile(
  userId: string,
  patch: {
    fullName?: string;
    phone?: string | null;
    avatarObjectKey?: string | null;
    avatarVersion?: number;
    email?: string;
  },
): Promise<UserRow | null> {
  return queryOne<UserRow>(
    `update users
        set full_name = coalesce($2, full_name),
            phone = coalesce($3, phone),
            avatar_object_key = coalesce($4, avatar_object_key),
            avatar_version = coalesce($5, avatar_version),
            email = coalesce($6, email)
      where id = $1
      returning *`,
    [
      userId,
      patch.fullName ?? null,
      patch.phone === undefined ? null : patch.phone,
      patch.avatarObjectKey ?? null,
      patch.avatarVersion ?? null,
      patch.email ?? null,
    ],
  );
}

export async function bumpAvatarVersion(userId: string, objectKey: string): Promise<UserRow | null> {
  return queryOne<UserRow>(
    `update users set avatar_object_key = $2, avatar_version = avatar_version + 1
      where id = $1 returning *`,
    [userId, objectKey],
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Member queries
// ─────────────────────────────────────────────────────────────────────────────

const MEMBER_SELECT = `
  select
    u.id,
    u.email::text as email,
    u.full_name,
    u.phone,
    u.status as account_status,
    u.avatar_object_key,
    u.avatar_version,
    mp.gender,
    mp.address,
    mp.joined_on,
    mp.archived_at,
    mp.emergency_contact_name,
    mp.emergency_contact_phone,
    sh.id as shift_id,
    sh.name as shift_name,
    cm.id as membership_id,
    cm.plan_id,
    p.name as plan_name,
    cm.start_date,
    cm.end_date,
    coalesce(sc.score, 0)::int as score
  from users u
  join member_profiles mp on mp.user_id = u.id
  left join gym_shifts sh on sh.id = mp.shift_id
  left join v_current_membership cm on cm.member_id = u.id
  left join membership_plans p on p.id = cm.plan_id
  left join scored sc on sc.member_id = u.id
`;

function scoredCte(scoreFrom: string, scoreTo: string): string {
  // $4 / $5 are the score window parameters.
  return `with scored as (
    select a.member_id, count(*)::int as score
    from attendance a
    where a.gym_id = $1 and a.status = 'active'
      and a.attendance_date between $4::date and $5::date
    group by a.member_id
  )`;
}

interface BuiltFilter {
  fromSql: string;
  whereSql: string;
  orderSql: string;
  params: unknown[];
  countWhereSql: string;
  countFromSql: string;
}

/**
 * Builds the shared member filter. Parameters $1..$5 are fixed:
 *   1 gymId, 2 today, 3 expiringSoonDays, 4 scoreFrom, 5 scoreTo.
 */
export function buildMemberFilter(context: MemberQueryContext, filter: MemberListFilter): BuiltFilter {
  const params: unknown[] = [
    context.gymId,
    context.today,
    context.expiringSoonDays,
    context.scoreFrom,
    context.scoreTo,
  ];
  const arg = (value: unknown): string => {
    params.push(value);
    return `$${params.length}`;
  };

  const conditions: string[] = ['u.gym_id = $1', "u.role = 'member'"];

  switch (filter.tab) {
    case 'archived':
      conditions.push('mp.archived_at is not null');
      break;
    case 'expiring':
      conditions.push('mp.archived_at is null');
      conditions.push('cm.end_date is not null');
      conditions.push('cm.end_date >= $2::date');
      conditions.push('cm.end_date <= ($2::date + $3::int)');
      break;
    case 'expired':
      conditions.push('mp.archived_at is null');
      conditions.push('cm.end_date is not null');
      conditions.push('cm.end_date < $2::date');
      break;
    case 'all':
    default:
      conditions.push('mp.archived_at is null');
      break;
  }

  if (filter.search) {
    const pattern = arg(`%${filter.search.trim()}%`);
    conditions.push(
      `(u.full_name ilike ${pattern} or u.email::text ilike ${pattern} or coalesce(u.phone, '') ilike ${pattern})`,
    );
  }
  if (filter.gender) conditions.push(`mp.gender = ${arg(filter.gender)}`);
  if (filter.planId) conditions.push(`cm.plan_id = ${arg(filter.planId)}::uuid`);
  if (filter.shiftId) conditions.push(`mp.shift_id = ${arg(filter.shiftId)}::uuid`);
  if (filter.minScore !== undefined) conditions.push(`coalesce(sc.score, 0) >= ${arg(filter.minScore)}`);
  if (filter.maxScore !== undefined) conditions.push(`coalesce(sc.score, 0) <= ${arg(filter.maxScore)}`);
  if (filter.expiresFrom) conditions.push(`cm.end_date >= ${arg(filter.expiresFrom)}::date`);
  if (filter.expiresTo) conditions.push(`cm.end_date <= ${arg(filter.expiresTo)}::date`);
  if (filter.joinedFrom) conditions.push(`mp.joined_on >= ${arg(filter.joinedFrom)}::date`);
  if (filter.joinedTo) conditions.push(`mp.joined_on <= ${arg(filter.joinedTo)}::date`);
  if (filter.status === 'none') conditions.push('cm.id is null');
  if (filter.status === 'active') {
    conditions.push('cm.end_date >= $2::date');
    conditions.push('cm.end_date > ($2::date + $3::int)');
  }
  if (filter.status === 'expiring') {
    conditions.push('cm.end_date >= $2::date');
    conditions.push('cm.end_date <= ($2::date + $3::int)');
  }
  if (filter.status === 'expired') conditions.push('cm.end_date < $2::date');

  const orderBy: Record<MemberSort, string> = {
    score_desc: 'coalesce(sc.score, 0) desc, u.full_name asc, u.id asc',
    score_asc: 'coalesce(sc.score, 0) asc, u.full_name asc, u.id asc',
    newest: 'mp.joined_on desc, u.created_at desc, u.id desc',
    oldest: 'mp.joined_on asc, u.created_at asc, u.id asc',
    expiry_asc: 'cm.end_date asc nulls last, u.full_name asc',
    name_asc: 'u.full_name asc, u.id asc',
  };

  return {
    fromSql: MEMBER_SELECT,
    whereSql: conditions.join(' and '),
    orderSql: orderBy[filter.sort ?? 'name_asc'],
    params,
    countWhereSql: conditions.join(' and '),
    countFromSql: `
      from users u
      join member_profiles mp on mp.user_id = u.id
      left join v_current_membership cm on cm.member_id = u.id
      left join scored sc on sc.member_id = u.id
    `,
  };
}

export async function listMembers(
  context: MemberQueryContext,
  filter: MemberListFilter,
  page: { limit: number; offset: number },
): Promise<{ items: MemberRow[]; total: number }> {
  const built = buildMemberFilter(context, filter);

  const countRow = await queryOne<{ total: number }>(
    `${scoredCte(context.scoreFrom, context.scoreTo)}
     select count(*)::int as total ${built.countFromSql} where ${built.countWhereSql}`,
    built.params,
  );

  const params = [...built.params, page.limit, page.offset];
  const items = await query<MemberRow>(
    `${scoredCte(context.scoreFrom, context.scoreTo)}
     ${built.fromSql}
     where ${built.whereSql}
     order by ${built.orderSql}
     limit $${params.length - 1} offset $${params.length}`,
    params,
  );

  return { items, total: countRow?.total ?? 0 };
}

export async function getMember(gymId: string, memberId: string): Promise<MemberRow | null> {
  return queryOne<MemberRow>(
    `with scored as (
       select a.member_id, count(*)::int as score
       from attendance a
       where a.gym_id = $1 and a.status = 'active'
         and a.attendance_date between date_trunc('month', $2::date)::date and $2::date
       group by a.member_id
     )
     ${MEMBER_SELECT}
     where u.gym_id = $1 and u.id = $2 and u.role = 'member'`,
    [gymId, memberId],
  );
}

export interface MemberDetailExtras {
  lifetimeScore: number;
  monthlyScore: number;
  weeklyScore: number;
  lastAttendanceAt: Date | null;
  totalAttendanceDays: number;
  activeSessionExpiresAt: Date | null;
}

export async function getMemberExtras(
  gymId: string,
  memberId: string,
  range: { today: string; monthStart: string; weekStart: string },
): Promise<MemberDetailExtras> {
  const row = await queryOne<{
    lifetime_score: number;
    monthly_score: number;
    weekly_score: number;
    last_attendance_at: Date | null;
    total_days: number;
    session_expires_at: Date | null;
  }>(
    `select
       (select count(*)::int from attendance a
          where a.gym_id = $1 and a.member_id = $2 and a.status = 'active') as lifetime_score,
       (select count(*)::int from attendance a
          where a.gym_id = $1 and a.member_id = $2 and a.status = 'active'
            and a.attendance_date between $3::date and $4::date) as monthly_score,
       (select count(*)::int from attendance a
          where a.gym_id = $1 and a.member_id = $2 and a.status = 'active'
            and a.attendance_date between $5::date and $4::date) as weekly_score,
       (select max(a.marked_at) from attendance a
          where a.gym_id = $1 and a.member_id = $2 and a.status = 'active') as last_attendance_at,
       (select count(*)::int from attendance a
          where a.gym_id = $1 and a.member_id = $2 and a.status = 'active') as total_days,
       (select s.expires_at from gym_sessions s
          where s.member_id = $2 and s.status = 'active' and s.expires_at > now()
          order by s.expires_at desc limit 1) as session_expires_at`,
    [gymId, memberId, range.monthStart, range.today, range.weekStart],
  );

  return {
    lifetimeScore: row?.lifetime_score ?? 0,
    monthlyScore: row?.monthly_score ?? 0,
    weeklyScore: row?.weekly_score ?? 0,
    lastAttendanceAt: row?.last_attendance_at ?? null,
    totalAttendanceDays: row?.total_days ?? 0,
    activeSessionExpiresAt: row?.session_expires_at ?? null,
  };
}

export async function createMember(
  input: {
    gymId: string;
    email: string;
    fullName: string;
    phone?: string | null;
    passwordHash?: string | null;
    status: UserStatus;
    gender?: string | null;
    address?: string | null;
    shiftId?: string | null;
    joinedOn: string;
    emergencyContactName?: string | null;
    emergencyContactPhone?: string | null;
    dateOfBirth?: string | null;
    notes?: string | null;
  },
  client?: PoolClient,
): Promise<string> {
  const run = async (executor: PoolClient): Promise<string> => {
    const inserted = await executor.query<{ id: string }>(
      `insert into users (gym_id, email, password_hash, role, status, full_name, phone, permissions)
       values ($1, $2, $3, 'member', $4, $5, $6, '{}')
       returning id`,
      [input.gymId, input.email, input.passwordHash ?? null, input.status, input.fullName, input.phone ?? null],
    );
    const userId = inserted.rows[0]?.id;
    if (!userId) throw new Error('Failed to create member');
    await executor.query(
      `insert into member_profiles
         (user_id, gym_id, gender, address, shift_id, joined_on, emergency_contact_name,
          emergency_contact_phone, date_of_birth, notes)
       values ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)`,
      [
        userId,
        input.gymId,
        input.gender ?? null,
        input.address ?? null,
        input.shiftId ?? null,
        input.joinedOn,
        input.emergencyContactName ?? null,
        input.emergencyContactPhone ?? null,
        input.dateOfBirth ?? null,
        input.notes ?? null,
      ],
    );
    return userId;
  };

  if (client) return run(client);
  return withTransaction((transaction) => run(transaction));
}

export async function updateMember(
  gymId: string,
  memberId: string,
  patch: {
    fullName?: string;
    phone?: string | null;
    email?: string;
    gender?: string | null;
    address?: string | null;
    shiftId?: string | null;
    emergencyContactName?: string | null;
    emergencyContactPhone?: string | null;
    dateOfBirth?: string | null;
    notes?: string | null;
  },
): Promise<void> {
  await withTransaction(async (client) => {
    if (patch.fullName !== undefined || patch.phone !== undefined || patch.email !== undefined) {
      await client.query(
        `update users
            set full_name = coalesce($3, full_name),
                phone = coalesce($4, phone),
                email = coalesce($5, email)
          where gym_id = $1 and id = $2`,
        [gymId, memberId, patch.fullName ?? null, patch.phone ?? null, patch.email ?? null],
      );
    }
    const profileFields: Array<[string, unknown]> = [
      ['gender', patch.gender],
      ['address', patch.address],
      ['shift_id', patch.shiftId],
      ['emergency_contact_name', patch.emergencyContactName],
      ['emergency_contact_phone', patch.emergencyContactPhone],
      ['date_of_birth', patch.dateOfBirth],
      ['notes', patch.notes],
    ];
    const assignments: string[] = [];
    const params: unknown[] = [gymId, memberId];
    for (const [column, value] of profileFields) {
      if (value === undefined) continue;
      params.push(value);
      assignments.push(`${column} = $${params.length}`);
    }
    if (assignments.length > 0) {
      await client.query(
        `update member_profiles set ${assignments.join(', ')} where gym_id = $1 and user_id = $2`,
        params,
      );
    }
  });
}

export async function setMemberArchived(
  gymId: string,
  memberId: string,
  archived: boolean,
  actorId: string,
): Promise<boolean> {
  const result = await query(
    `update member_profiles
        set archived_at = case when $3 then now() else null end,
            archived_by = case when $3 then $4::uuid else null end
      where gym_id = $1 and user_id = $2
      returning user_id`,
    [gymId, memberId, archived, actorId],
  );
  return result.length > 0;
}

export async function countMembers(gymId: string, asOf?: string, timezone = 'UTC'): Promise<number> {
  const row = asOf
    ? await queryOne<{ count: number }>(
        `select count(*)::int as count
           from users u
           join member_profiles mp on mp.user_id = u.id
          where u.gym_id = $1
            and u.role = 'member'
            and u.status <> 'inactive'
            and mp.joined_on <= $2::date
            and (mp.archived_at is null or (mp.archived_at at time zone $3)::date > $2::date)`,
        [gymId, asOf, timezone],
      )
    : await queryOne<{ count: number }>(
        `select count(*)::int as count
           from users u
           join member_profiles mp on mp.user_id = u.id
          where u.gym_id = $1 and u.role = 'member' and u.status = 'active' and mp.archived_at is null`,
        [gymId],
      );
  return row?.count ?? 0;
}

export async function findMemberIdsEligibility(
  gymId: string,
  memberIds: string[],
  date: string,
): Promise<Set<string>> {
  if (memberIds.length === 0) return new Set();
  const rows = await query<{ id: string }>(
    `select u.id
       from users u
       join member_profiles mp on mp.user_id = u.id
      where u.gym_id = $1
        and u.id = any($2::uuid[])
        and u.role = 'member'
        and u.status = 'active'
        and mp.archived_at is null
        and exists (
          select 1 from memberships m
           where m.member_id = u.id
             and m.status in ('active', 'superseded')
             and m.start_date <= $3::date
             and m.end_date >= $3::date
        )`,
    [gymId, memberIds, date],
  );
  return new Set(rows.map((row) => row.id));
}

// ─────────────────────────────────────────────────────────────────────────────
// Admin queries
// ─────────────────────────────────────────────────────────────────────────────

export interface AdminRow {
  id: string;
  email: string;
  full_name: string;
  phone: string | null;
  role: Role;
  status: UserStatus;
  permissions: Permission[];
  last_login_at: Date | null;
  created_at: Date;
  avatar_object_key: string | null;
  avatar_version: number;
}

export async function listAdmins(gymId: string): Promise<AdminRow[]> {
  return query<AdminRow>(
    `select id, email::text as email, full_name, phone, role, status, permissions,
            last_login_at, created_at, avatar_object_key, avatar_version
       from users
      where gym_id = $1 and role in ('admin', 'super_admin')
      order by role asc, full_name asc`,
    [gymId],
  );
}

export async function getAdmin(gymId: string, adminId: string): Promise<AdminRow | null> {
  return queryOne<AdminRow>(
    `select id, email::text as email, full_name, phone, role, status, permissions,
            last_login_at, created_at, avatar_object_key, avatar_version
       from users
      where gym_id = $1 and id = $2 and role in ('admin', 'super_admin')`,
    [gymId, adminId],
  );
}

export async function createAdmin(
  input: {
    gymId: string;
    email: string;
    fullName: string;
    phone?: string | null;
    passwordHash?: string | null;
    status: UserStatus;
    permissions?: Permission[];
  },
  client?: PoolClient,
): Promise<string> {
  const sql = `insert into users (gym_id, email, password_hash, role, status, full_name, phone, permissions)
               values ($1, $2, $3, 'admin', $4, $5, $6, $7)
               returning id`;
  const params = [
    input.gymId,
    input.email,
    input.passwordHash ?? null,
    input.status,
    input.fullName,
    input.phone ?? null,
    input.permissions ?? DEFAULT_ADMIN_PERMISSIONS,
  ];
  if (client) {
    const result = await client.query<{ id: string }>(sql, params);
    if (!result.rows[0]) throw new Error('Failed to create admin');
    return result.rows[0].id;
  }
  const created = await queryOne<{ id: string }>(sql, params);
  if (!created) throw new Error('Failed to create admin');
  return created.id;
}

export async function countActiveSuperAdmins(gymId: string): Promise<number> {
  const row = await queryOne<{ count: number }>(
    `select count(*)::int as count from users
      where gym_id = $1 and role = 'super_admin' and status = 'active'`,
    [gymId],
  );
  return row?.count ?? 0;
}

export async function emailExists(email: string): Promise<boolean> {
  const row = await queryOne<{ id: string }>('select id from users where email = $1', [email]);
  return Boolean(row);
}

export { isUniqueViolation };
