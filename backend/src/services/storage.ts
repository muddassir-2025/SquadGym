import { createReadStream } from 'node:fs';
import { mkdir, readFile, unlink, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { Readable } from 'node:stream';
import { GetObjectCommand, PutObjectCommand, S3Client } from '@aws-sdk/client-s3';
import { env } from '../config/env';
import { logger } from '../utils/logger';

/**
 * Private media storage.
 *
 * Two drivers behind one interface:
 *  * `local` — files under `LOCAL_UPLOAD_DIR` (development, zero setup);
 *  * `s3`    — any S3-compatible bucket (Neon Object Storage / R2 / MinIO / S3).
 *
 * Files are always served through the authenticated `/media/:token` endpoint, so
 * the bucket can stay private and a leaked key is useless without a session.
 */

export interface StorageDriver {
  readonly name: 'local' | 's3';
  put(key: string, body: Buffer, contentType: string): Promise<void>;
  get(key: string): Promise<{ body: Readable; contentType: string; contentLength?: number }>;
  remove(key: string): Promise<void>;
}

const KEY_PATTERN = /^[a-zA-Z0-9][a-zA-Z0-9/_.-]{0,300}$/;

export function assertSafeKey(key: string): string {
  if (!KEY_PATTERN.test(key) || key.includes('..') || key.startsWith('/')) {
    throw new Error('Unsafe storage key');
  }
  return key;
}

class LocalStorage implements StorageDriver {
  readonly name = 'local' as const;
  private readonly root = resolve(process.cwd(), env.LOCAL_UPLOAD_DIR);

  private pathFor(key: string): string {
    assertSafeKey(key);
    const full = join(this.root, key);
    if (!full.startsWith(this.root)) throw new Error('Unsafe storage path');
    return full;
  }

  async put(key: string, body: Buffer): Promise<void> {
    const path = this.pathFor(key);
    await mkdir(dirname(path), { recursive: true });
    await writeFile(path, body);
  }

  async get(key: string): Promise<{ body: Readable; contentType: string }> {
    const path = this.pathFor(key);
    const data = await readFile(path);
    return { body: Readable.from(data), contentType: contentTypeForKey(key) };
  }

  async remove(key: string): Promise<void> {
    try {
      await unlink(this.pathFor(key));
    } catch {
      /* already gone */
    }
  }
}

class S3Storage implements StorageDriver {
  readonly name = 's3' as const;
  private readonly client: S3Client;
  private readonly bucket: string;

  constructor() {
    if (!env.S3_BUCKET) throw new Error('S3_BUCKET is required when STORAGE_DRIVER=s3');
    this.bucket = env.S3_BUCKET;
    this.client = new S3Client({
      region: env.S3_REGION,
      ...(env.S3_ENDPOINT ? { endpoint: env.S3_ENDPOINT } : {}),
      forcePathStyle: env.S3_FORCE_PATH_STYLE,
      ...(env.S3_ACCESS_KEY_ID && env.S3_SECRET_ACCESS_KEY
        ? { credentials: { accessKeyId: env.S3_ACCESS_KEY_ID, secretAccessKey: env.S3_SECRET_ACCESS_KEY } }
        : {}),
    });
  }

  async put(key: string, body: Buffer, contentType: string): Promise<void> {
    await this.client.send(
      new PutObjectCommand({
        Bucket: this.bucket,
        Key: assertSafeKey(key),
        Body: body,
        ContentType: contentType,
        CacheControl: 'private, max-age=31536000, immutable',
      }),
    );
  }

  async get(key: string): Promise<{ body: Readable; contentType: string; contentLength?: number }> {
    const response = await this.client.send(
      new GetObjectCommand({ Bucket: this.bucket, Key: assertSafeKey(key) }),
    );
    if (!response.Body) throw new Error('Empty object body');
    return {
      body: response.Body as Readable,
      contentType: response.ContentType ?? contentTypeForKey(key),
      contentLength: response.ContentLength,
    };
  }

  async remove(key: string): Promise<void> {
    try {
      const { DeleteObjectCommand } = await import('@aws-sdk/client-s3');
      await this.client.send(new DeleteObjectCommand({ Bucket: this.bucket, Key: assertSafeKey(key) }));
    } catch (error) {
      logger.warn('Failed to delete media object', { key, message: (error as Error).message });
    }
  }
}

const CONTENT_TYPES: Record<string, string> = {
  jpg: 'image/jpeg',
  jpeg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
};

export function contentTypeForKey(key: string): string {
  const extension = key.split('.').pop()?.toLowerCase() ?? '';
  return CONTENT_TYPES[extension] ?? 'application/octet-stream';
}

export function extensionForMimeType(mimeType: string): string {
  switch (mimeType) {
    case 'image/png':
      return 'png';
    case 'image/webp':
      return 'webp';
    case 'image/jpeg':
    case 'image/jpg':
    default:
      return 'jpg';
  }
}

let cached: StorageDriver | null = null;

export function storage(): StorageDriver {
  if (!cached) {
    cached = env.STORAGE_DRIVER === 's3' ? new S3Storage() : new LocalStorage();
    logger.info('Storage driver ready', { driver: cached.name });
  }
  return cached;
}

export function memberPhotoKey(gymId: string, memberId: string, mimeType: string): string {
  const extension = extensionForMimeType(mimeType);
  return `gym/${gymId}/members/${memberId}/${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}.${extension}`;
}

export const ALLOWED_IMAGE_TYPES = ['image/jpeg', 'image/jpg', 'image/png', 'image/webp'];
