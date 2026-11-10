import { AUDIT_ACTIONS, DEFAULT_ADMIN_PERMISSIONS, PERMISSIONS, type Permission } from '../config/constants';
import { AppError } from '../utils/errors';
import type { AuthContext } from '../types/auth';
import type { RequestMeta } from './authService';
import { issueInvitation } from './authService';
import { recordAudit, listAuditLogs, type AuditFilter } from '../repositories/auditRepo';
import {
  countActiveSuperAdmins,
  createAdmin,
  emailExists,
  getAdmin,
  listAdmins,
  setUserPermissions,
  setUserStatus,
  updateUserProfile,
  type AdminRow,
} from '../repositories/userRepo';
import {
  createPlan,
  createShift,
  getOperatingDays,
  getPlan,
  getShift,
  listPlans,
  listShifts,
  replaceOperatingDays,
  requireGym,
  updateGymSettings,
  updatePlan,
  updateShift,
  type GymSettingsPatch,
} from '../repositories/gymRepo';
import { hashPassword } from '../utils/password';
import { mediaUrl, toGymDto, toPlanDto, toShiftDto, type GymDto, type PlanDto, type ShiftDto } from './dto';

export interface AdminDto {
  id: string;
  email: string;
  fullName: string;
  phone: string | null;
  role: 'admin' | 'super_admin';
  status: 'active' | 'inactive' | 'invited';
  permissions: Permission[];
  photoUrl: string | null;
  imageVersion: number;
  lastLoginAt: string | null;
  createdAt: string;
}

function toAdminDto(row: AdminRow): AdminDto {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    phone: row.phone,
    role: row.role === 'super_admin' ? 'super_admin' : 'admin',
    status: row.status,
    permissions: row.permissions,
    photoUrl: mediaUrl(row.avatar_object_key, row.avatar_version),
    imageVersion: row.avatar_version,
    lastLoginAt: row.last_login_at ? row.last_login_at.toISOString() : null,
    createdAt: row.created_at.toISOString(),
  };
}

export async function listAdminsService(auth: AuthContext): Promise<AdminDto[]> {
  const rows = await listAdmins(auth.gymId);
  return rows.map(toAdminDto);
}

export interface CreateAdminInput {
  fullName: string;
  email: string;
  phone?: string | null;
  permissions?: Permission[];
  initialPassword?: string | null;
}

export async function createAdminService(
  auth: AuthContext,
  input: CreateAdminInput,
  meta: RequestMeta,
): Promise<{ admin: AdminDto; invitation: { sent: boolean; token?: string } | null }> {
  const gym = await requireGym(auth.gymId);
  const email = input.email.trim().toLowerCase();
  if (await emailExists(email)) {
    throw AppError.validation({ email: 'An account with this email already exists.' });
  }
  const permissions = sanitizePermissions(input.permissions);
  const passwordHash = input.initialPassword ? await hashPassword(input.initialPassword) : null;
  const status = passwordHash ? 'active' : 'invited';

  const adminId = await createAdmin({
    gymId: gym.id,
    email,
    fullName: input.fullName.trim(),
    phone: input.phone ?? null,
    passwordHash,
    status,
    permissions,
  });

  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.ADMIN_CREATED,
    entityType: 'admin',
    entityId: adminId,
    metadata: { email, fullName: input.fullName, permissions, invited: status === 'invited' },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });

  let invitation: { sent: boolean; token?: string } | null = null;
  if (status === 'invited') {
    const issued = await issueInvitation({
      gymId: gym.id,
      gymName: gym.name,
      email,
      fullName: input.fullName.trim(),
      role: 'admin',
      userId: adminId,
      invitedBy: auth.userId,
    });
    invitation = { sent: issued.delivered, ...(issued.token ? { token: issued.token } : {}) };
  }

  const created = await getAdmin(gym.id, adminId);
  if (!created) throw AppError.internal();
  return { admin: toAdminDto(created), invitation };
}

export interface UpdateAdminInput {
  fullName?: string;
  phone?: string | null;
  permissions?: Permission[];
}

