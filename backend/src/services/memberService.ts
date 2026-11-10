import { AUDIT_ACTIONS, GENDERS } from '../config/constants';
import { AppError } from '../utils/errors';
import {
  addDays,
  formatShortDate,
  startOfMonth,
  startOfWeek,
  todayIn,
} from '../utils/datetime';
import type { AuthContext } from '../types/auth';
import type { RequestMeta } from './authService';
import { issueInvitation } from './authService';
import { recordAudit } from '../repositories/auditRepo';
import {
  buildMemberFilter,
  createMember as createMemberRow,
  emailExists,
  findUserById,
  getMember,
  getMemberExtras,
  listMembers as listMemberRows,
  setMemberArchived,
  updateMember as updateMemberRow,
  type MemberListFilter,
  type MemberQueryContext,
  type MemberRow,
  type MemberTab,
} from '../repositories/userRepo';
import { getPlan, listPlans, requireGym } from '../repositories/gymRepo';
import {
  createMembership,
  listMembershipHistory,
  listRenewals,
} from '../repositories/membershipRepo';
import { memberAttendanceHistory, voidActiveSessionsForMember } from '../repositories/attendanceRepo';
import { hashPassword } from '../utils/password';
import { withTransaction } from '../database/pool';
import { storage, ALLOWED_IMAGE_TYPES, memberPhotoKey } from './storage';
import {
  mediaUrl,
  toMemberDto,
  toMembershipDto,
  type MemberDto,
  type MembershipDto,
} from './dto';

export interface ListMembersResultDto {
  members: MemberDto[];
  context: { gymLocalDate: string; expiringSoonDays: number; scorePeriodStart: string; scorePeriodEnd: string };
  meta: { page: number; pageSize: number; total: number; totalPages: number; hasNext: boolean; hasPrevious: boolean };
}

export async function listMembers(
  auth: AuthContext,
  filter: MemberListFilter,
  page: { page: number; pageSize: number; offset: number },
): Promise<ListMembersResultDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const context: MemberQueryContext = {
    gymId: gym.id,
    today,
    expiringSoonDays: gym.expiring_soon_days,
    scoreFrom: startOfMonth(today),
    scoreTo: today,
  };
  const { items, total } = await listMemberRows(context, filter, {
    limit: page.pageSize,
    offset: page.offset,
  });

  return {
    members: items.map((row) => toMemberDto(row, gym, today)),
    context: {
      gymLocalDate: today,
      expiringSoonDays: gym.expiring_soon_days,
      scorePeriodStart: startOfMonth(today),
      scorePeriodEnd: today,
    },
    meta: {
      page: page.page,
      pageSize: page.pageSize,
      total,
      totalPages: Math.max(1, Math.ceil(total / page.pageSize)),
      hasNext: page.page * page.pageSize < total,
      hasPrevious: page.page > 1,
    },
  };
}

export interface MemberDetailDto {
  member: MemberDto;
  extras: {
    lifetimeScore: number;
    monthlyScore: number;
    weeklyScore: number;
    lastAttendanceAt: string | null;
    totalAttendanceDays: number;
    inGymNowUntil: string | null;
    currentStreak: number;
  };
  membershipHistory: MembershipDto[];
  attendance: Array<{
    id: string;
    date: string;
    dateLabel: string;
    status: string;
    markedAt: string;
    markedByName: string | null;
  }>;
  renewals: Array<{
    id: string;
    performedAt: string;
    newStartDate: string;
    newEndDate: string;
    status: string;
    undoExpiresAt: string;
  }>;
}

