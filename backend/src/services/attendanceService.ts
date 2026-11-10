import { AUDIT_ACTIONS } from '../config/constants';
import { AppError } from '../utils/errors';
import {
  addSeconds,
  formatLongDate,
  startOfMonth,
  todayIn,
} from '../utils/datetime';
import type { AuthContext } from '../types/auth';
import type { RequestMeta } from './authService';
import { recordAudit } from '../repositories/auditRepo';
import {
  attendanceSummary as summaryQuery,
  checkoutSession,
  countInGymNow,
  createSession,
  getActiveAttendance,
  getAttendanceById,
  getSessionByAttendance,
  insertAttendance,
  listActiveSessions,
  listAttendanceMembers,
  markAttendanceCorrected,
  markAttendanceUndone,
  voidSessionForAttendance,
  withTransaction,
  type AttendanceFilter,
  type AttendanceTab,
} from '../repositories/attendanceRepo';
import { getOperatingDays, requireGym } from '../repositories/gymRepo';
import { getMembershipCoveringDate } from '../repositories/membershipRepo';
import { findUserById } from '../repositories/userRepo';
import { isOpenDay } from './scoring';
import {
  mediaUrl,
  toAttendanceMemberDto,
  toUndoWindow,
  type AttendanceMemberDto,
  type UndoWindowDto,
} from './dto';
import type { GymRow } from '../repositories/gymRepo';

export async function gymToday(gymId: string): Promise<{ gym: GymRow; today: string; gymOpen: boolean }> {
  const gym = await requireGym(gymId);
  const today = todayIn(gym.timezone);
  const days = await getOperatingDays(gymId);
  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  return { gym, today, gymOpen: isOpenDay(today, openWeekdays) };
}

export interface AttendanceDashboardQuery {
  date?: string;
  tab: AttendanceTab;
  search?: string;
  shiftId?: string;
  gender?: string;
  planId?: string;
}

export interface AttendanceDashboardDto {
  date: string;
  dateLabel: string;
  timezone: string;
  gymOpen: boolean;
  inGymNow: number;
  summary: { total: number; present: number; absent: number; filtered: boolean };
  gymTotals: { total: number; present: number; absent: number };
  members: AttendanceMemberDto[];
  meta: { page: number; pageSize: number; total: number; totalPages: number; hasNext: boolean; hasPrevious: boolean };
}

