import { randomInt } from 'node:crypto';
import { env } from '../config/env';
import { AUDIT_ACTIONS } from '../config/constants';
import { recordAudit } from '../repositories/auditRepo';
import {
  acceptInvitation,
  consumePasswordResetToken,
  createInvitation,
  createPasswordResetToken,
  findInvitationByHash,
  findLatestPendingInvitation,
  findPasswordResetToken,
  findRefreshTokenByHash,
  revokeAllRefreshTokens,
  revokePendingInvitations,
  revokeRefreshToken,
  saveRefreshToken,
} from '../repositories/tokenRepo';
import { findUserByEmail, findUserById, setPasswordHash, touchLastLogin, type UserRow } from '../repositories/userRepo';
import { getGym, listShifts, type GymRow } from '../repositories/gymRepo';
import { addMinutes } from '../utils/datetime';
import { AppError } from '../utils/errors';
import { logger } from '../utils/logger';
import { hashPassword, verifyPassword } from '../utils/password';
import { randomToken, sha256 } from '../utils/crypto';
import { sendInvitationEmail, sendPasswordResetEmail } from './mailer';
import { signAccessToken } from '../middleware/auth';
import { toGymDto, toShiftDto, toUserDto, type GymDto, type ShiftDto, type UserDto } from './dto';

export interface RequestMeta {
  ip?: string | null;
  userAgent?: string | null;
}

export interface SessionDto {
  user: UserDto;
  gym: GymDto;
  shifts: ShiftDto[];
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresIn: number;
  tokenType: 'Bearer';
}

const DUMMY_HASH = '$2b$12$C6UzMDM.H6dfI/f/IKcEe.7g4C6ZcVpUZz5s8R4o9D1uJvF3J4J0e';

async function buildSession(user: UserRow, gym: GymRow, meta: RequestMeta): Promise<SessionDto> {
  const { token: accessToken, expiresIn } = signAccessToken({
    sub: user.id,
    gymId: user.gym_id,
    role: user.role,
    email: user.email,
  });

  const refreshToken = randomToken(48);
  await saveRefreshToken({
    userId: user.id,
    tokenHash: sha256(refreshToken),
    expiresAt: addMinutes(new Date(), Math.floor(env.REFRESH_TOKEN_TTL_SECONDS / 60)),
    userAgent: meta.userAgent ?? null,
    ip: meta.ip ?? null,
  });

  const shifts = await listShifts(gym.id);

  return {
    user: toUserDto(user),
    gym: toGymDto(gym),
    shifts: shifts.map(toShiftDto),
    accessToken,
    refreshToken,
    accessTokenExpiresIn: expiresIn,
    tokenType: 'Bearer',
  };
}

export async function login(input: { email: string; password: string }, meta: RequestMeta): Promise<SessionDto> {
  const user = await findUserByEmail(input.email.trim());
  const passwordOk = await verifyPassword(input.password, user?.password_hash ?? DUMMY_HASH);

  if (!user || !passwordOk) {
    throw AppError.invalidCredentials();
  }
  if (user.status === 'inactive') {
    throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'This account has been deactivated. Ask an administrator for help.');
  }
  if (user.status === 'invited' || !user.password_hash) {
    throw new AppError(403, 'AUTH_ACCOUNT_INVITED', 'Activate your account using the invitation you received.');
  }

  const gym = await getGym(user.gym_id);
  if (!gym) throw AppError.forbidden('This account is not linked to a gym.');

  await touchLastLogin(user.id);
  await recordAudit({
    gymId: user.gym_id,
    actorUserId: user.id,
    actorName: user.full_name,
    actorRole: user.role,
    action: AUDIT_ACTIONS.LOGIN,
    entityType: 'user',
    entityId: user.id,
    metadata: { role: user.role },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });

  return buildSession({ ...user, last_login_at: new Date() }, gym, meta);
}

