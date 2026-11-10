import { Router } from 'express';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth } from '../middleware/auth';
import { mediaParams } from '../validators/schemas';
import { mediaHandler } from '../controllers/mediaController';

export const mediaRouter = Router();

// Requires a valid session: the bucket itself is private.
mediaRouter.get('/:token', requireAuth, validate(mediaParams, 'params'), asyncHandler(mediaHandler));
