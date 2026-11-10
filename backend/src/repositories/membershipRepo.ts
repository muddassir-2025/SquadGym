import type { PoolClient } from 'pg';
import { query, queryOne, withTransaction } from '../database/pool';

export interface MembershipRow {
  id: string;
  gym_id: string;
  member_id: string;
  plan_id: string;
  start_date: string;
  end_date: string;
  status: 'active' | 'superseded' | 'reversed' | 'cancelled';
  created_by: string | null;
  created_at: Date;
  updated_at: Date;
}

export interface MembershipWithPlanRow extends MembershipRow {
  plan_name: string;
  duration_days: number;
  price_cents: number;
  currency: string;
}

export interface RenewalRow {
  id: string;
  gym_id: string;
  member_id: string;
  previous_membership_id: string;
  new_membership_id: string;
  previous_plan_id: string | null;
  new_plan_id: string | null;
  previous_start_date: string;
  previous_end_date: string;
  new_start_date: string;
  new_end_date: string;
  status: 'active' | 'reversed';
  performed_by: string | null;
  performed_at: Date;
  undo_expires_at: Date;
  reversed_at: Date | null;
  reversed_by: string | null;
  reason: string | null;
  metadata: Record<string, unknown>;
}

const CURRENT_MEMBERSHIP_SQL = `
  select cm.*, p.name as plan_name, p.duration_days, p.price_cents, p.currency
    from v_current_membership cm
    join membership_plans p on p.id = cm.plan_id`;

export async function getCurrentMembership(memberId: string): Promise<MembershipWithPlanRow | null> {
  return queryOne<MembershipWithPlanRow>(`${CURRENT_MEMBERSHIP_SQL} where cm.member_id = $1`, [memberId]);
}

/** The term covering a specific gym-local date (used for attendance eligibility). */
export async function getMembershipCoveringDate(
  memberId: string,
  date: string,
): Promise<MembershipWithPlanRow | null> {
  return queryOne<MembershipWithPlanRow>(
    `${CURRENT_MEMBERSHIP_SQL}
      where cm.member_id = $1
        and cm.start_date <= $2::date
        and cm.end_date >= $2::date
      order by cm.end_date desc
      limit 1`,
    [memberId, date],
  );
}

export async function getMembershipById(id: string): Promise<MembershipRow | null> {
  return queryOne<MembershipRow>('select * from memberships where id = $1', [id]);
}

export async function listMembershipHistory(memberId: string): Promise<MembershipWithPlanRow[]> {
  return query<MembershipWithPlanRow>(
    `select m.*, p.name as plan_name, p.duration_days, p.price_cents, p.currency
       from memberships m
       join membership_plans p on p.id = m.plan_id
      where m.member_id = $1
      order by m.start_date desc, m.created_at desc`,
    [memberId],
  );
}

export async function createMembership(
  input: {
    gymId: string;
    memberId: string;
    planId: string;
    startDate: string;
    endDate: string;
    status?: MembershipRow['status'];
    createdBy: string | null;
  },
  client?: PoolClient,
): Promise<MembershipRow> {
  const sql = `insert into memberships (gym_id, member_id, plan_id, start_date, end_date, status, created_by)
               values ($1, $2, $3, $4, $5, $6, $7)
               returning *`;
  const params = [
    input.gymId,
    input.memberId,
    input.planId,
    input.startDate,
    input.endDate,
    input.status ?? 'active',
    input.createdBy,
  ];
  const row = client
    ? (await client.query<MembershipRow>(sql, params)).rows[0]
    : await queryOne<MembershipRow>(sql, params);
  if (!row) throw new Error('Failed to create membership');
  return row;
}

export async function setMembershipStatus(
  id: string,
  status: MembershipRow['status'],
  client?: PoolClient,
): Promise<void> {
  const sql = 'update memberships set status = $2 where id = $1';
  if (client) await client.query(sql, [id, status]);
  else await query(sql, [id, status]);
}

export async function createRenewal(
  input: {
    gymId: string;
    memberId: string;
    previousMembershipId: string;
    newMembershipId: string;
    previousPlanId: string | null;
    newPlanId: string | null;
    previousStartDate: string;
    previousEndDate: string;
    newStartDate: string;
    newEndDate: string;
    performedBy: string;
    undoExpiresAt: Date;
    reason?: string | null;
    metadata?: Record<string, unknown>;
  },
  client?: PoolClient,
): Promise<RenewalRow> {
  const sql = `insert into membership_renewals (
                 gym_id, member_id, previous_membership_id, new_membership_id,
                 previous_plan_id, new_plan_id, previous_start_date, previous_end_date,
                 new_start_date, new_end_date, performed_by, undo_expires_at, reason, metadata
               ) values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
               returning *`;
  const params = [
    input.gymId,
    input.memberId,
    input.previousMembershipId,
    input.newMembershipId,
    input.previousPlanId,
    input.newPlanId,
    input.previousStartDate,
    input.previousEndDate,
    input.newStartDate,
    input.newEndDate,
    input.performedBy,
    input.undoExpiresAt,
    input.reason ?? null,
    JSON.stringify(input.metadata ?? {}),
  ];
  const row = client
    ? (await client.query<RenewalRow>(sql, params)).rows[0]
    : await queryOne<RenewalRow>(sql, params);
  if (!row) throw new Error('Failed to record renewal');
  return row;
}

export async function getRenewal(id: string): Promise<RenewalRow | null> {
  return queryOne<RenewalRow>('select * from membership_renewals where id = $1', [id]);
}

/** Latest renewals for a member, newest first. */
export async function listRenewals(memberId: string, limit = 20): Promise<RenewalRow[]> {
  return query<RenewalRow>(
    `select * from membership_renewals where member_id = $1 order by performed_at desc limit $2`,
    [memberId, limit],
  );
}

export async function markRenewalReversed(
  id: string,
  actorId: string,
  reason: string | null,
  client?: PoolClient,
): Promise<RenewalRow | null> {
  const sql = `update membership_renewals
                  set status = 'reversed', reversed_at = now(), reversed_by = $2, reason = coalesce($3, reason)
                where id = $1 and status = 'active'
                returning *`;
  if (client) {
    const result = await client.query<RenewalRow>(sql, [id, actorId, reason]);
    return result.rows[0] ?? null;
  }
  return queryOne<RenewalRow>(sql, [id, actorId, reason]);
}

/**
 * True when a renewal is still the newest membership change for the member.
 * Prevents undoing an old renewal after newer operations happened.
 */
export async function isLatestRenewal(renewal: RenewalRow): Promise<boolean> {
  const row = await queryOne<{ newer: number }>(
    `select count(*)::int as newer
       from memberships m
      where m.member_id = $1
        and m.created_at > (select created_at from memberships where id = $2)`,
    [renewal.member_id, renewal.new_membership_id],
  );
  return (row?.newer ?? 0) === 0;
}

/** Convenience: perform the whole renewal write inside one transaction. */
export async function renewMembershipTransaction<T>(
  work: (client: PoolClient) => Promise<T>,
): Promise<T> {
  return withTransaction(work, { isolationLevel: 'SERIALIZABLE' });
}
