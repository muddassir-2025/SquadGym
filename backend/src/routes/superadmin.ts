import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requireRole } from '../middleware/auth';
import { apiLimiter, writeLimiter } from '../middleware/rateLimit';
import {
  adminStatusSchema,
  auditQuerySchema,
  createAdminSchema,
  gymSettingsSchema,
  idParams,
  operatingDaysSchema,
  planBodySchema,
  planPatchSchema,
  shiftBodySchema,
  shiftPatchSchema,
  updateAdminSchema,
} from '../validators/schemas';
import {
  auditLogsHandler,
  createAdminHandler,
  createPlanHandler,
  createShiftHandler,
  gymConfigurationHandler,
  listAdminsHandler,
  setAdminStatusHandler,
  updateAdminHandler,
  updateGymSettingsHandler,
  updateOperatingDaysHandler,
  updatePlanHandler,
  updateShiftHandler,
} from '../controllers/adminController';

/** Everything here is Super Admin only, enforced by middleware. */
export const superAdminRouter = Router();

superAdminRouter.use(requireAuth, requireRole('super_admin'), apiLimiter);

superAdminRouter.get('/admins', asyncHandler(listAdminsHandler));
superAdminRouter.post('/admins', writeLimiter, validate(createAdminSchema, 'body'), asyncHandler(createAdminHandler));
superAdminRouter.patch(
  '/admins/:id',
  validate(idParams, 'params'),
  validate(updateAdminSchema, 'body'),
  asyncHandler(updateAdminHandler),
);
superAdminRouter.post(
  '/admins/:id/status',
  validate(idParams, 'params'),
  validate(adminStatusSchema, 'body'),
  asyncHandler(setAdminStatusHandler),
);

superAdminRouter.get('/audit-logs', validate(auditQuerySchema, 'query'), asyncHandler(auditLogsHandler));

superAdminRouter.get('/gym', asyncHandler(gymConfigurationHandler));
superAdminRouter.patch(
  '/gym/settings',
  validate(gymSettingsSchema, 'body'),
  asyncHandler(updateGymSettingsHandler),
);
superAdminRouter.put(
  '/gym/operating-days',
  validate(operatingDaysSchema, 'body'),
  asyncHandler(updateOperatingDaysHandler),
);
superAdminRouter.post(
  '/gym/shifts',
  validate(shiftBodySchema, 'body'),
  asyncHandler(createShiftHandler),
);
superAdminRouter.patch(
  '/gym/shifts/:id',
  validate(idParams, 'params'),
  validate(shiftPatchSchema, 'body'),
  asyncHandler(updateShiftHandler),
);

superAdminRouter.post('/plans', validate(planBodySchema, 'body'), asyncHandler(createPlanHandler));
superAdminRouter.patch(
  '/plans/:id',
  validate(idParams, 'params'),
  validate(planPatchSchema, 'body'),
  asyncHandler(updatePlanHandler),
);
