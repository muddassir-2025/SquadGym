import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, page, query } from './helpers';
import { attendanceChart, occupancyHistory, todayDashboard } from '../services/dashboardService';
import { leaderboard } from '../services/leaderboardService';
import { plansForGym } from '../services/membershipService';
import type { ChartQuery, LeaderboardQuery } from '../validators/schemas';

export async function dashboardHandler(req: Request, res: Response): Promise<void> {
  res.status(200).json(ok(await todayDashboard(auth(req))));
}

export async function chartHandler(req: Request, res: Response): Promise<void> {
  const input = query<ChartQuery>(req);
  res.status(200).json(ok(await attendanceChart(auth(req), input)));
}

export async function occupancyHandler(req: Request, res: Response): Promise<void> {
  const input = query<{ date?: string }>(req);
  res.status(200).json(ok(await occupancyHistory(auth(req), input.date)));
}

export async function leaderboardHandler(req: Request, res: Response): Promise<void> {
  const input = query<LeaderboardQuery>(req);
  res.status(200).json(ok(await leaderboard(auth(req), { period: input.period, anchor: input.anchor }, page(req))));
}

export async function plansHandler(req: Request, res: Response): Promise<void> {
  const plans = await plansForGym(auth(req).gymId);
  res.status(200).json(
    ok({
      plans: plans.map((plan) => ({
        id: plan.id,
        name: plan.name,
        durationDays: plan.duration_days,
        priceCents: plan.price_cents,
        currency: plan.currency,
        description: plan.description,
        isActive: plan.is_active,
        sortOrder: plan.sort_order,
      })),
    }),
  );
}
