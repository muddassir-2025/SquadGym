/** Shared constants: roles, permissions, business defaults, error codes. */

export const ROLES = ['super_admin', 'admin', 'member'] as const;
export type Role = (typeof ROLES)[number];

export const USER_STATUSES = ['active', 'inactive', 'invited'] as const;
export type UserStatus = (typeof USER_STATUSES)[number];

export const PERMISSIONS = [
  'dashboard.view',
  'members.view',
  'members.manage',
  'attendance.view',
  'attendance.manage',
  'attendance.correct',
  'memberships.view',
  'memberships.manage',
  'leaderboard.view',
  'plans.manage',
  'admins.manage',
  'audit.view',
  'settings.manage',
] as const;
export type Permission = (typeof PERMISSIONS)[number];

export const DEFAULT_ADMIN_PERMISSIONS: Permission[] = [
  'dashboard.view',
  'members.view',
  'members.manage',
  'attendance.view',
  'attendance.manage',
  'attendance.correct',
  'memberships.view',
  'memberships.manage',
  'leaderboard.view',
];

export const SUPER_ADMIN_PERMISSIONS: Permission[] = [...PERMISSIONS];

export const GENDERS = ['male', 'female', 'other'] as const;
export type Gender = (typeof GENDERS)[number];

export const SHIFTS = ['morning', 'evening'] as const;

/** Attendance status of a member for one gym day. */
export const ATTENDANCE_STATUS = ['present', 'absent'] as const;

export const MEMBERSHIP_ROW_STATUS = ['active', 'superseded', 'reversed', 'cancelled'] as const;
export type MembershipRowStatus = (typeof MEMBERSHIP_ROW_STATUS)[number];

export const AUDIT_ACTIONS = {
  LOGIN: 'auth.login',
  LOGOUT: 'auth.logout',
  PASSWORD_RESET: 'auth.password_reset',
  INVITATION_ACCEPTED: 'invitation.accepted',
  MEMBER_CREATED: 'member.created',
  MEMBER_UPDATED: 'member.updated',
  MEMBER_ARCHIVED: 'member.archived',
  MEMBER_RESTORED: 'member.restored',
  MEMBER_PHOTO_UPDATED: 'member.photo_updated',
  ATTENDANCE_MARKED: 'attendance.marked',
  ATTENDANCE_UNDONE: 'attendance.undone',
  ATTENDANCE_CORRECTED: 'attendance.corrected',
  SESSION_CHECKED_OUT: 'attendance.session_checked_out',
  MEMBERSHIP_RENEWED: 'membership.renewed',
  MEMBERSHIP_RENEWAL_REVERSED: 'membership.renewal_reversed',
  PLAN_CREATED: 'plan.created',
  PLAN_UPDATED: 'plan.updated',
  ADMIN_CREATED: 'admin.created',
  ADMIN_UPDATED: 'admin.updated',
  ADMIN_STATUS_CHANGED: 'admin.status_changed',
  ADMIN_PERMISSIONS_CHANGED: 'admin.permissions_changed',
  SETTINGS_UPDATED: 'settings.updated',
} as const;

export const WEEKDAY_NAMES = [
  'Monday',
  'Tuesday',
  'Wednesday',
  'Thursday',
  'Friday',
  'Saturday',
  'Sunday',
] as const;
