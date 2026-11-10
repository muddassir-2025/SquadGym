import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, body, meta, page, params, query } from './helpers';
import {
  auditLogsService,
  createAdminService,
  createPlanService,
  createShiftService,
  gymConfiguration,
  listAdminsService,
  setAdminStatusService,
  updateAdminService,
  updateGymSettingsService,
  updateOperatingDaysService,
  updatePlanService,
  updateShiftService,
} from '../services/adminService';
import type { CreateAdminBody, UpdateAdminBody } from '../validators/schemas';

export async function listAdminsHandler(req: Request, res: Response): Promise<void> {
  res.status(200).json(ok({ admins: await listAdminsService(auth(req)) }));
}

export async function createAdminHandler(req: Request, res: Response): Promise<void> {
  const input = body<CreateAdminBody>(req);
  res.status(201).json(ok(await createAdminService(auth(req), input, meta(req))));
}

export async function updateAdminHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<UpdateAdminBody>(req);
  res.status(200).json(ok(await updateAdminService(auth(req), id, input, meta(req))));
}

export async function setAdminStatusHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<{ status: 'active' | 'inactive' }>(req);
  res.status(200).json(ok(await setAdminStatusService(auth(req), id, input.status, meta(req))));
}

export async function gymConfigurationHandler(req: Request, res: Response): Promise<void> {
  res.status(200).json(ok(await gymConfiguration(auth(req))));
}

export async function updateGymSettingsHandler(req: Request, res: Response): Promise<void> {
  const input = body<Record<string, unknown>>(req);
  res.status(200).json(ok(await updateGymSettingsService(auth(req), input, meta(req))));
}

export async function updateOperatingDaysHandler(req: Request, res: Response): Promise<void> {
  const input = body<{ days: Array<{ dayOfWeek: number; isOpen: boolean; opensAt?: string; closesAt?: string }> }>(req);
  res.status(200).json(ok(await updateOperatingDaysService(auth(req), input.days, meta(req))));
}

export async function createShiftHandler(req: Request, res: Response): Promise<void> {
  const input = body<{ name: string; startTime: string; endTime: string; sortOrder?: number }>(req);
  res.status(201).json(ok(await createShiftService(auth(req), input, meta(req))));
}

export async function updateShiftHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<{ name?: string; startTime?: string; endTime?: string; sortOrder?: number; isActive?: boolean }>(req);
  res.status(200).json(ok(await updateShiftService(auth(req), id, input, meta(req))));
}

export async function createPlanHandler(req: Request, res: Response): Promise<void> {
  const input = body<{
    name: string;
    durationDays: number;
    priceCents?: number;
    currency?: string;
    description?: string | null;
    sortOrder?: number;
  }>(req);
  res.status(201).json(ok(await createPlanService(auth(req), input, meta(req))));
}

export async function updatePlanHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<{
    name?: string;
    durationDays?: number;
    priceCents?: number;
    currency?: string;
    description?: string | null;
    isActive?: boolean;
    sortOrder?: number;
  }>(req);
  res.status(200).json(ok(await updatePlanService(auth(req), id, input, meta(req))));
}

export async function auditLogsHandler(req: Request, res: Response): Promise<void> {
  const input = query<{
    action?: string;
    actorId?: string;
    entityType?: string;
    from?: string;
    to?: string;
    search?: string;
  }>(req);
  res.status(200).json(ok(await auditLogsService(auth(req), input, page(req))));
}
