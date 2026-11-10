import { env } from '../config/env';
import type { Permission, Role } from '../config/constants';
import type { GymRow, PlanRow, ShiftRow } from '../repositories/gymRepo';
import type { MemberRow, UserRow } from '../repositories/userRepo';
import type { MembershipWithPlanRow } from '../repositories/membershipRepo';
import type { AttendanceMemberRow } from '../repositories/attendanceRepo';
import { diffDays, formatLongDate, formatShortDate } from '../utils/datetime';
import { membershipStatusFor, undoState } from './scoring';

/**
 * Response shapes.
 *
 * These are the contract the Android app is written against — keep in sync with
 * `docs/API.md` and the Kotlin DTOs under `data/remote/dto`.
 */

export function encodeMediaKey(key: string): string {
  return Buffer.from(key, 'utf8').toString('base64url');
}

export function decodeMediaKey(token: string): string {
  return Buffer.from(token, 'base64url').toString('utf8');
}

export function mediaUrl(objectKey: string | null | undefined, version: number): string | null {
  if (!objectKey) return null;
  if (env.MEDIA_PUBLIC_BASE_URL) {
    return `${env.MEDIA_PUBLIC_BASE_URL.replace(/\/$/, '')}/${objectKey}?v=${version}`;
  }
  return `${env.API_BASE_URL.replace(/\/$/, '')}/api/v1/media/${encodeMediaKey(objectKey)}?avatar=v${version}`;
}

export interface UserDto {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  status: 'active' | 'inactive' | 'invited';
  phone: string | null;
  photoUrl: string | null;
  imageVersion: number;
  permissions: Permission[];
  lastLoginAt: string | null;
}

export function toUserDto(user: UserRow): UserDto {
  return {
    id: user.id,
    email: user.email,
    fullName: user.full_name,
    role: user.role,
    status: user.status,
    phone: user.phone,
    photoUrl: mediaUrl(user.avatar_object_key, user.avatar_version),
    imageVersion: user.avatar_version,
    permissions: user.permissions,
    lastLoginAt: user.last_login_at ? user.last_login_at.toISOString() : null,
  };
}

export interface ShiftDto {
  id: string;
  name: string;
  startTime: string;
  endTime: string;
  sortOrder: number;
  isActive: boolean;
}

export function toShiftDto(row: ShiftRow): ShiftDto {
  return {
    id: row.id,
    name: row.name,
    startTime: String(row.start_time).slice(0, 5),
    endTime: String(row.end_time).slice(0, 5),
    sortOrder: row.sort_order,
    isActive: row.is_active,
  };
}

export interface OperatingDayDto {
  dayOfWeek: number;
  isOpen: boolean;
  opensAt: string;
  closesAt: string;
}

export interface PlanDto {
  id: string;
  name: string;
  durationDays: number;
  priceCents: number;
  currency: string;
  description: string | null;
  isActive: boolean;
  sortOrder: number;
}

export function toPlanDto(row: PlanRow): PlanDto {
  return {
    id: row.id,
    name: row.name,
    durationDays: row.duration_days,
    priceCents: row.price_cents,
    currency: row.currency,
    description: row.description,
    isActive: row.is_active,
    sortOrder: row.sort_order,
  };
}

export interface GymDto {
  id: string;
  name: string;
  timezone: string;
  address: string | null;
  phone: string | null;
  email: string | null;
  expiringSoonDays: number;
  archiveAfterDays: number;
  inGymWindowMinutes: number;
  attendanceUndoSeconds: number;
  renewalUndoSeconds: number;
}

export function toGymDto(gym: GymRow): GymDto {
  return {
    id: gym.id,
    name: gym.name,
    timezone: gym.timezone,
    address: gym.address,
    phone: gym.phone,
    email: gym.email,
    expiringSoonDays: gym.expiring_soon_days,
    archiveAfterDays: gym.archive_after_days,
    inGymWindowMinutes: gym.in_gym_window_minutes,
    attendanceUndoSeconds: gym.attendance_undo_seconds,
    renewalUndoSeconds: gym.renewal_undo_seconds,
  };
}

export interface DayComparison {
  previousValue: number;
  difference: number;
  percentChange: number | null;
  direction: 'up' | 'down' | 'flat';
}

export interface StatCard {
  value: number;
  comparison: DayComparison | null;
}

export interface DashboardDto {
  gymLocalDate: string;
  gymLocalDateLabel: string;
  gymOpen: boolean;
  timezone: string;
  gymName: string;
  stats: {
    inGymNow: StatCard;
    totalMembers: StatCard;
    todayAttendance: StatCard;
  };
  generatedAt: string;
}

export interface MemberDto {
  id: string;
  fullName: string;
  email: string;
  phone: string | null;
  gender: string | null;
  address: string | null;
  joinedOn: string;
  archivedAt: string | null;
  emergencyContactName: string | null;
  emergencyContactPhone: string | null;
  photoUrl: string | null;
  imageVersion: number;
  accountStatus: string;
  shift: { id: string; name: string } | null;
  membership: {
    id: string;
    planId: string;
    planName: string | null;
    startDate: string;
    endDate: string;
    status: 'active' | 'expiring' | 'expired' | 'upcoming' | 'none';
    daysRemaining: number;
  } | null;
  membershipStatus: 'active' | 'expiring' | 'expired' | 'upcoming' | 'none';
  score: number;
}

