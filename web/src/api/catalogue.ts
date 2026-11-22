import { api, request } from './client'
import type { Batch, ExpiryAlert, Item, Purchase, StockList, Supplier, Unit } from './types'

const qs = (params: Record<string, string | boolean | undefined>) => {
  const p = new URLSearchParams()
  for (const [k, v] of Object.entries(params)) if (v !== undefined && v !== '' && v !== false) p.set(k, String(v))
  const s = p.toString()
  return s ? `?${s}` : ''
}

export const suppliers = {
  list: (q?: string) => api.get<Supplier[]>('/api/v1/suppliers' + qs({ q })),
  get: (id: string) => api.get<Supplier>(`/api/v1/suppliers/${id}`),
  create: (body: unknown) => api.post<Supplier>('/api/v1/suppliers', body),
  update: (id: string, version: number, body: unknown) => api.put<Supplier>(`/api/v1/suppliers/${id}`, body, version),
  remove: (id: string, version: number) => api.delete<Supplier>(`/api/v1/suppliers/${id}`, version),
}

export const units = {
  list: () => api.get<Unit[]>('/api/v1/units'),
  create: (body: { code: string; name: string }) => api.post<Unit>('/api/v1/units', body),
  remove: (id: string, version: number) => api.delete<Unit>(`/api/v1/units/${id}`, version),
}

export const items = {
  list: (q?: string, category?: string) => api.get<Item[]>('/api/v1/items' + qs({ q, category })),
  categories: () => api.get<string[]>('/api/v1/items/categories'),
  get: (id: string) => api.get<Item>(`/api/v1/items/${id}`),
  create: (body: unknown) => api.post<Item>('/api/v1/items', body),
  update: (id: string, version: number, body: unknown) => api.put<Item>(`/api/v1/items/${id}`, body, version),
  remove: (id: string, version: number) => api.delete<Item>(`/api/v1/items/${id}`, version),
}

export const stockLists = {
  list: (q?: string, status?: string) => api.get<StockList[]>('/api/v1/stock-lists' + qs({ q, status })),
  get: (id: string) => api.get<StockList>(`/api/v1/stock-lists/${id}`),
  create: (body: unknown) => api.post<StockList>('/api/v1/stock-lists', body),
  update: (id: string, version: number, body: unknown) => api.put<StockList>(`/api/v1/stock-lists/${id}`, body, version),
  remove: (id: string, version: number) => api.delete<StockList>(`/api/v1/stock-lists/${id}`, version),
  duplicate: (id: string) => api.post<StockList>(`/api/v1/stock-lists/${id}/duplicate`),
}

export const purchases = {
  list: (q?: string, status?: string) => api.get<Purchase[]>('/api/v1/purchases' + qs({ q, status })),
  get: (id: string) => api.get<Purchase>(`/api/v1/purchases/${id}`),
  create: (body: unknown) => api.post<Purchase>('/api/v1/purchases', body),
  update: (id: string, version: number, body: unknown) => api.put<Purchase>(`/api/v1/purchases/${id}`, body, version),
  removeDraft: (id: string, version: number) => api.delete<Purchase>(`/api/v1/purchases/${id}`, version),
  cancel: (id: string, version: number, reason: string) => request<Purchase>('POST', `/api/v1/purchases/${id}/cancel`, { reason }, { headers: { 'If-Match': String(version) } }),
  receive: (id: string, body: unknown) => api.post<Purchase>(`/api/v1/purchases/${id}/deliveries`, body),
}

export const batches = {
  list: (expiry?: string, q?: string) => api.get<Batch[]>('/api/v1/batches' + qs({ expiry, q })),
  alert: () => api.get<ExpiryAlert>('/api/v1/batches/alert'),
}
