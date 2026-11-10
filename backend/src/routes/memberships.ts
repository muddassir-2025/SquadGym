import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requirePermission } from '../middleware/auth';
import { apiLimiter } from '../middleware/rateLimit';
import { idParams } from '../validators/schemas';
import { undoRenewalHandler } from '../controllers/membershipController';

export const membershipsRouter = Router();

membershipsRouter.use(requireAuth, apiLimiter);

membershipsRouter.post(
  '/renewals/:id/undo',
  requirePermission('memberships.manage'),
  validate(idParams, 'params'),
  asyncHandler(undoRenewalHandler),
);
