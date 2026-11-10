import { z } from 'zod';
import { GENDERS, PERMISSIONS, ROLES } from '../config/constants';
import { PASSWORD_MAX_LENGTH, PASSWORD_MIN_LENGTH } from '../utils/password';
import { isIsoDate, isMonth } from '../utils/datetime';

/** Request schemas. Every route validates before touching a service. */

export const uuidSchema = z.string().uuid('That identifier is not valid.');
export const isoDateSchema = z
  .string()
  .refine(isIsoDate, 'Use the YYYY-MM-DD format.');
export const monthSchema = z.string().refine(isMonth, 'Use the YYYY-MM format.');
export const emailSchema = z.string().trim().toLowerCase().email('Enter a valid email address.').max(200);
export const passwordSchema = z
  .string()
  .min(PASSWORD_MIN_LENGTH, `Use at least ${PASSWORD_MIN_LENGTH} characters.`)
  .max(PASSWORD_MAX_LENGTH, `Use at most ${PASSWORD_MAX_LENGTH} characters.`);
export const phoneSchema = z.string().trim().min(5, 'Enter a valid phone number.').max(30).nullish();
export const idParams = z.object({ id: uuidSchema });

export const paginationSchema = z.object({
  page: z.coerce.number().int().min(1).max(100_000).optional(),
  pageSize: z.coerce.number().int().min(1).max(100).optional(),
});

// ── Auth ────────────────────────────────────────────────────────────────────

export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, 'Enter your password.').max(200),
});

export const refreshSchema = z.object({
  refreshToken: z.string().min(16, 'A refresh token is required.').max(500),
});

export const logoutSchema = z.object({
  refreshToken: z.string().max(500).optional(),
});

export const forgotPasswordSchema = z.object({ email: emailSchema });

export const resetPasswordSchema = z.object({
  token: z.string().trim().min(4, 'Enter the reset code.').max(200),
  newPassword: passwordSchema,
});

export const acceptInvitationSchema = z.object({
  token: z.string().trim().min(10, 'This invitation link is not valid.').max(500),
  password: passwordSchema,
  fullName: z.string().trim().min(2).max(120).optional(),
});

export const invitationTokenParams = z.object({ token: z.string().min(10).max(500) });

// ── Members ─────────────────────────────────────────────────────────────────

export const memberTabSchema = z.enum(['all', 'expiring', 'expired', 'archived']);
export const memberSortSchema = z.enum(['score_desc', 'score_asc', 'newest', 'oldest', 'expiry_asc', 'name_asc']);

export const listMembersQuerySchema = paginationSchema.extend({
  tab: memberTabSchema.default('all'),
  search: z.string().trim().max(120).optional(),
  gender: z.enum(GENDERS).optional(),
  planId: uuidSchema.optional(),
  shiftId: uuidSchema.optional(),
  status: z.enum(['active', 'expiring', 'expired', 'none']).optional(),
  minScore: z.coerce.number().int().min(0).max(10_000).optional(),
  maxScore: z.coerce.number().int().min(0).max(10_000).optional(),
  expiresFrom: isoDateSchema.optional(),
  expiresTo: isoDateSchema.optional(),
  joinedFrom: isoDateSchema.optional(),
  joinedTo: isoDateSchema.optional(),
  sort: memberSortSchema.optional(),
});

export const createMemberSchema = z.object({
  fullName: z.string().trim().min(2, 'Enter the full name.').max(120),
  email: emailSchema,
  phone: phoneSchema,
  gender: z.enum(GENDERS).nullish(),
  address: z.string().trim().max(500).nullish(),
  planId: uuidSchema,
  startDate: isoDateSchema.optional(),
  shiftId: uuidSchema.nullish(),
  emergencyContactName: z.string().trim().max(120).nullish(),
  emergencyContactPhone: z.string().trim().max(30).nullish(),
  dateOfBirth: isoDateSchema.nullish(),
  notes: z.string().trim().max(1000).nullish(),
  initialPassword: passwordSchema.nullish(),
});

export const updateMemberSchema = z
  .object({
    fullName: z.string().trim().min(2).max(120).optional(),
    email: emailSchema.optional(),
    phone: phoneSchema,
    gender: z.enum(GENDERS).nullish(),
    address: z.string().trim().max(500).nullish(),
    shiftId: uuidSchema.nullish(),
    emergencyContactName: z.string().trim().max(120).nullish(),
    emergencyContactPhone: z.string().trim().max(30).nullish(),
    dateOfBirth: isoDateSchema.nullish(),
    notes: z.string().trim().max(1000).nullish(),
  })
  .refine((value) => Object.keys(value).length > 0, 'Nothing to update.');

export const archiveMemberSchema = z.object({
  reason: z.string().trim().max(300).optional(),
});

// ── Attendance ──────────────────────────────────────────────────────────────

export const attendanceTabSchema = z.enum(['all', 'present', 'absent']);

export const attendanceQuerySchema = paginationSchema.extend({
  date: isoDateSchema.optional(),
  tab: attendanceTabSchema.default('all'),
  search: z.string().trim().max(120).optional(),
  shiftId: uuidSchema.optional(),
  gender: z.enum(GENDERS).optional(),
  planId: uuidSchema.optional(),
});

export const markAttendanceSchema = z.object({
  memberId: uuidSchema,
  date: isoDateSchema.optional(),
});

export const correctionSchema = z.object({
  memberId: uuidSchema,
  date: isoDateSchema,
  action: z.enum(['mark_present', 'remove']),
  reason: z.string().trim().min(3, 'Explain the correction.').max(300),
});

