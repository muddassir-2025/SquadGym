export interface PageRequest {
  page: number;
  pageSize: number;
  offset: number;
}

export const MAX_PAGE_SIZE = 100;
export const DEFAULT_PAGE_SIZE = 20;

function readPositiveInt(value: unknown, fallback: number): number {
  const parsed = Number(value);
  if (!Number.isFinite(parsed) || parsed <= 0) return fallback;
  return Math.floor(parsed);
}

export function parsePagination(
  query: Record<string, unknown>,
  defaultPageSize = DEFAULT_PAGE_SIZE,
): PageRequest {
  const page = Math.min(readPositiveInt(query.page, 1), 100_000);
  const requestedSize = readPositiveInt(query.pageSize, defaultPageSize);
  const pageSize = Math.min(requestedSize, MAX_PAGE_SIZE);
  return { page, pageSize, offset: (page - 1) * pageSize };
}

export interface PaginationMeta {
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
  hasNext: boolean;
  hasPrevious: boolean;
}

export function paginationMeta(total: number, request: PageRequest): PaginationMeta {
  const totalPages = Math.max(1, Math.ceil(total / request.pageSize));
  return {
    page: request.page,
    pageSize: request.pageSize,
    total,
    totalPages,
    hasNext: request.page < totalPages,
    hasPrevious: request.page > 1,
  };
}

/** Percentage change with a safe denominator. `null` when a comparison is meaningless. */
export function percentChange(current: number, previous: number): number | null {
  if (previous === 0) return null;
  return Math.round(((current - previous) / previous) * 1000) / 10;
}
