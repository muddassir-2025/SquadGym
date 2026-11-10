import { AUDIT_ACTIONS } from '../config/constants';
import { AppError } from '../utils/errors';
import { addSeconds, diffDays, todayIn } from '../utils/datetime';
import type { AuthContext } from '../types/auth';
import type { RequestMeta } from './authService';
import { recordAudit } from '../repositories/auditRepo';
import { getPlan, listPlans, requireGym } from '../repositories/gymRepo';
import {
  createMembership,
  createRenewal,
  getCurrentMembership,
  getRenewal,
  isLatestRenewal,
  listMembershipHistory,
  listRenewals,
  markRenewalReversed,
  renewMembershipTransaction,
  setMembershipStatus,
  type MembershipWithPlanRow,
} from '../repositories/membershipRepo';
import { findUserById, getMember } from '../repositories/userRepo';
import { computeRenewalDates, undoState } from './scoring';
import { toMembershipDto, type MembershipDto } from './dto';

export interface RenewalPreviewDto {
  memberId: string;
  planId: string;
  planName: string;
  durationDays: number;
  current: MembershipDto | null;
  newStartDate: string;
  newEndDate: string;
  extendedFromExisting: boolean;
  priceCents: number;
  currency: string;
}

export async function previewRenewal(
  auth: AuthContext,
  memberId: string,
  planId: string,
): Promise<RenewalPreviewDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const plan = await getPlan(gym.id, planId);
  if (!plan || !plan.is_active) {
    throw AppError.validation({ planId: 'Choose an active membership plan.' });
  }
  const member = await getMember(gym.id, memberId, today);
  if (!member) throw AppError.notFound('Member');
  const current = await getCurrentMembership(memberId);
  const dates = computeRenewalDates({
    durationDays: plan.duration_days,
    today,
    currentEndDate: current?.end_date ?? null,
  });

  return {
    memberId,
    planId: plan.id,
    planName: plan.name,
    durationDays: plan.duration_days,
    current: current ? toMembershipDto(current, gym, today) : null,
    newStartDate: dates.startDate,
    newEndDate: dates.endDate,
    extendedFromExisting: dates.extendedFromExisting,
    priceCents: plan.price_cents,
    currency: plan.currency,
  };
}

export interface RenewalResultDto {
  memberId: string;
  membership: MembershipDto;
  previousMembership: MembershipDto | null;
  renewal: {
    id: string;
    performedAt: string;
    undoExpiresAt: string;
    canUndo: boolean;
    secondsRemaining: number;
    extendedFromExisting: boolean;
  };
}

export async function renewMembership(
  auth: AuthContext,
  memberId: string,
  planId: string,
  meta: RequestMeta,
): Promise<RenewalResultDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);

  const plan = await getPlan(gym.id, planId);
  if (!plan || !plan.is_active) {
    throw AppError.validation({ planId: 'Choose an active membership plan.' });
  }
  const member = await findUserById(memberId);
  if (!member || member.gym_id !== gym.id || member.role !== 'member') throw AppError.notFound('Member');
  const current = await getCurrentMembership(memberId);
  if (!current) {
    throw new AppError(409, 'MEMBER_NOT_ELIGIBLE', 'This member has no membership to renew. Create one first.');
  }

  const dates = computeRenewalDates({
    durationDays: plan.duration_days,
    today,
    currentEndDate: current.end_date,
  });
  const now = new Date();
  const undoExpiresAt = addSeconds(now, gym.renewal_undo_seconds);

  const result = await renewMembershipTransaction(async (client) => {
    await setMembershipStatus(current.id, 'superseded', client);
    const created = await createMembership(
      {
        gymId: gym.id,
        memberId,
        planId: plan.id,
        startDate: dates.startDate,
        endDate: dates.endDate,
        createdBy: auth.userId,
      },
      client,
    );
    const renewal = await createRenewal(
      {
        gymId: gym.id,
        memberId,
        previousMembershipId: current.id,
        newMembershipId: created.id,
        previousPlanId: current.plan_id,
        newPlanId: plan.id,
        previousStartDate: current.start_date,
        previousEndDate: current.end_date,
        newStartDate: dates.startDate,
        newEndDate: dates.endDate,
        performedBy: auth.userId,
        undoExpiresAt,
        metadata: {
          planName: plan.name,
          durationDays: plan.duration_days,
          extendedFromExisting: dates.extendedFromExisting,
        },
      },
      client,
    );
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.MEMBERSHIP_RENEWED,
        entityType: 'membership',
        entityId: created.id,
        metadata: {
          memberId,
          memberName: member.full_name,
          renewalId: renewal.id,
          previous: { planId: current.plan_id, startDate: current.start_date, endDate: current.end_date },
          next: { planId: plan.id, planName: plan.name, startDate: dates.startDate, endDate: dates.endDate },
          extendedFromExisting: dates.extendedFromExisting,
        },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      client,
    );
    return { created, renewal };
  });

  const createdWithPlan: MembershipWithPlanRow = {
    ...result.created,
    plan_name: plan.name,
    duration_days: plan.duration_days,
    price_cents: plan.price_cents,
    currency: plan.currency,
  };
  const undo = undoState(undoExpiresAt, now);

  return {
    memberId,
    membership: toMembershipDto(createdWithPlan, gym, today),
    previousMembership: toMembershipDto(current, gym, today),
    renewal: {
      id: result.renewal.id,
      performedAt: result.renewal.performed_at.toISOString(),
      undoExpiresAt: undoExpiresAt.toISOString(),
      canUndo: undo.canUndo,
      secondsRemaining: undo.secondsRemaining,
      extendedFromExisting: dates.extendedFromExisting,
    },
  };
}

