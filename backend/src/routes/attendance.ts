import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requirePermission } from '../middleware/auth';
import { apiLimiter, writeLimiter } from '../middleware/rateLimit';
import {
  attendanceQuerySchema,
  correctionSchema,
  idParams,
  markAttendanceSchema,
} from '../validators/schemas';
import {
  activeSessionsHandler,
  attendanceDashboardHandler,
  checkoutHandler,
  correctAttendanceHandler,
  markPresentHandler,
  undoAttendanceHandler,
} from '../controllers/attendanceController';

export const attendanceRouter = Router();

attendanceRouter.use(requireAuth, apiLimiter);

attendanceRouter.get(
  '/',
  requirePermission('attendance.view'),
  validate(attendanceQuerySchema, 'query'),
  asyncHandler(attendanceDashboardHandler),
);

attendanceRouter.post(
  '/mark',
  requirePermission('attendance.manage'),
  writeLimiter,
  validate(markAttendanceSchema, 'body'),
  asyncHandler(markPresentHandler),
);

attendanceRouter.post(
  '/corrections',
  requirePermission('attendance.correct'),
  writeLimiter,
  validate(correctionSchema, 'body'),
  asyncHandler(correctAttendanceHandler),
);

attendanceRouter.post(
  '/:id/undo',
  requirePermission('attendance.manage'),
  writeLimiter,
  validate(idParams, 'params'),
  asyncHandler(undoAttendanceHandler),
);

attendanceRouter.get(
  '/sessions/active',
  requirePermission('attendance.view'),
  asyncHandler(activeSessionsHandler),
);

attendanceRouter.post(
  '/sessions/:id/checkout',
  requirePermission('attendance.manage'),
  writeLimiter,
  validate(idParams, 'params'),
  asyncHandler(checkoutHandler),
);