export const attendanceHistoryQuerySchema = z.object({
  from: isoDateSchema.optional(),
  to: isoDateSchema.optional(),
});

// ── Memberships ─────────────────────────────────────────────────────────────

export const renewSchema = z.object({ planId: uuidSchema });
export const renewalPreviewQuerySchema = z.object({ planId: uuidSchema });

export const planBodySchema = z.object({
  name: z.string().trim().min(2).max(60),
  durationDays: z.coerce.number().int().min(1).max(3650),
  priceCents: z.coerce.number().int().min(0).max(100_000_000).optional(),
  currency: z.string().trim().length(3).toUpperCase().optional(),
  description: z.string().trim().max(300).nullish(),
  sortOrder: z.coerce.number().int().min(0).max(1000).optional(),
});

export const planPatchSchema = planBodySchema.partial().extend({
  isActive: z.boolean().optional(),
});

// ── Dashboard / leaderboard ─────────────────────────────────────────────────

export const chartQuerySchema = z.object({
  metric: z.enum(['attendance', 'new_members', 'total_members']).default('attendance'),
  range: z.enum(['week', 'month', 'custom']).default('week'),
  from: isoDateSchema.optional(),
  to: isoDateSchema.optional(),
});

export const occupancyQuerySchema = z.object({ date: isoDateSchema.optional() });

export const leaderboardQuerySchema = paginationSchema.extend({
  period: z.enum(['week', 'month']).default('week'),
  anchor: isoDateSchema.optional(),
});

export const memberDashboardQuerySchema = z.object({ month: monthSchema.optional() });
export const calendarQuerySchema = z.object({ month: monthSchema });
export const myHistoryQuerySchema = z.object({
  from: isoDateSchema.optional(),
  to: isoDateSchema.optional(),
});

// ── Super Admin ─────────────────────────────────────────────────────────────

const permissionSchema = z.enum(PERMISSIONS);

export const createAdminSchema = z.object({
  fullName: z.string().trim().min(2).max(120),
  email: emailSchema,
  phone: phoneSchema,
  permissions: z.array(permissionSchema).max(30).optional(),
  initialPassword: passwordSchema.nullish(),
});

export const updateAdminSchema = z
  .object({
    fullName: z.string().trim().min(2).max(120).optional(),
    phone: phoneSchema,
    permissions: z.array(permissionSchema).max(30).optional(),
  })
  .refine((value) => Object.keys(value).length > 0, 'Nothing to update.');

export const adminStatusSchema = z.object({ status: z.enum(['active', 'inactive']) });

export const gymSettingsSchema = z
  .object({
    name: z.string().trim().min(2).max(120).optional(),
    timezone: z.string().trim().min(3).max(60).optional(),
    address: z.string().trim().max(300).nullish(),
    phone: z.string().trim().max(30).nullish(),
    email: z.string().trim().email().nullish(),
    expiringSoonDays: z.coerce.number().int().min(1).max(120).optional(),
    archiveAfterDays: z.coerce.number().int().min(1).max(3650).optional(),
    inGymWindowMinutes: z.coerce.number().int().min(5).max(480).optional(),
    attendanceUndoSeconds: z.coerce.number().int().min(0).max(86_400).optional(),
    renewalUndoSeconds: z.coerce.number().int().min(0).max(86_400).optional(),
  })
  .refine((value) => Object.keys(value).length > 0, 'Nothing to update.');

export const operatingDaysSchema = z.object({
  days: z
    .array(
      z.object({
        dayOfWeek: z.coerce.number().int().min(1).max(7),
        isOpen: z.boolean(),
        opensAt: z.string().regex(/^\d{2}:\d{2}$/).optional(),
        closesAt: z.string().regex(/^\d{2}:\d{2}$/).optional(),
      }),
    )
    .min(1)
    .max(7),
});

export const shiftBodySchema = z.object({
  name: z.string().trim().min(1).max(40),
  startTime: z.string().regex(/^\d{2}:\d{2}$/, 'Use HH:MM.'),
  endTime: z.string().regex(/^\d{2}:\d{2}$/, 'Use HH:MM.'),
  sortOrder: z.coerce.number().int().min(0).max(100).optional(),
});

export const shiftPatchSchema = shiftBodySchema.partial().extend({ isActive: z.boolean().optional() });

export const auditQuerySchema = paginationSchema.extend({
  action: z.string().trim().max(80).optional(),
  actorId: uuidSchema.optional(),
  entityType: z.string().trim().max(60).optional(),
  from: isoDateSchema.optional(),
  to: isoDateSchema.optional(),
  search: z.string().trim().max(120).optional(),
});

export const mediaParams = z.object({ token: z.string().min(4).max(1000) });

export const roleSchema = z.enum(ROLES);

export type LoginInput = z.infer<typeof loginSchema>;
export type ListMembersQuery = z.infer<typeof listMembersQuerySchema>;
export type AttendanceQuery = z.infer<typeof attendanceQuerySchema>;
export type CreateMemberBody = z.infer<typeof createMemberSchema>;
export type UpdateMemberBody = z.infer<typeof updateMemberSchema>;
export type CreateAdminBody = z.infer<typeof createAdminSchema>;
export type UpdateAdminBody = z.infer<typeof updateAdminSchema>;
export type GymSettingsBody = z.infer<typeof gymSettingsSchema>;
export type ChartQuery = z.infer<typeof chartQuerySchema>;
export type LeaderboardQuery = z.infer<typeof leaderboardQuerySchema>;
export type AuditQuery = z.infer<typeof auditQuerySchema>;
