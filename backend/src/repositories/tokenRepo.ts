import { query, queryOne } from '../database/pool';

/** Refresh tokens, password resets and invitations — all stored hashed. */

export interface RefreshTokenRow {
  id: string;
  user_id: string;
  token_hash: string;
  expires_at: Date;
  revoked_at: Date | null;
  replaced_by_id: string | null;
  created_at: Date;
}

export async function saveRefreshToken(input: {
  userId: string;
  tokenHash: string;
  expiresAt: Date;
  userAgent?: string | null;
  ip?: string | null;
}): Promise<string> {
  const row = await queryOne<{ id: string }>(
    `insert into refresh_tokens (user_id, token_hash, expires_at, user_agent, ip)
     values ($1, $2, $3, $4, $5) returning id`,
    [input.userId, input.tokenHash, input.expiresAt, input.userAgent ?? null, input.ip ?? null],
  );
  if (!row) throw new Error('Failed to persist refresh token');
  return row.id;
}

export async function findRefreshTokenByHash(tokenHash: string): Promise<RefreshTokenRow | null> {
  return queryOne<RefreshTokenRow>('select * from refresh_tokens where token_hash = $1', [tokenHash]);
}

export async function revokeRefreshToken(id: string, replacedById?: string | null): Promise<void> {
  await query(
    `update refresh_tokens set revoked_at = now(), replaced_by_id = coalesce($2, replaced_by_id)
      where id = $1 and revoked_at is null`,
    [id, replacedById ?? null],
  );
}

export async function revokeAllRefreshTokens(userId: string): Promise<void> {
  await query('update refresh_tokens set revoked_at = now() where user_id = $1 and revoked_at is null', [
    userId,
  ]);
}

export async function purgeExpiredTokens(): Promise<void> {
  await query("delete from refresh_tokens where expires_at < now() - interval '7 days'");
  await query("delete from password_reset_tokens where expires_at < now() - interval '7 days'");
}

export async function createPasswordResetToken(input: {
  userId: string;
  tokenHash: string;
  expiresAt: Date;
}): Promise<void> {
  await query(
    `insert into password_reset_tokens (user_id, token_hash, expires_at) values ($1, $2, $3)`,
    [input.userId, input.tokenHash, input.expiresAt],
  );
}

export interface PasswordResetRow {
  id: string;
  user_id: string;
  expires_at: Date;
  used_at: Date | null;
}

export async function findPasswordResetToken(tokenHash: string): Promise<PasswordResetRow | null> {
  return queryOne<PasswordResetRow>(
    'select id, user_id, expires_at, used_at from password_reset_tokens where token_hash = $1',
    [tokenHash],
  );
}

export async function consumePasswordResetToken(id: string): Promise<void> {
  await query('update password_reset_tokens set used_at = now() where id = $1 and used_at is null', [id]);
}

export interface InvitationRow {
  id: string;
  gym_id: string;
  email: string;
  full_name: string;
  role: 'admin' | 'member';
  token_hash: string;
  member_id: string | null;
  expires_at: Date;
  accepted_at: Date | null;
  revoked_at: Date | null;
  created_at: Date;
}

export async function createInvitation(input: {
  gymId: string;
  email: string;
  fullName: string;
  role: 'admin' | 'member';
  tokenHash: string;
  invitedBy: string;
  memberId?: string | null;
  expiresAt: Date;
}): Promise<InvitationRow> {
  const row = await queryOne<InvitationRow>(
    `insert into invitations (gym_id, email, full_name, role, token_hash, invited_by, member_id, expires_at)
     values ($1,$2,$3,$4,$5,$6,$7,$8)
     returning *`,
    [
      input.gymId,
      input.email,
      input.fullName,
      input.role,
      input.tokenHash,
      input.invitedBy,
      input.memberId ?? null,
      input.expiresAt,
    ],
  );
  if (!row) throw new Error('Failed to create invitation');
  return row;
}

export async function findInvitationByHash(tokenHash: string): Promise<InvitationRow | null> {
  return queryOne<InvitationRow>('select * from invitations where token_hash = $1', [tokenHash]);
}

export async function acceptInvitation(id: string): Promise<void> {
  await query('update invitations set accepted_at = now() where id = $1 and accepted_at is null', [id]);
}

export async function revokePendingInvitations(gymId: string, email: string): Promise<void> {
  await query(
    `update invitations set revoked_at = now()
      where gym_id = $1 and email = $2 and accepted_at is null and revoked_at is null`,
    [gymId, email],
  );
}

export async function findLatestPendingInvitation(
  gymId: string,
  email: string,
): Promise<InvitationRow | null> {
  return queryOne<InvitationRow>(
    `select * from invitations
      where gym_id = $1 and email = $2 and accepted_at is null and revoked_at is null
      order by created_at desc limit 1`,
    [gymId, email],
  );
}
