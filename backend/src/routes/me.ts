import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requireMember } from '../middleware/auth';
import { apiLimiter } from '../middleware/rateLimit';
import { calendarQuerySchema, memberDashboardQuerySchema, myHistoryQuerySchema } from '../validators/schemas';
import {
  memberCalendarHandler,
  memberDashboardHandler,
  myHistoryHandler,
  myMembershipHandler,
  myProfileHandler,
} from '../controllers/meController';

export const meRouter = Router();

meRouter.use(requireAuth, requireMember(), apiLimiter);

meRouter.get(
  '/dashboard',
  validate(memberDashboardQuerySchema, 'query'),
  asyncHandler(memberDashboardHandler),
);
meRouter.get('/calendar', validate(calendarQuerySchema, 'query'), asyncHandler(memberCalendarHandler));
meRouter.get('/profile', asyncHandler(myProfileHandler));
meRouter.get('/membership', asyncHandler(myMembershipHandler));
meRouter.get('/attendance', validate(myHistoryQuerySchema, 'query'), asyncHandler(myHistoryHandler));