export async function updateAdminService(
  auth: AuthContext,
  adminId: string,
  input: UpdateAdminInput,
  meta: RequestMeta,
): Promise<AdminDto> {
  const gym = await requireGym(auth.gymId);
  const existing = await getAdmin(gym.id, adminId);
  if (!existing) throw AppError.notFound('Admin');

  if (existing.role === 'super_admin' && input.permissions) {
    throw AppError.forbidden('Super Admin permissions cannot be edited.');
  }

  if (input.fullName !== undefined || input.phone !== undefined) {
    await updateUserProfile(adminId, { fullName: input.fullName, phone: input.phone });
  }
  if (input.permissions) {
    await setUserPermissions(adminId, sanitizePermissions(input.permissions));
  }

  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: input.permissions ? AUDIT_ACTIONS.ADMIN_PERMISSIONS_CHANGED : AUDIT_ACTIONS.ADMIN_UPDATED,
    entityType: 'admin',
    entityId: adminId,
    metadata: {
      changes: Object.keys(input).filter((key) => input[key as keyof UpdateAdminInput] !== undefined),
      permissions: input.permissions ?? undefined,
      previousPermissions: existing.permissions,
    },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });

  const refreshed = await getAdmin(gym.id, adminId);
  if (!refreshed) throw AppError.notFound('Admin');
  return toAdminDto(refreshed);
}

export async function setAdminStatusService(
  auth: AuthContext,
  adminId: string,
  status: 'active' | 'inactive',
  meta: RequestMeta,
): Promise<AdminDto> {
  const gym = await requireGym(auth.gymId);
  const existing = await getAdmin(gym.id, adminId);
  if (!existing) throw AppError.notFound('Admin');
  if (existing.id === auth.userId) {
    throw new AppError(409, 'CONFLICT', 'You cannot deactivate your own account.');
  }
  if (existing.role === 'super_admin' && status === 'inactive') {
    const remaining = await countActiveSuperAdmins(gym.id);
    if (remaining <= 1) {
      throw new AppError(409, 'CONFLICT', 'At least one active Super Admin must remain.');
    }
  }

  const updated = await setUserStatus(adminId, status);
  if (!updated) throw AppError.notFound('Admin');

  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.ADMIN_STATUS_CHANGED,
    entityType: 'admin',
    entityId: adminId,
    metadata: { from: existing.status, to: status, adminName: existing.full_name },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });

  const refreshed = await getAdmin(gym.id, adminId);
  if (!refreshed) throw AppError.notFound('Admin');
  return toAdminDto(refreshed);
}

function sanitizePermissions(permissions: Permission[] | undefined): Permission[] {
  if (!permissions) return DEFAULT_ADMIN_PERMISSIONS;
  const known = new Set<string>(PERMISSIONS);
  const filtered = permissions.filter((permission) => known.has(permission));
  // Admins can never manage admins, gym settings or the audit log implicitly.
  return Array.from(new Set(filtered)).filter(
    (permission) => permission !== 'admins.manage' && permission !== 'settings.manage',
  );
}

export interface GymConfigurationDto {
  gym: GymDto;
  operatingDays: Array<{ dayOfWeek: number; dayLabel: string; isOpen: boolean; opensAt: string; closesAt: string }>;
  shifts: ShiftDto[];
  plans: PlanDto[];
  permissionsCatalog: Permission[];
}

export async function gymConfiguration(auth: AuthContext): Promise<GymConfigurationDto> {
  const gym = await requireGym(auth.gymId);
  const [days, shifts, plans] = await Promise.all([
    getOperatingDays(gym.id),
    listShifts(gym.id, true),
    listPlans(gym.id, true),
  ]);
  return {
    gym: toGymDto(gym),
    operatingDays: days.map((day) => ({
      dayOfWeek: day.day_of_week,
      dayLabel: dayLabel(day.day_of_week),
      isOpen: day.is_open,
      opensAt: String(day.opens_at).slice(0, 5),
      closesAt: String(day.closes_at).slice(0, 5),
    })),
    shifts: shifts.map(toShiftDto),
    plans: plans.map(toPlanDto),
    permissionsCatalog: [...PERMISSIONS],
  };
}

function dayLabel(dayOfWeek: number): string {
  return ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday', 'Sunday'][dayOfWeek - 1] ?? '';
}

export async function updateGymSettingsService(
  auth: AuthContext,
  patch: GymSettingsPatch,
  meta: RequestMeta,
): Promise<GymDto> {
  const gym = await requireGym(auth.gymId);
  if (patch.timezone) assertTimeZone(patch.timezone);
  const updated = await updateGymSettings(gym.id, patch);
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.SETTINGS_UPDATED,
    entityType: 'gym',
    entityId: gym.id,
    metadata: { changes: patch },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return toGymDto(updated);
}

