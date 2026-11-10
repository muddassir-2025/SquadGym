import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, query } from './helpers';
import { memberCalendar, memberDashboard, myAttendanceHistory, myMembership, myProfile } from '../services/meService';
import { addDays, todayIn } from '../utils/datetime';
import { requireGym } from '../repositories/gymRepo';

export async function memberDashboardHandler(req: Request, res: Response): Promise<void> {
  const { month } = query<{ month?: string }>(req);
  res.status(200).json(ok(await memberDashboard(auth(req), month)));
}

export async function memberCalendarHandler(req: Request, res: Response): Promise<void> {
  const { month } = query<{ month: string }>(req);
  res.status(200).json(ok(await memberCalendar(auth(req), month)));
}

export async function myProfileHandler(req: Request, res: Response): Promise<void> {
  res.status(200).json(ok(await myProfile(auth(req))));
}

export async function myMembershipHandler(req: Request, res: Response): Promise<void> {
  res.status(200).json(ok({ membership: await myMembership(auth(req)) }));
}

export async function myHistoryHandler(req: Request, res: Response): Promise<void> {
  const input = query<{ from?: string; to?: string }>(req);
  const gym = await requireGym(auth(req).gymId);
  const today = todayIn(gym.timezone);
  const from = input.from ?? addDays(today, -30);
  const to = input.to ?? today;
  res.status(200).json(ok(await myAttendanceHistory(auth(req), from, to)));
}
