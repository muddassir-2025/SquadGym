import { Router } from 'express';
import multer from 'multer';
import { env } from '../config/env';
import { asyncHandler } from '../utils/asyncHandler';
import { validate } from '../middleware/validate';
import { requireAuth, requirePermission } from '../middleware/auth';
import { apiLimiter, writeLimiter } from '../middleware/rateLimit';
import {
  archiveMemberSchema,
  createMemberSchema,
  idParams,
  listMembersQuerySchema,
  renewSchema,
  renewalPreviewQuerySchema,
  updateMemberSchema,
} from '../validators/schemas';
import {
  archiveMemberHandler,
  createMemberHandler,
  getMemberHandler,
  listMembersHandler,
  membershipHistoryHandler,
  renewMembershipHandler,
  renewalPreviewHandler,
  restoreMemberHandler,
  updateMemberHandler,
  uploadPhotoHandler,
} from '../controllers/memberController';
import { ALLOWED_IMAGE_TYPES } from '../services/storage';

const upload = multer({
  storage: multer.memoryStorage(),
  limits: { fileSize: env.MAX_UPLOAD_BYTES, files: 1 },
  fileFilter: (_req, file, callback) => {
    if (!ALLOWED_IMAGE_TYPES.includes(file.mimetype)) {
      callback(new Error('Only JPEG, PNG or WebP images are allowed.'));
      return;
    }
    callback(null, true);
  },
});

export const membersRouter = Router();

membersRouter.use(requireAuth, apiLimiter);

membersRouter.get(
  '/',
  requirePermission('members.view'),
  validate(listMembersQuerySchema, 'query'),
  asyncHandler(listMembersHandler),
);

membersRouter.post(
  '/',
  requirePermission('members.manage'),
  writeLimiter,
  validate(createMemberSchema, 'body'),
  asyncHandler(createMemberHandler),
);

membersRouter.get(
  '/:id',
  requirePermission('members.view'),
  validate(idParams, 'params'),
  asyncHandler(getMemberHandler),
);

membersRouter.patch(
  '/:id',
  requirePermission('members.manage'),
  validate(idParams, 'params'),
  validate(updateMemberSchema, 'body'),
  asyncHandler(updateMemberHandler),
);

membersRouter.post(
  '/:id/archive',
  requirePermission('members.manage'),
  validate(idParams, 'params'),
  validate(archiveMemberSchema, 'body'),
  asyncHandler(archiveMemberHandler),
);

membersRouter.post(
  '/:id/restore',
  requirePermission('members.manage'),
  validate(idParams, 'params'),
  asyncHandler(restoreMemberHandler),
);

membersRouter.post(
  '/:id/photo',
  requirePermission('members.manage'),
  validate(idParams, 'params'),
  upload.single('photo'),
  asyncHandler(uploadPhotoHandler),
);

membersRouter.get(
  '/:id/memberships',
  requirePermission('memberships.view'),
  validate(idParams, 'params'),
  asyncHandler(membershipHistoryHandler),
);

membersRouter.get(
  '/:id/renewal-preview',
  requirePermission('memberships.manage'),
  validate(idParams, 'params'),
  validate(renewalPreviewQuerySchema, 'query'),
  asyncHandler(renewalPreviewHandler),
);

membersRouter.post(
  '/:id/renewals',
  requirePermission('memberships.manage'),
  writeLimiter,
  validate(idParams, 'params'),
  validate(renewSchema, 'body'),
  asyncHandler(renewMembershipHandler),
);