export async function attendanceDashboard(
  auth: AuthContext,
  query: AttendanceDashboardQuery,
  page: { page: number; pageSize: number; offset: number },
): Promise<AttendanceDashboardDto> {
  const gym = await requireGym(auth.gymId);
  const date = query.date ?? todayIn(gym.timezone);
  const days = await getOperatingDays(gym.id);
  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  const gymOpen = isOpenDay(date, openWeekdays);
  const today = todayIn(gym.timezone);
  const monthStart = startOfMonth(today);

  const filter: AttendanceFilter = {
    tab: query.tab,
    search: query.search,
    shiftId: query.shiftId,
    gender: query.gender,
    planId: query.planId,
  };
  const context = { gymId: gym.id, date, scoreFrom: monthStart, scoreTo: today };

  const { items, total } = await listAttendanceMembers(context, filter, {
    limit: page.pageSize,
    offset: page.offset,
  });

  const filtered = Boolean(query.search || query.shiftId || query.gender || query.planId);
  const summary = await summaryQuery(context, { ...filter, tab: 'all' });
  const gymTotals = filtered
    ? await summaryQuery(context, { tab: 'all' })
    : { total: summary.total, present: summary.present, absent: summary.absent };

  const now = new Date();

  return {
    date,
    dateLabel: formatLongDate(date),
    timezone: gym.timezone,
    gymOpen,
    inGymNow: await countInGymNow(gym.id),
    summary: { ...summary, filtered },
    gymTotals,
    members: items.map((row) => toAttendanceMemberDto(row, gym, date, now)),
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

async function assertGymOpen(gym: GymRow, date: string): Promise<void> {
  const days = await getOperatingDays(gym.id);
  const openWeekdays = new Set(days.filter((day) => day.is_open).map((day) => day.day_of_week));
  if (!isOpenDay(date, openWeekdays)) {
    throw new AppError(
      409,
      'GYM_CLOSED',
      `The gym is closed on ${formatLongDate(date)}. Attendance cannot be recorded for a closed day.`,
    );
  }
}

async function loadEligibleMember(
  gym: GymRow,
  memberId: string,
  date: string,
): Promise<{ fullName: string; photoUrl: string | null; imageVersion: number }> {
  const member = await findUserById(memberId);
  if (!member || member.gym_id !== gym.id || member.role !== 'member') {
    throw AppError.notFound('Member');
  }
  if (member.status === 'inactive') {
    throw new AppError(409, 'MEMBER_NOT_ELIGIBLE', `${member.full_name}'s account is inactive.`);
  }
  const membership = await getMembershipCoveringDate(member.id, date);
  if (!membership) {
    throw new AppError(
      409,
      'MEMBER_NOT_ELIGIBLE',
      `${member.full_name} does not have a valid membership on ${formatLongDate(date)}.`,
    );
  }
  return {
    fullName: member.full_name,
    photoUrl: mediaUrl(member.avatar_object_key, member.avatar_version),
    imageVersion: member.avatar_version,
  };
}

export interface MarkPresentInput {
  memberId: string;
  date?: string;
}

export interface MarkPresentResultDto {
  attendance: {
    id: string;
    memberId: string;
    attendanceDate: string;
    markedAt: string;
    status: string;
    source: string;
  };
  member: { id: string; fullName: string; photoUrl: string | null; imageVersion: number };
  alreadyMarked: boolean;
  undo: UndoWindowDto;
  session: { id: string; checkedInAt: string; expiresAt: string; secondsRemaining: number } | null;
}

export async function markPresent(
  auth: AuthContext,
  input: MarkPresentInput,
  meta: RequestMeta,
): Promise<MarkPresentResultDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const date = input.date ?? today;

  if (date > today) {
    throw AppError.validation({ date: 'You cannot record attendance for a future date.' });
  }
  await assertGymOpen(gym, date);
  const member = await loadEligibleMember(gym, input.memberId, date);

  const now = new Date();
  const undoExpiresAt = addSeconds(now, gym.attendance_undo_seconds);
  const { attendance, created } = await insertAttendance({
    gymId: gym.id,
    memberId: input.memberId,
    date,
    markedBy: auth.userId,
    undoExpiresAt,
    source: 'manual',
  });

  let session = await getSessionByAttendance(attendance.id);
  if (created && date === today) {
    const expiresAt = addSeconds(now, gym.in_gym_window_minutes * 60);
    session =
      (await createSession({
        gymId: gym.id,
        memberId: input.memberId,
        attendanceId: attendance.id,
        checkedInAt: now,
        expiresAt,
      })) ?? session;
  }

  if (created) {
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.ATTENDANCE_MARKED,
        entityType: 'attendance',
        entityId: attendance.id,
        metadata: {
          memberId: input.memberId,
          memberName: member.fullName,
          attendanceDate: date,
          sessionId: session?.id ?? null,
          gymLocalDate: today,
        },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
    );
  }

  return {
    attendance: {
      id: attendance.id,
      memberId: attendance.member_id,
      attendanceDate: attendance.attendance_date,
      markedAt: attendance.marked_at.toISOString(),
      status: attendance.status,
      source: attendance.source,
    },
    member: { id: input.memberId, ...member },
    alreadyMarked: !created,
    undo: toUndoWindow(attendance.undo_expires_at, now),
    session: session
      ? {
          id: session.id,
          checkedInAt: session.checked_in_at.toISOString(),
          expiresAt: session.expires_at.toISOString(),
          secondsRemaining: Math.max(0, Math.floor((session.expires_at.getTime() - now.getTime()) / 1000)),
        }
      : null,
  };
}

export interface UndoAttendanceResultDto {
  attendanceId: string;
  memberId: string;
  attendanceDate: string;
  status: string;
  undoneAt: string;
}

export async function undoAttendance(
  auth: AuthContext,
  attendanceId: string,
  meta: RequestMeta,
): Promise<UndoAttendanceResultDto> {
  const gym = await requireGym(auth.gymId);
  const attendance = await getAttendanceById(attendanceId);
  if (!attendance || attendance.gym_id !== gym.id) throw AppError.notFound('Attendance record');
  if (attendance.status !== 'active') {
    throw new AppError(409, 'ALREADY_UNDONE', 'This attendance record was already reversed.');
  }

  const now = new Date();
  if (attendance.undo_expires_at.getTime() <= now.getTime()) {
    throw new AppError(
      409,
      'UNDO_WINDOW_EXPIRED',
      `The ${Math.round(gym.attendance_undo_seconds / 60)}-minute undo window has passed. Use an attendance correction instead.`,
    );
  }

  const updated = await withTransaction(async (client) => {
    const row = await markAttendanceUndone(attendanceId, auth.userId, client);
    if (!row) throw new AppError(409, 'ALREADY_UNDONE', 'This attendance record was already reversed.');
    await voidSessionForAttendance(attendanceId, auth.userId, client);
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.ATTENDANCE_UNDONE,
        entityType: 'attendance',
        entityId: attendanceId,
        metadata: {
          memberId: attendance.member_id,
          attendanceDate: attendance.attendance_date,
          markedAt: attendance.marked_at.toISOString(),
          undoneAt: now.toISOString(),
          secondsAfterMarking: Math.floor((now.getTime() - attendance.marked_at.getTime()) / 1000),
        },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      client,
    );
    return row;
  });

  return {
    attendanceId: updated.id,
    memberId: updated.member_id,
    attendanceDate: updated.attendance_date,
    status: updated.status,
    undoneAt: updated.undone_at?.toISOString() ?? now.toISOString(),
  };
}

export interface CorrectionInput {
  memberId: string;
  date: string;
  action: 'mark_present' | 'remove';
  reason: string;
}

