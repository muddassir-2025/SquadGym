import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requirePermission } from '../middleware/auth';
import { apiLimiter, writeLimiter } from '../middleware/rateLimit';
import {
  chartQuerySchema,
  idParams,
  leaderboardQuerySchema,
  occupancyQuerySchema,
  planBodySchema,
  planPatchSchema,
} from '../validators/schemas';
import {
  chartHandler,
  dashboardHandler,
  leaderboardHandler,
  occupancyHandler,
  plansHandler,
} from '../controllers/dashboardController';
import { createPlanHandler, removePlanHandler, updatePlanHandler } from '../controllers/adminController';

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

// Membership plan upkeep is available to any staff member who can manage memberships,
// not just Super Admins. Removal deactivates the plan so history stays intact.
plansRouter.post(
  '/',
  requirePermission('memberships.manage'),
  writeLimiter,
  validate(planBodySchema, 'body'),
  asyncHandler(createPlanHandler),
);
plansRouter.patch(
  '/:id',
  requirePermission('memberships.manage'),
  writeLimiter,
  validate(idParams, 'params'),
  validate(planPatchSchema, 'body'),
  asyncHandler(updatePlanHandler),
);
plansRouter.delete(
  '/:id',
  requirePermission('memberships.manage'),
  writeLimiter,
  validate(idParams, 'params'),
  asyncHandler(removePlanHandler),
);
