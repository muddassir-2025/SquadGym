import type { Request, Response } from 'express';
import { ok } from '../utils/errors';
import { auth, body, page, params, query } from './helpers';
import {
  getMemberDetail,
  listMembers,
  createMember,
  updateMember,
  archiveMember,
  restoreMember,
  uploadMemberPhoto,
} from '../services/memberService';
import { membershipHistory, previewRenewal, renewMembership } from '../services/membershipService';
import type { MemberListFilter } from '../repositories/userRepo';
import type {
  CreateMemberBody,
  ListMembersQuery,
  UpdateMemberBody,
} from '../validators/schemas';
import { meta } from './helpers';

export async function listMembersHandler(req: Request, res: Response): Promise<void> {
  const queryInput = query<ListMembersQuery>(req);
  const filter: MemberListFilter = {
    tab: queryInput.tab,
    search: queryInput.search,
    gender: queryInput.gender,
    planId: queryInput.planId,
    shiftId: queryInput.shiftId,
    status: queryInput.status,
    minScore: queryInput.minScore,
    maxScore: queryInput.maxScore,
    expiresFrom: queryInput.expiresFrom,
    expiresTo: queryInput.expiresTo,
    joinedFrom: queryInput.joinedFrom,
    joinedTo: queryInput.joinedTo,
    sort: queryInput.sort,
  };
  const result = await listMembers(auth(req), filter, page(req));
  res.status(200).json(ok(result));
}

export async function getMemberHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const detail = await getMemberDetail(auth(req), id);
  res.status(200).json(ok(detail));
}

export async function createMemberHandler(req: Request, res: Response): Promise<void> {
  const input = body<CreateMemberBody>(req);
  const result = await createMember(auth(req), input);
  res.status(201).json(ok(result));
}

export async function updateMemberHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<UpdateMemberBody>(req);
  const member = await updateMember(auth(req), id, input, meta(req));
  res.status(200).json(ok(member));
}

export async function archiveMemberHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<{ reason?: string }>(req);
  await archiveMember(auth(req), id, input.reason ?? null, meta(req));
  res.status(200).json(ok({ archived: true, memberId: id }));
}

export async function restoreMemberHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  await restoreMember(auth(req), id, meta(req));
  res.status(200).json(ok({ archived: false, memberId: id }));
}

export async function uploadPhotoHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  if (!req.file) {
    res.status(422).json({
      success: false,
      error: { code: 'VALIDATION_ERROR', message: 'Attach an image file.', requestId: req.requestId },
    });
    return;
  }
  const result = await uploadMemberPhoto(
    auth(req),
    id,
    { buffer: req.file.buffer, mimetype: req.file.mimetype, size: req.file.size },
    meta(req),
  );
  res.status(200).json(ok(result));
}

export async function membershipHistoryHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const history = await membershipHistory(auth(req), id);
  res.status(200).json(ok({ items: history }));
}

export async function renewalPreviewHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const { planId } = query<{ planId: string }>(req);
  const preview = await previewRenewal(auth(req), id, planId);
  res.status(200).json(ok(preview));
}

export async function renewMembershipHandler(req: Request, res: Response): Promise<void> {
  const { id } = params<{ id: string }>(req);
  const input = body<{ planId: string }>(req);
  const result = await renewMembership(auth(req), id, input.planId, meta(req));
  res.status(201).json(ok(result));
}
