import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth } from '../middleware/auth';
import { authLimiter } from '../middleware/rateLimit';
import {
  acceptInvitationSchema,
  forgotPasswordSchema,
  invitationTokenParams,
  loginSchema,
  logoutSchema,
  refreshSchema,
  resetPasswordSchema,
} from '../validators/schemas';
import {
  acceptInvitationHandler,
  forgotPasswordHandler,
  loginHandler,
  logoutHandler,
  meHandler,
  previewInvitationHandler,
  refreshHandler,
  resetPasswordHandler,
} from '../controllers/authController';

export const authRouter = Router();

authRouter.post('/login', authLimiter, validate(loginSchema, 'body'), asyncHandler(loginHandler));
authRouter.post('/refresh', authLimiter, validate(refreshSchema, 'body'), asyncHandler(refreshHandler));
authRouter.post('/logout', validate(logoutSchema, 'body'), asyncHandler(logoutHandler));
authRouter.get('/me', requireAuth, asyncHandler(meHandler));
authRouter.post(
  '/forgot-password',
  authLimiter,
  validate(forgotPasswordSchema, 'body'),
  asyncHandler(forgotPasswordHandler),
);
authRouter.post(
  '/reset-password',
  authLimiter,
  validate(resetPasswordSchema, 'body'),
  asyncHandler(resetPasswordHandler),
);

export const invitationRouter = Router();
invitationRouter.get(
  '/:token',
  authLimiter,
  validate(invitationTokenParams, 'params'),
  asyncHandler(previewInvitationHandler),
);
invitationRouter.post(
  '/accept',
  authLimiter,
  validate(acceptInvitationSchema, 'body'),
  asyncHandler(acceptInvitationHandler),
);