export async function refreshSession(refreshToken: string, meta: RequestMeta): Promise<SessionDto> {
  const tokenHash = sha256(refreshToken);
  const stored = await findRefreshTokenByHash(tokenHash);
  if (!stored || stored.revoked_at || stored.expires_at.getTime() <= Date.now()) {
    throw new AppError(401, 'AUTH_TOKEN_INVALID', 'Your session has expired. Please sign in again.');
  }

  const user = await findUserById(stored.user_id);
  if (!user) throw new AppError(401, 'AUTH_TOKEN_INVALID', 'The session is no longer valid.');
  if (user.status !== 'active') {
    throw new AppError(403, 'AUTH_ACCOUNT_INACTIVE', 'This account is not active.');
  }
  const gym = await getGym(user.gym_id);
  if (!gym) throw AppError.forbidden('This account is not linked to a gym.');

  const next = await buildSession(user, gym, meta);
  // Rotate: the presented refresh token is single-use.
  await revokeRefreshToken(stored.id);
  return next;
}

export async function logout(refreshToken: string | undefined, meta: RequestMeta, userId?: string): Promise<void> {
  if (refreshToken) {
    const stored = await findRefreshTokenByHash(sha256(refreshToken));
    if (stored) {
      await revokeRefreshToken(stored.id);
      if (userId) {
        const user = await findUserById(stored.user_id);
        if (user) {
          await recordAudit({
            gymId: user.gym_id,
            actorUserId: user.id,
            actorName: user.full_name,
            actorRole: user.role,
            action: AUDIT_ACTIONS.LOGOUT,
            entityType: 'user',
            entityId: user.id,
            ip: meta.ip ?? null,
            userAgent: meta.userAgent ?? null,
          });
        }
      }
    }
  }
}

export async function getSessionProfile(userId: string): Promise<{
  user: UserDto;
  gym: GymDto;
  shifts: ShiftDto[];
}> {
  const user = await findUserById(userId);
  if (!user) throw AppError.unauthorized();
  const gym = await getGym(user.gym_id);
  if (!gym) throw AppError.forbidden('This account is not linked to a gym.');
  return { user: toUserDto(user), gym: toGymDto(gym), shifts: (await listShifts(gym.id)).map(toShiftDto) };
}

// ── Password reset ──────────────────────────────────────────────────────────

const CODE_ALPHABET = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';

function generateCode(length = 8): string {
  let code = '';
  for (let index = 0; index < length; index += 1) {
    code += CODE_ALPHABET[randomInt(0, CODE_ALPHABET.length)];
  }
  return code;
}

export async function requestPasswordReset(email: string): Promise<{ devToken?: string }> {
  const user = await findUserByEmail(email.trim());
  // Always behave the same, whether or not the address exists.
  if (!user || user.status === 'inactive') return {};

  const code = generateCode();
  await createPasswordResetToken({
    userId: user.id,
    tokenHash: sha256(code),
    expiresAt: addMinutes(new Date(), 30),
  });
  const result = await sendPasswordResetEmail({ to: user.email, fullName: user.full_name, token: code });
  return result.devToken ? { devToken: result.devToken } : {};
}

export async function resetPassword(input: {
  token: string;
  newPassword: string;
  meta: RequestMeta;
}): Promise<void> {
  const stored = await findPasswordResetToken(sha256(input.token.trim().toUpperCase()));
  if (!stored || stored.used_at || stored.expires_at.getTime() <= Date.now()) {
    throw AppError.validation({ token: 'This reset code is invalid or has expired.' });
  }
  const user = await findUserById(stored.user_id);
  if (!user) throw AppError.validation({ token: 'This reset code is invalid or has expired.' });

  await setPasswordHash(user.id, await hashPassword(input.newPassword));
  await consumePasswordResetToken(stored.id);
  await revokeAllRefreshTokens(user.id);
  await recordAudit({
    gymId: user.gym_id,
    actorUserId: user.id,
    actorName: user.full_name,
    actorRole: user.role,
    action: AUDIT_ACTIONS.PASSWORD_RESET,
    entityType: 'user',
    entityId: user.id,
    ip: input.meta.ip ?? null,
    userAgent: input.meta.userAgent ?? null,
  });
}

