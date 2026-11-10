import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, body, params, meta } from './helpers';
import {
  acceptInvitationAndStartSession,
  getSessionProfile,
  login,
  logout,
  previewInvitation,
  refreshSession,
  requestPasswordReset,
  resetPassword,
} from '../services/authService';
import type { z } from 'zod';
import type {
  acceptInvitationSchema,
  loginSchema,
  logoutSchema,
  refreshSchema,
  resetPasswordSchema,
} from '../validators/schemas';

export async function loginHandler(req: Request, res: Response): Promise<void> {
  const input = body<z.infer<typeof loginSchema>>(req);
  const session = await login(input, meta(req));
  res.status(200).json(ok(session));
}

export async function refreshHandler(req: Request, res: Response): Promise<void> {
  const input = body<z.infer<typeof refreshSchema>>(req);
  const session = await refreshSession(input.refreshToken, meta(req));
  res.status(200).json(ok(session));
}

export async function logoutHandler(req: Request, res: Response): Promise<void> {
  const input = body<z.infer<typeof logoutSchema>>(req);
  await logout(input.refreshToken, meta(req), req.auth?.userId);
  res.status(200).json(ok({ signedOut: true }));
}

export async function meHandler(req: Request, res: Response): Promise<void> {
  const context = auth(req);
  const profile = await getSessionProfile(context.userId);
  res.status(200).json(ok(profile));
}

export async function forgotPasswordHandler(req: Request, res: Response): Promise<void> {
  const input = body<{ email: string }>(req);
  const result = await requestPasswordReset(input.email);
  res.status(202).json(
    ok({
      accepted: true,
      message: 'If that email belongs to an account, a reset code has been sent.',
      ...(result.devToken ? { devToken: result.devToken } : {}),
    }),
  );
}

export async function resetPasswordHandler(req: Request, res: Response): Promise<void> {
  const input = body<z.infer<typeof resetPasswordSchema>>(req);
  await resetPassword({ token: input.token, newPassword: input.newPassword, meta: meta(req) });
  res.status(200).json(ok({ reset: true }));
}

export async function previewInvitationHandler(req: Request, res: Response): Promise<void> {
  const { token } = params<{ token: string }>(req);
  const preview = await previewInvitation(token);
  res.status(200).json(ok(preview));
}

export async function acceptInvitationHandler(req: Request, res: Response): Promise<void> {
  const input = body<z.infer<typeof acceptInvitationSchema>>(req);
  const session = await acceptInvitationAndStartSession({ ...input, meta: meta(req) });
  res.status(200).json(ok(session));
}
