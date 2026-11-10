import type { Request, Response } from 'express';
import { decodeMediaKey } from '../services/dto';
import { contentTypeForKey, storage } from '../services/storage';
import { AppError } from '../utils/errors';
import { logger } from '../utils/logger';

/**
 * Authenticated media proxy.
 *
 * Profile pictures live in a private bucket. The Android app loads them through
 * this endpoint with its session token, so no public URL can be guessed.
 * `Cache-Control` is immutable because the URL carries the image version.
 */
export async function mediaHandler(req: Request, res: Response): Promise<void> {
  const raw = req.params.token;
  const token = Array.isArray(raw) ? (raw[0] ?? '') : (raw ?? '');
  let key: string;
  try {
    key = decodeMediaKey(token);
  } catch {
    throw AppError.notFound('Image');
  }
  if (!key.startsWith('gym/')) throw AppError.notFound('Image');

  try {
    const object = await storage().get(key);
    res.setHeader('Content-Type', object.contentType || contentTypeForKey(key));
    res.setHeader('Cache-Control', 'private, max-age=31536000, immutable');
    if (object.contentLength) res.setHeader('Content-Length', String(object.contentLength));
    object.body.on('error', (error: Error) => {
      logger.warn('Media stream failed', { key, message: error.message });
      if (!res.headersSent) res.status(502).end();
      else res.end();
    });
    object.body.pipe(res);
  } catch (error) {
    logger.warn('Media not found', { key, message: (error as Error).message });
    throw AppError.notFound('Image');
  }
}