export async function getMemberDetail(auth: AuthContext, memberId: string): Promise<MemberDetailDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const row = await getMember(gym.id, memberId);
  if (!row) throw AppError.notFound('Member');

  const [extras, history, attendance, renewals] = await Promise.all([
    getMemberExtras(gym.id, memberId, {
      today,
      monthStart: startOfMonth(today),
      weekStart: startOfWeek(today),
    }),
    listMembershipHistory(memberId),
    memberAttendanceHistory(gym.id, memberId, addDays(today, -60), today),
    listRenewals(memberId, 10),
  ]);

  return {
    member: toMemberDto(row, gym, today),
    extras: {
      lifetimeScore: extras.lifetimeScore,
      monthlyScore: extras.monthlyScore,
      weeklyScore: extras.weeklyScore,
      lastAttendanceAt: extras.lastAttendanceAt ? extras.lastAttendanceAt.toISOString() : null,
      totalAttendanceDays: extras.totalAttendanceDays,
      inGymNowUntil: extras.activeSessionExpiresAt ? extras.activeSessionExpiresAt.toISOString() : null,
      currentStreak: 0,
    },
    membershipHistory: history.map((entry) => toMembershipDto(entry, gym, today)),
    attendance: attendance
      .filter((entry) => entry.status === 'active')
      .map((entry) => ({
        id: entry.id,
        date: entry.attendance_date,
        dateLabel: formatShortDate(entry.attendance_date),
        status: entry.status,
        markedAt: entry.marked_at.toISOString(),
        markedByName: entry.marked_by_name,
      })),
    renewals: renewals.map((renewal) => ({
      id: renewal.id,
      performedAt: renewal.performed_at.toISOString(),
      newStartDate: renewal.new_start_date,
      newEndDate: renewal.new_end_date,
      status: renewal.status,
      undoExpiresAt: renewal.undo_expires_at.toISOString(),
    })),
  };
}

export interface CreateMemberInput {
  fullName: string;
  email: string;
  phone?: string | null;
  gender?: (typeof GENDERS)[number] | null;
  address?: string | null;
  planId: string;
  startDate?: string;
  shiftId?: string | null;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
  dateOfBirth?: string | null;
  notes?: string | null;
  initialPassword?: string | null;
}

export interface CreateMemberResultDto {
  memberId: string;
  invitation: { sent: boolean; token?: string } | null;
  accountStatus: 'active' | 'invited';
}

export async function createMember(
  auth: AuthContext,
  input: CreateMemberInput,
): Promise<CreateMemberResultDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const email = input.email.trim().toLowerCase();

  if (await emailExists(email)) {
    throw AppError.validation({ email: 'An account with this email already exists.' });
  }
  const plan = await getPlan(gym.id, input.planId);
  if (!plan || !plan.is_active) {
    throw AppError.validation({ planId: 'Choose an active membership plan.' });
  }

  const startDate = input.startDate ?? today;
  if (startDate > addDays(today, 365)) {
    throw AppError.validation({ startDate: 'The start date is too far in the future.' });
  }
  const endDate = addDays(startDate, plan.duration_days - 1);
  const passwordHash = input.initialPassword ? await hashPassword(input.initialPassword) : null;
  const status = passwordHash ? 'active' : 'invited';

  const memberId = await withTransaction(async (client) => {
    const createdId = await createMemberRow(
      {
        gymId: gym.id,
        email,
        fullName: input.fullName.trim(),
        phone: input.phone ?? null,
        passwordHash,
        status,
        gender: input.gender ?? null,
        address: input.address ?? null,
        shiftId: input.shiftId ?? null,
        joinedOn: startDate,
        emergencyContactName: input.emergencyContactName ?? null,
        emergencyContactPhone: input.emergencyContactPhone ?? null,
        dateOfBirth: input.dateOfBirth ?? null,
        notes: input.notes ?? null,
      },
      client,
    );
    await createMembership(
      {
        gymId: gym.id,
        memberId: createdId,
        planId: plan.id,
        startDate,
        endDate,
        createdBy: auth.userId,
      },
      client,
    );
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.MEMBER_CREATED,
        entityType: 'member',
        entityId: createdId,
        metadata: {
          email,
          fullName: input.fullName,
          planId: plan.id,
          planName: plan.name,
          startDate,
          endDate,
          invited: status === 'invited',
        },
      },
      client,
    );
    return createdId;
  });

  let invitation: { sent: boolean; token?: string } | null = null;
  if (status === 'invited') {
    const issued = await issueInvitation({
      gymId: gym.id,
      gymName: gym.name,
      email,
      fullName: input.fullName.trim(),
      role: 'member',
      userId: memberId,
      invitedBy: auth.userId,
    });
    invitation = { sent: issued.delivered, ...(issued.token ? { token: issued.token } : {}) };
  }

  return { memberId, invitation, accountStatus: status };
}

export interface UpdateMemberInput {
  fullName?: string;
  email?: string;
  phone?: string | null;
  gender?: (typeof GENDERS)[number] | null;
  address?: string | null;
  shiftId?: string | null;
  emergencyContactName?: string | null;
  emergencyContactPhone?: string | null;
  dateOfBirth?: string | null;
  notes?: string | null;
}