export function toMemberDto(row: MemberRow, gym: GymRow, today: string): MemberDto {
  const status = membershipStatusFor({
    endDate: row.end_date,
    startDate: row.start_date,
    today,
    expiringSoonDays: gym.expiring_soon_days,
  });
  return {
    id: row.id,
    fullName: row.full_name,
    email: row.email,
    phone: row.phone,
    gender: row.gender,
    address: row.address,
    joinedOn: row.joined_on,
    archivedAt: row.archived_at ? row.archived_at.toISOString() : null,
    emergencyContactName: row.emergency_contact_name,
    emergencyContactPhone: row.emergency_contact_phone,
    photoUrl: mediaUrl(row.avatar_object_key, row.avatar_version),
    imageVersion: row.avatar_version,
    accountStatus: row.account_status,
    shift: row.shift_id && row.shift_name ? { id: row.shift_id, name: row.shift_name } : null,
    membership:
      row.membership_id && row.start_date && row.end_date
        ? {
            id: row.membership_id,
            planId: row.plan_id ?? '',
            planName: row.plan_name,
            startDate: row.start_date,
            endDate: row.end_date,
            status,
            daysRemaining: diffDays(today, row.end_date),
          }
        : null,
    membershipStatus: status,
    score: row.score,
  };
}

export interface SessionDto {
  id: string;
  checkedInAt: string;
  expiresAt: string;
  secondsRemaining: number;
  isActive: boolean;
  checkedOutAt: string | null;
  status: string;
}

export interface AttendanceMemberDto {
  memberId: string;
  fullName: string;
  email: string;
  phone: string | null;
  photoUrl: string | null;
  imageVersion: number;
  gender: string | null;
  shift: { id: string; name: string } | null;
  plan: { id: string; name: string | null } | null;
  membershipEndDate: string | null;
  membershipStatus: 'active' | 'expiring' | 'expired' | 'upcoming' | 'none';
  status: 'present' | 'absent';
  attendanceId: string | null;
  markedAt: string | null;
  canUndo: boolean;
  undoSecondsRemaining: number;
  session: SessionDto | null;
  score: number;
}

export function toAttendanceMemberDto(
  row: AttendanceMemberRow,
  gym: GymRow,
  today: string,
  now: Date,
): AttendanceMemberDto {
  const undo = undoState(row.undo_expires_at, now);
  const status = membershipStatusFor({
    endDate: row.end_date,
    startDate: row.start_date,
    today,
    expiringSoonDays: gym.expiring_soon_days,
  });
  const sessionActive =
    Boolean(row.session_id) && row.session_status === 'active' && row.expires_at !== null && row.expires_at > now;

  return {
    memberId: row.id,
    fullName: row.full_name,
    email: row.email,
    phone: row.phone,
    photoUrl: mediaUrl(row.avatar_object_key, row.avatar_version),
    imageVersion: row.avatar_version,
    gender: row.gender,
    shift: row.shift_id && row.shift_name ? { id: row.shift_id, name: row.shift_name } : null,
    plan: row.plan_id ? { id: row.plan_id, name: row.plan_name } : null,
    membershipEndDate: row.end_date,
    membershipStatus: status,
    status: row.attendance_id ? 'present' : 'absent',
    attendanceId: row.attendance_id,
    markedAt: row.marked_at ? row.marked_at.toISOString() : null,
    canUndo: Boolean(row.attendance_id) && undo.canUndo,
    undoSecondsRemaining: undo.secondsRemaining,
    session: row.session_id
      ? {
          id: row.session_id,
          checkedInAt: row.checked_in_at ? row.checked_in_at.toISOString() : '',
          expiresAt: row.expires_at ? row.expires_at.toISOString() : '',
          secondsRemaining: row.expires_at
            ? Math.max(0, Math.floor((row.expires_at.getTime() - now.getTime()) / 1000))
            : 0,
          isActive: sessionActive,
          checkedOutAt: row.checked_out_at ? row.checked_out_at.toISOString() : null,
          status: row.session_status ?? 'unknown',
        }
      : null,
    score: row.score,
  };
}

export interface MembershipDto {
  id: string;
  planId: string;
  planName: string;
  startDate: string;
  endDate: string;
  status: 'active' | 'expiring' | 'expired' | 'upcoming' | 'none';
  rowStatus: string;
  daysRemaining: number;
  priceCents: number;
  currency: string;
}

export function toMembershipDto(row: MembershipWithPlanRow, gym: GymRow, today: string): MembershipDto {
  return {
    id: row.id,
    planId: row.plan_id,
    planName: row.plan_name,
    startDate: row.start_date,
    endDate: row.end_date,
    status: membershipStatusFor({
      endDate: row.end_date,
      startDate: row.start_date,
      today,
      expiringSoonDays: gym.expiring_soon_days,
    }),
    rowStatus: row.status,
    daysRemaining: diffDays(today, row.end_date),
    priceCents: row.price_cents,
    currency: row.currency,
  };
}

export interface UndoWindowDto {
  canUndo: boolean;
  secondsRemaining: number;
  expiresAt: string;
}

export function toUndoWindow(expiresAt: Date, now: Date = new Date()): UndoWindowDto {
  const state = undoState(expiresAt, now);
  return { ...state, expiresAt: expiresAt.toISOString() };
}

export interface AttendanceRecordDto {
  id: string;
  memberId: string;
  attendanceDate: string;
  markedAt: string;
  markedBy: string | null;
  status: string;
  source: string;
  undoExpiresAt: string;
  undoneAt: string | null;
  note: string | null;
}

export function dateLabel(iso: string): string {
  return formatLongDate(iso);
}

export function shortLabel(iso: string): string {
  return formatShortDate(iso);
}

export type { PlanRow };