// ── Invitations ─────────────────────────────────────────────────────────────

export interface InvitationPreview {
  email: string;
  fullName: string;
  role: 'admin' | 'member';
  gymName: string;
  expiresAt: string;
}

export async function previewInvitation(token: string): Promise<InvitationPreview> {
  const invitation = await findInvitationByHash(sha256(token.trim()));
  if (!invitation) throw AppError.notFound('Invitation');
  if (invitation.accepted_at) throw AppError.conflict('CONFLICT', 'This invitation has already been used.');
  if (invitation.revoked_at) throw AppError.conflict('CONFLICT', 'This invitation was revoked.');
  if (invitation.expires_at.getTime() <= Date.now()) {
    throw AppError.conflict('CONFLICT', 'This invitation has expired. Ask an administrator for a new one.');
  }
  const gym = await getGym(invitation.gym_id);
  return {
    email: invitation.email,
    fullName: invitation.full_name,
    role: invitation.role,
    gymName: gym?.name ?? 'the gym',
    expiresAt: invitation.expires_at.toISOString(),
  };
}

export async function acceptInvitationAndStartSession(input: {
  token: string;
  password: string;
  fullName?: string;
  meta: RequestMeta;
}): Promise<SessionDto> {
  const preview = await previewInvitation(input.token);
  const invitation = await findInvitationByHash(sha256(input.token.trim()));
  if (!invitation || !invitation.member_id) {
    throw AppError.notFound('Invitation');
  }
  const user = await findUserById(invitation.member_id);
  if (!user) throw AppError.notFound('Invited account');
  const gym = await getGym(user.gym_id);
  if (!gym) throw AppError.forbidden('This invitation is not linked to a gym.');

  await setPasswordHash(user.id, await hashPassword(input.password));
  await acceptInvitation(invitation.id);
  await recordAudit({
    gymId: user.gym_id,
    actorUserId: user.id,
    actorName: user.full_name,
    actorRole: user.role,
    action: AUDIT_ACTIONS.INVITATION_ACCEPTED,
    entityType: 'user',
    entityId: user.id,
    metadata: { email: preview.email, role: preview.role },
    ip: input.meta.ip ?? null,
    userAgent: input.meta.userAgent ?? null,
  });

  const fresh = await findUserById(user.id);
  if (!fresh) throw AppError.internal();
  return buildSession(fresh, gym, input.meta);
}

/**
 * Creates (or refreshes) an invitation for an invited account and returns the
 * plaintext token so the caller can email it.
 */
export async function issueInvitation(input: {
  gymId: string;
  gymName: string;
  email: string;
  fullName: string;
  role: 'admin' | 'member';
  userId: string;
  invitedBy: string;
  daysValid?: number;
}): Promise<{ token?: string; delivered: boolean }> {
  await revokePendingInvitations(input.gymId, input.email);
  const token = randomToken(32);
  await createInvitation({
    gymId: input.gymId,
    email: input.email,
    fullName: input.fullName,
    role: input.role,
    tokenHash: sha256(token),
    invitedBy: input.invitedBy,
    memberId: input.userId,
    expiresAt: addMinutes(new Date(), (input.daysValid ?? 7) * 24 * 60),
  });

  try {
    const result = await sendInvitationEmail({
      to: input.email,
      fullName: input.fullName,
      gymName: input.gymName,
      role: input.role,
      token,
    });
    return { token: result.devToken ?? (env.isProduction ? undefined : token), delivered: result.delivered };
  } catch (error) {
    logger.error('Invitation email failed', { email: input.email, message: (error as Error).message });
    return { token, delivered: false };
  }
}

export async function pendingInvitationFor(gymId: string, email: string) {
  return findLatestPendingInvitation(gymId, email);
}