export async function updateMember(
  auth: AuthContext,
  memberId: string,
  input: UpdateMemberInput,
  meta: RequestMeta,
): Promise<MemberDto> {
  const gym = await requireGym(auth.gymId);
  const existing = await getMember(gym.id, memberId);
  if (!existing) throw AppError.notFound('Member');

  if (input.email && input.email.trim().toLowerCase() !== existing.email.toLowerCase()) {
    if (await emailExists(input.email.trim().toLowerCase())) {
      throw AppError.validation({ email: 'An account with this email already exists.' });
    }
  }

  await updateMemberRow(gym.id, memberId, {
    ...input,
    email: input.email ? input.email.trim().toLowerCase() : undefined,
  });

  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.MEMBER_UPDATED,
    entityType: 'member',
    entityId: memberId,
    metadata: { changes: Object.keys(input).filter((key) => input[key as keyof UpdateMemberInput] !== undefined) },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });

  const refreshed = await getMember(gym.id, memberId);
  if (!refreshed) throw AppError.notFound('Member');
  return toMemberDto(refreshed, gym, todayIn(gym.timezone));
}

export async function archiveMember(
  auth: AuthContext,
  memberId: string,
  reason: string | null,
  meta: RequestMeta,
): Promise<void> {
  const gym = await requireGym(auth.gymId);
  const member = await findUserById(memberId);
  if (!member || member.gym_id !== gym.id || member.role !== 'member') throw AppError.notFound('Member');

  const changed = await setMemberArchived(gym.id, memberId, true, auth.userId);
  if (!changed) throw AppError.notFound('Member');

  // An archived member must not linger in "In Gym Now"; attendance history stays.
  await voidActiveSessionsForMember(memberId, auth.userId);
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.MEMBER_ARCHIVED,
    entityType: 'member',
    entityId: memberId,
    metadata: { reason, attendancePreserved: true },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
}

export async function restoreMember(
  auth: AuthContext,
  memberId: string,
  meta: RequestMeta,
): Promise<void> {
  const gym = await requireGym(auth.gymId);
  const member = await findUserById(memberId);
  if (!member || member.gym_id !== gym.id || member.role !== 'member') throw AppError.notFound('Member');

  const changed = await setMemberArchived(gym.id, memberId, false, auth.userId);
  if (!changed) throw AppError.notFound('Member');

  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.MEMBER_RESTORED,
    entityType: 'member',
    entityId: memberId,
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
}

export async function uploadMemberPhoto(
  auth: AuthContext,
  memberId: string,
  file: { buffer: Buffer; mimetype: string; size: number },
  meta: RequestMeta,
): Promise<{ photoUrl: string | null; imageVersion: number }> {
  const gym = await requireGym(auth.gymId);
  const member = await findUserById(memberId);
  if (!member || member.gym_id !== gym.id || member.role !== 'member') throw AppError.notFound('Member');

  if (!ALLOWED_IMAGE_TYPES.includes(file.mimetype)) {
    throw new AppError(415, 'UNSUPPORTED_MEDIA_TYPE', 'Upload a JPEG, PNG or WebP image.');
  }

  const key = memberPhotoKey(gym.id, memberId, file.mimetype);
  await storage().put(key, file.buffer, file.mimetype);

  const updated = await withTransaction(async (client) => {
    const result = await client.query<{ avatar_object_key: string; avatar_version: number }>(
      `update users set avatar_object_key = $2, avatar_version = avatar_version + 1
        where gym_id = $1 and id = $3
        returning avatar_object_key, avatar_version`,
      [gym.id, key, memberId],
    );
    const row = result.rows[0];
    if (!row) throw AppError.notFound('Member');
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.MEMBER_PHOTO_UPDATED,
        entityType: 'member',
        entityId: memberId,
        metadata: { objectKey: key, imageVersion: row.avatar_version },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      client,
    );
    return row;
  });

  if (member.avatar_object_key && member.avatar_object_key !== key) {
    await storage().remove(member.avatar_object_key).catch(() => undefined);
  }

  return { photoUrl: mediaUrl(updated.avatar_object_key, updated.avatar_version), imageVersion: updated.avatar_version };
}

/** Expiring / expired lists reuse the member filter (spec §4.E). */
export async function membersByTab(
  auth: AuthContext,
  tab: MemberTab,
  page: { page: number; pageSize: number; offset: number },
): Promise<ListMembersResultDto> {
  return listMembers(auth, { tab }, page);
}

export async function plansForGym(gymId: string) {
  return listPlans(gymId);
}