export async function correctAttendance(
  auth: AuthContext,
  input: CorrectionInput,
  meta: RequestMeta,
): Promise<{ date: string; status: 'present' | 'absent'; attendanceId: string | null }> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);

  if (input.date > today) {
    throw AppError.validation({ date: 'You cannot correct a future date.' });
  }

  const existing = await getActiveAttendance(gym.id, input.memberId, input.date);

  if (input.action === 'mark_present') {
    if (existing) {
      throw new AppError(409, 'ALREADY_MARKED', 'This member is already marked present for that day.');
    }
    await assertGymOpen(gym, input.date);
    await loadEligibleMember(gym, input.memberId, input.date);

    const created = await withTransaction(async (client) => {
      const insert = await insertAttendance({
        gymId: gym.id,
        memberId: input.memberId,
        date: input.date,
        markedBy: auth.userId,
        undoExpiresAt: new Date(),
        source: 'correction',
      });
      if (insert.created && input.date === today) {
        await createSession({
          gymId: gym.id,
          memberId: input.memberId,
          attendanceId: insert.attendance.id,
          expiresAt: addSeconds(new Date(), gym.in_gym_window_minutes * 60),
        });
      }
      await recordAudit(
        {
          gymId: gym.id,
          actorUserId: auth.userId,
          actorName: auth.fullName,
          actorRole: auth.role,
          action: AUDIT_ACTIONS.ATTENDANCE_CORRECTED,
          entityType: 'attendance',
          entityId: insert.attendance.id,
          metadata: {
            correction: 'mark_present',
            memberId: input.memberId,
            attendanceDate: input.date,
            reason: input.reason,
          },
          ip: meta.ip ?? null,
          userAgent: meta.userAgent ?? null,
        },
        client,
      );
      return insert.attendance;
    });

    return { date: input.date, status: 'present', attendanceId: created.id };
  }

  if (!existing) {
    throw AppError.notFound('Attendance record');
  }

  const withinUndoWindow = existing.undo_expires_at.getTime() > Date.now();
  if (withinUndoWindow && auth.role !== 'super_admin') {
    throw new AppError(
      409,
      'UNDO_WINDOW_ACTIVE',
      'The undo window is still open for this record. Use Undo instead of a correction.',
    );
  }

  await withTransaction(async (client) => {
    const updated = await markAttendanceCorrected(existing.id, auth.userId, input.reason, client);
    if (!updated) throw AppError.notFound('Attendance record');
    await voidSessionForAttendance(existing.id, auth.userId, client);
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.ATTENDANCE_CORRECTED,
        entityType: 'attendance',
        entityId: existing.id,
        metadata: {
          correction: 'remove',
          memberId: existing.member_id,
          attendanceDate: existing.attendance_date,
          reason: input.reason,
          markedAt: existing.marked_at.toISOString(),
        },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      client,
    );
  });

  return { date: input.date, status: 'absent', attendanceId: existing.id };
}

export interface ActiveSessionDto {
  id: string;
  memberId: string;
  fullName: string;
  email: string;
  photoUrl: string | null;
  imageVersion: number;
  shiftName: string | null;
  checkedInAt: string;
  expiresAt: string;
  secondsRemaining: number;
}

export async function activeGymSessions(gymId: string): Promise<{ items: ActiveSessionDto[]; total: number }> {
  const rows = await listActiveSessions(gymId);
  const now = new Date();
  return {
    total: rows.length,
    items: rows.map((row) => ({
      id: row.session_id,
      memberId: row.member_id,
      fullName: row.full_name,
      email: row.email,
      photoUrl: mediaUrl(row.avatar_object_key, row.avatar_version),
      imageVersion: row.avatar_version,
      shiftName: row.shift_name,
      checkedInAt: row.checked_in_at.toISOString(),
      expiresAt: row.expires_at.toISOString(),
      secondsRemaining: Math.max(0, Math.floor((row.expires_at.getTime() - now.getTime()) / 1000)),
    })),
  };
}

export async function checkOutMember(
  auth: AuthContext,
  sessionId: string,
  meta: RequestMeta,
): Promise<{ sessionId: string; memberId: string; checkedOutAt: string }> {
  const gym = await requireGym(auth.gymId);
  const session = await checkoutSession(gym.id, sessionId, auth.userId);
  if (!session) {
    throw new AppError(409, 'CONFLICT', 'This session is no longer active.');
  }
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.SESSION_CHECKED_OUT,
    entityType: 'gym_session',
    entityId: session.id,
    metadata: { memberId: session.member_id, checkedInAt: session.checked_in_at.toISOString() },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return {
    sessionId: session.id,
    memberId: session.member_id,
    checkedOutAt: session.checked_out_at?.toISOString() ?? new Date().toISOString(),
  };
}

/** Used by the member dashboard for the current term + occupancy flag. */
export async function memberAttendanceSnapshot(
  gym: GymRow,
  memberId: string,
): Promise<{ today: string; inGymNow: boolean }> {
  const today = todayIn(gym.timezone);
  const sessions = await listActiveSessions(gym.id);
  return { today, inGymNow: sessions.some((session) => session.member_id === memberId) };
}
