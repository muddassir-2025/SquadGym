import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, body, meta, page, params, query } from './helpers';
import {
  activeGymSessions,
  attendanceDashboard,
  checkOutMember,
  correctAttendance,
  markPresent,
  undoAttendance,
} from '../services/attendanceService';
import type { AttendanceQuery } from '../validators/schemas';

export async function attendanceDashboardHandler(req: Request, res: Response): Promise<void> {
  const input = query<AttendanceQuery>(req);
  const result = await attendanceDashboard(
    auth(req),
    {
      date: input.date,
      tab: input.tab,
      search: input.search,
      shiftId: input.shiftId,
      gender: input.gender,
      planId: input.planId,
    },
    page(req),
  );
  res.status(200).json(ok(result));
}

export async function markPresentHandler(req: Request, res: Response): Promise<void> {
  const input = body<{ memberId: string; date?: string }>(req);
  const result = await markPresent(auth(req), input, meta(req));
  res.status(201).json(ok(result));
}

export async function undoAttendanceHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const result = await undoAttendance(auth(req), id, meta(req));
  res.status(200).json(ok(result));
}

export async function correctAttendanceHandler(req: Request, res: Response): Promise<void> {
  const input = body<{ memberId: string; date: string; action: 'mark_present' | 'remove'; reason: string }>(req);
  const result = await correctAttendance(auth(req), input, meta(req));
  res.status(200).json(ok(result));
}

export async function activeSessionsHandler(req: Request, res: Response): Promise<void> {
  const result = await activeGymSessions(auth(req).gymId);
  res.status(200).json(ok(result));
}

export async function checkoutHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const result = await checkOutMember(auth(req), id, meta(req));
  res.status(200).json(ok(result));
}