function assertTimeZone(timezone: string): void {
  try {
    new Intl.DateTimeFormat('en-US', { timeZone: timezone }).format(new Date());
  } catch {
    throw AppError.validation({ timezone: 'That is not a valid IANA timezone (e.g. Asia/Kolkata).' });
  }
}

export async function updateOperatingDaysService(
  auth: AuthContext,
  days: Array<{ dayOfWeek: number; isOpen: boolean; opensAt?: string; closesAt?: string }>,
  meta: RequestMeta,
): Promise<GymConfigurationDto> {
  const gym = await requireGym(auth.gymId);
  await replaceOperatingDays(gym.id, days);
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.SETTINGS_UPDATED,
    entityType: 'gym_operating_days',
    entityId: gym.id,
    metadata: { days },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return gymConfiguration(auth);
}

export async function createShiftService(
  auth: AuthContext,
  input: { name: string; startTime: string; endTime: string; sortOrder?: number },
  meta: RequestMeta,
): Promise<ShiftDto> {
  const gym = await requireGym(auth.gymId);
  const existing = (await listShifts(gym.id, true)).find(
    (shift) => shift.name.toLowerCase() === input.name.trim().toLowerCase(),
  );
  if (existing) throw AppError.validation({ name: 'A shift with that name already exists.' });

  const created = await createShift(gym.id, { ...input, name: input.name.trim() });
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.SETTINGS_UPDATED,
    entityType: 'gym_shift',
    entityId: created.id,
    metadata: { name: created.name, startTime: created.start_time, endTime: created.end_time },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return toShiftDto(created);
}

export async function updateShiftService(
  auth: AuthContext,
  shiftId: string,
  input: { name?: string; startTime?: string; endTime?: string; sortOrder?: number; isActive?: boolean },
  meta: RequestMeta,
): Promise<ShiftDto> {
  const gym = await requireGym(auth.gymId);
  const existing = await getShift(gym.id, shiftId);
  if (!existing) throw AppError.notFound('Shift');
  const updated = await updateShift(gym.id, shiftId, input);
  if (!updated) throw AppError.notFound('Shift');
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.SETTINGS_UPDATED,
    entityType: 'gym_shift',
    entityId: shiftId,
    metadata: { changes: input },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return toShiftDto(updated);
}

export async function createPlanService(
  auth: AuthContext,
  input: { name: string; durationDays: number; priceCents?: number; currency?: string; description?: string | null; sortOrder?: number },
  meta: RequestMeta,
): Promise<PlanDto> {
  const gym = await requireGym(auth.gymId);
  const created = await createPlan(gym.id, { ...input, name: input.name.trim() });
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.PLAN_CREATED,
    entityType: 'membership_plan',
    entityId: created.id,
    metadata: { name: created.name, durationDays: created.duration_days },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return toPlanDto(created);
}

export async function updatePlanService(
  auth: AuthContext,
  planId: string,
  input: { name?: string; durationDays?: number; priceCents?: number; currency?: string; description?: string | null; isActive?: boolean; sortOrder?: number },
  meta: RequestMeta,
): Promise<PlanDto> {
  const gym = await requireGym(auth.gymId);
  const existing = await getPlan(gym.id, planId);
  if (!existing) throw AppError.notFound('Plan');
  const updated = await updatePlan(gym.id, planId, input);
  if (!updated) throw AppError.notFound('Plan');
  await recordAudit({
    gymId: gym.id,
    actorUserId: auth.userId,
    actorName: auth.fullName,
    actorRole: auth.role,
    action: AUDIT_ACTIONS.PLAN_UPDATED,
    entityType: 'membership_plan',
    entityId: planId,
    metadata: { changes: input },
    ip: meta.ip ?? null,
    userAgent: meta.userAgent ?? null,
  });
  return toPlanDto(updated);
}

export async function auditLogsService(
  auth: AuthContext,
  filter: AuditFilter,
  page: { page: number; pageSize: number; offset: number },
) {
  const { items, total } = await listAuditLogs(auth.gymId, filter, {
    limit: page.pageSize,
    offset: page.offset,
  });
  return {
    items: items.map((row) => ({
      id: row.id,
      actorUserId: row.actor_user_id,
      actorName: row.actor_name,
      actorRole: row.actor_role,
      action: row.action,
      entityType: row.entity_type,
      entityId: row.entity_id,
      metadata: row.metadata,
      createdAt: row.created_at.toISOString(),
    })),
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
