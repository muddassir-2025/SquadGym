import type { PoolClient } from 'pg';
import { query, queryOne } from '../database/pool';
import type { Role } from '../config/constants';

export interface AuditEntry {
  gymId: string;
  actorUserId: string | null;
  actorName: string | null;
  actorRole: Role | null;
  action: string;
  entityType?: string | null;
  entityId?: string | null;
  metadata?: Record<string, unknown>;
  ip?: string | null;
  userAgent?: string | null;
}

/**
 * Append-only audit trail. Called for every state-changing operation (spec §4.C,
 * §5, §7). There is deliberately no update/delete helper in this module.
 */
export async function recordAudit(entry: AuditEntry, client?: PoolClient): Promise<void> {
  const sql = `insert into audit_logs
                 (gym_id, actor_user_id, actor_name, actor_role, action,
                  entity_type, entity_id, metadata, ip, user_agent)
               values ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`;
  const params = [
    entry.gymId,
    entry.actorUserId,
    entry.actorName,
    entry.actorRole,
    entry.action,
    entry.entityType ?? null,
    entry.entityId ?? null,
    JSON.stringify(entry.metadata ?? {}),
    entry.ip ?? null,
    entry.userAgent ?? null,
  ];
  if (client) await client.query(sql, params);
  else await query(sql, params);
}

export interface AuditRow {
  id: string;
  actor_user_id: string | null;
  actor_name: string | null;
  actor_role: string | null;
  action: string;
  entity_type: string | null;
  entity_id: string | null;
  metadata: Record<string, unknown>;
  created_at: Date;
}

export interface AuditFilter {
  action?: string;
  actorId?: string;
  entityType?: string;
  from?: string;
  to?: string;
  search?: string;
}

export async function listAuditLogs(
  gymId: string,
  filter: AuditFilter,
  page: { limit: number; offset: number },
): Promise<{ items: AuditRow[]; total: number }> {
  const params: unknown[] = [gymId];
  const conditions: string[] = ['gym_id = $1'];
  const arg = (value: unknown): string => {
    params.push(value);
    return `$${params.length}`;
  };

  if (filter.action) conditions.push(`action = ${arg(filter.action)}`);
  if (filter.actorId) conditions.push(`actor_user_id = ${arg(filter.actorId)}::uuid`);
  if (filter.entityType) conditions.push(`entity_type = ${arg(filter.entityType)}`);
  if (filter.from) conditions.push(`created_at >= ${arg(filter.from)}::date`);
  if (filter.to) conditions.push(`created_at < (${arg(filter.to)}::date + 1)`);
  if (filter.search) {
    const pattern = arg(`%${filter.search}%`);
    conditions.push(`(action ilike ${pattern} or coalesce(actor_name, '') ilike ${pattern})`);
  }

  const whereSql = conditions.join(' and ');
  const countRow = await queryOne<{ total: number }>(
    `select count(*)::int as total from audit_logs where ${whereSql}`,
    params,
  );

  const listParams = [...params, page.limit, page.offset];
  const items = await query<AuditRow>(
    `select id::text as id, actor_user_id, actor_name, actor_role, action, entity_type, entity_id,
            metadata, created_at
       from audit_logs
      where ${whereSql}
      order by created_at desc, id desc
      limit $${listParams.length - 1} offset $${listParams.length}`,
    listParams,
  );

  return { items, total: countRow?.total ?? 0 };
}
