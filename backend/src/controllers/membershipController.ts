import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, meta, params } from './helpers';
import { undoRenewal } from '../services/membershipService';

export async function undoRenewalHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const result = await undoRenewal(auth(req), id, meta(req));
  res.status(200).json(ok(result));
}