export interface UndoRenewalResultDto {
  memberId: string;
  membership: MembershipDto;
  renewalId: string;
  reversedAt: string;
}

export async function undoRenewal(
  auth: AuthContext,
  renewalId: string,
  meta: RequestMeta,
): Promise<UndoRenewalResultDto> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const renewal = await getRenewal(renewalId);
  if (!renewal || renewal.gym_id !== gym.id) throw AppError.notFound('Renewal');

  if (renewal.status !== 'active') {
    throw new AppError(409, 'RENEWAL_NOT_REVERSIBLE', 'This renewal was already reversed.');
  }
  const now = new Date();
  if (renewal.undo_expires_at.getTime() <= now.getTime()) {
    throw AppError.undoWindowExpired('The five-minute renewal undo window has passed.');
  }
  if (!(await isLatestRenewal(renewal))) {
    throw new AppError(
      409,
      'RENEWAL_NOT_REVERSIBLE',
      'A newer membership change exists for this member, so this renewal can no longer be undone.',
    );
  }

  await renewMembershipTransaction(async (client) => {
    await setMembershipStatus(renewal.new_membership_id, 'reversed', client);
    await setMembershipStatus(renewal.previous_membership_id, 'active', client);
    const reversed = await markRenewalReversed(renewal.id, auth.userId, 'Undo from app', client);
    if (!reversed) throw new AppError(409, 'RENEWAL_NOT_REVERSIBLE', 'This renewal was already reversed.');
    await recordAudit(
      {
        gymId: gym.id,
        actorUserId: auth.userId,
        actorName: auth.fullName,
        actorRole: auth.role,
        action: AUDIT_ACTIONS.MEMBERSHIP_RENEWAL_REVERSED,
        entityType: 'membership_renewal',
        entityId: renewal.id,
        metadata: {
          memberId: renewal.member_id,
          newStartDate: renewal.new_start_date,
          newEndDate: renewal.new_end_date,
          restoredStartDate: renewal.previous_start_date,
          restoredEndDate: renewal.previous_end_date,
          secondsAfterRenewal: Math.floor((now.getTime() - renewal.performed_at.getTime()) / 1000),
        },
        ip: meta.ip ?? null,
        userAgent: meta.userAgent ?? null,
      },
      client,
    );
  });

  const restored = await getCurrentMembership(renewal.member_id);
  if (!restored) throw AppError.internal('The previous membership could not be restored.');

  return {
    memberId: renewal.member_id,
    membership: toMembershipDto(restored, gym, today),
    renewalId: renewal.id,
    reversedAt: now.toISOString(),
  };
}

export interface MembershipHistoryItemDto {
  id: string;
  planName: string;
  startDate: string;
  endDate: string;
  status: string;
  days: number;
  renewalId: string | null;
}

export async function membershipHistory(
  auth: AuthContext,
  memberId: string,
): Promise<MembershipHistoryItemDto[]> {
  const gym = await requireGym(auth.gymId);
  const today = todayIn(gym.timezone);
  const member = await getMember(gym.id, memberId, today);
  if (!member) throw AppError.notFound('Member');

  const [rows, renewals] = await Promise.all([listMembershipHistory(memberId), listRenewals(memberId, 50)]);

  return rows.map((row) => ({
    id: row.id,
    planName: row.plan_name,
    startDate: row.start_date,
    endDate: row.end_date,
    status: row.status,
    days: diffDays(row.start_date, row.end_date) + 1,
    renewalId: renewals.find((renewal) => renewal.new_membership_id === row.id)?.id ?? null,
  }));
}

export async function plansForGym(gymId: string) {
  return listPlans(gymId);
}
