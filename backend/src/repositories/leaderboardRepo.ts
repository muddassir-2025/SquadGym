import { query, queryOne } from '../database/pool';

/** Leaderboard is derived from attendance (one point per unique gym day). */

export interface LeaderboardMemberRow {
  id: string;
  full_name: string;
  email: string;
  avatar_object_key: string | null;
  avatar_version: number;
  score: number;
  joined_on: string;
}

/** Every active member, ranked by score for the period. Deterministic tie-break. */
export async function rankedMembers(input: {
  gymId: string;
  from: string;
  to: string;
  limit: number;
  offset: number;
}): Promise<{ items: Array<LeaderboardMemberRow & { rank: number }>; total: number }> {
  const countRow = await queryOne<{ total: number }>(
    `select count(*)::int as total
       from users u join member_profiles mp on mp.user_id = u.id
      where u.gym_id = $1 and u.role = 'member' and u.status = 'active' and mp.archived_at is null`,
    [input.gymId],
  );

  const rows = await query<LeaderboardMemberRow & { rank: number }>(
    `with scores as (
       select a.member_id, count(*)::int as score
         from attendance a
        where a.gym_id = $1 and a.status = 'active'
          and a.attendance_date between $2::date and $3::date
        group by a.member_id
     )
     select u.id, u.full_name, u.email::text as email, u.avatar_object_key, u.avatar_version,
            coalesce(s.score, 0)::int as score, mp.joined_on,
            (row_number() over (order by coalesce(s.score, 0) desc, u.full_name asc, u.id asc))::int as rank
       from users u
       join member_profiles mp on mp.user_id = u.id
       left join scores s on s.member_id = u.id
      where u.gym_id = $1 and u.role = 'member' and u.status = 'active' and mp.archived_at is null
      order by rank
      limit $4 offset $5`,
    [input.gymId, input.from, input.to, input.limit, input.offset],
  );

  return { items: rows, total: countRow?.total ?? 0 };
}

export interface MemberRankRow {
  rank: number;
  score: number;
  total: number;
}

/** Rank of a single member inside the same ordering (used on the member dashboard). */
export async function memberRank(input: {
  gymId: string;
  memberId: string;
  from: string;
  to: string;
}): Promise<MemberRankRow> {
  const row = await queryOne<{ rank: number; score: number; total: number }>(
    `with scores as (
       select a.member_id, count(*)::int as score
         from attendance a
        where a.gym_id = $1 and a.status = 'active'
          and a.attendance_date between $3::date and $4::date
        group by a.member_id
     ),
     ranked as (
       select u.id, coalesce(s.score, 0) as score,
              row_number() over (order by coalesce(s.score, 0) desc, u.full_name asc, u.id asc) as rank
         from users u
         join member_profiles mp on mp.user_id = u.id
         left join scores s on s.member_id = u.id
        where u.gym_id = $1 and u.role = 'member' and u.status = 'active' and mp.archived_at is null
     )
     select rank::int as rank, score::int as score, (select count(*)::int from ranked) as total
       from ranked where id = $2`,
    [input.gymId, input.memberId, input.from, input.to],
  );
  return { rank: row?.rank ?? 0, score: row?.score ?? 0, total: row?.total ?? 0 };
}

export async function memberPeriodScore(
  gymId: string,
  memberId: string,
  from: string,
  to: string,
): Promise<number> {
  const row = await queryOne<{ score: number }>(
    `select count(*)::int as score from attendance
      where gym_id = $1 and member_id = $2 and status = 'active'
        and attendance_date between $3::date and $4::date`,
    [gymId, memberId, from, to],
  );
  return row?.score ?? 0;
}

/** Attendance dates for several members in one query (streak calculation). */
export async function attendanceDatesByMembers(
  gymId: string,
  memberIds: string[],
  from: string,
  to: string,
): Promise<Map<string, Set<string>>> {
  if (memberIds.length === 0) return new Map();
  const rows = await query<{ member_id: string; attendance_date: string }>(
    `select member_id, attendance_date from attendance
      where gym_id = $1 and status = 'active' and member_id = any($2::uuid[])
        and attendance_date between $3::date and $4::date`,
    [gymId, memberIds, from, to],
  );
  const map = new Map<string, Set<string>>();
  for (const row of rows) {
    const set = map.get(row.member_id);
    if (set) set.add(row.attendance_date);
    else map.set(row.member_id, new Set([row.attendance_date]));
  }
  return map;
}
