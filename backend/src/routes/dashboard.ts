import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requirePermission } from '../middleware/auth';
import { apiLimiter } from '../middleware/rateLimit';
import { chartQuerySchema, leaderboardQuerySchema, occupancyQuerySchema } from '../validators/schemas';
import {
  chartHandler,
  dashboardHandler,
  leaderboardHandler,
  occupancyHandler,
  plansHandler,
} from '../controllers/dashboardController';

export const dashboardRouter = Router();

dashboardRouter.use(requireAuth, apiLimiter);

dashboardRouter.get('/today', requirePermission('dashboard.view'), asyncHandler(dashboardHandler));
dashboardRouter.get(
  '/chart',
  requirePermission('dashboard.view'),
  validate(chartQuerySchema, 'query'),
  asyncHandler(chartHandler),
);
dashboardRouter.get(
  '/occupancy',
  requirePermission('dashboard.view'),
  validate(occupancyQuerySchema, 'query'),
  asyncHandler(occupancyHandler),
);

export const leaderboardRouter = Router();
leaderboardRouter.use(requireAuth, apiLimiter);
leaderboardRouter.get(
  '/',
  requirePermission('leaderboard.view'),
  validate(leaderboardQuerySchema, 'query'),
  asyncHandler(leaderboardHandler),
);

export const plansRouter = Router();
plansRouter.use(requireAuth, apiLimiter);
plansRouter.get('/', requirePermission('memberships.view'), asyncHandler(plansHandler));
