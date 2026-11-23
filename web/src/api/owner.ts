import { api } from './client'

export interface StaffMember {
  id: string; username: string; fullName: string; mobile: string | null; status: 'ACTIVE' | 'INACTIVE' | string
  mustChangePassword: boolean; createdAt: string | null; lastLoginAt: string | null
}
export interface CreatedStaff { staff: StaffMember; permissions: string[]; temporaryPassword: string }
export interface StaffSession { familyId: string; deviceId: string; deviceName: string | null; signedInAt: string; expiresAt: string }
export interface Business {
  id: string; name: string; address: string; city: string; state: string; pinCode: string; phone: string; email: string
  gstin: string | null; hasLogo: boolean; version: number; updatedAt: string | null
}
export interface ActivityEntry { id: number; userId: string | null; username: string | null; action: string; recordType: string | null; recordId: string | null; details: string | null; deviceId: string | null; createdAt: string }
export interface ActivityPage { items: ActivityEntry[]; page: number; size: number; total: number }

/** The permission codes this screen can show: the first release of the server catalogue. Others are kept as they are. */
export const KNOWN_PERMISSIONS = [
  'SUPPLIER_VIEW', 'SUPPLIER_ADD', 'SUPPLIER_EDIT', 'SUPPLIER_DELETE', 'ITEM_VIEW', 'ITEM_ADD', 'ITEM_EDIT', 'ITEM_DELETE',
  'LIST_CREATE', 'LIST_EDIT', 'LIST_DELETE', 'PURCHASE_RECORD', 'PURCHASE_EDIT', 'PURCHASE_DELETE', 'PURCHASE_PRICE_ENTER',
  'SELLING_PRICE_ENTER', 'CATALOG_SELLING_PRICE_CHANGE', 'PURCHASE_HISTORY_VIEW', 'PDF_GENERATE', 'PDF_SHARE', 'EXPIRY_VIEW',
  'EXPECTED_PROFIT_VIEW', 'PROFIT_REPORT_VIEW', 'SETTINGS_MANAGE', 'STAFF_MANAGE',
] as const

export const staff = {
  list: () => api.get<StaffMember[]>('/api/v1/staff'),
  get: (id: string) => api.get<StaffMember>(`/api/v1/staff/${id}`),
  create: (body: { fullName: string; username: string; mobile: string | null; permissions: string[] }) => api.post<CreatedStaff>('/api/v1/staff', body),
  update: (id: string, body: { fullName: string; mobile: string | null }) => api.put<StaffMember>(`/api/v1/staff/${id}`, body),
  setActive: (id: string, active: boolean) => api.post<StaffMember>(`/api/v1/staff/${id}/${active ? 'activate' : 'deactivate'}`),
  resetPassword: (id: string) => api.post<{ temporaryPassword: string }>(`/api/v1/staff/${id}/reset-password`),
  permissions: (id: string) => api.get<{ userId: string; permissions: string[] }>(`/api/v1/staff/${id}/permissions`),
  setPermissions: (id: string, permissions: string[]) =>
    api.put<{ userId: string; permissions: string[] }>(`/api/v1/staff/${id}/permissions`, { permissions, knownPermissions: [...KNOWN_PERMISSIONS] }),
  sessions: (id: string) => api.get<StaffSession[]>(`/api/v1/staff/${id}/sessions`),
  revokeSessions: (id: string) => api.delete<void>(`/api/v1/staff/${id}/sessions`),
}

export const business = {
  get: () => api.get<Business>('/api/v1/business'),
  update: (body: Omit<Business, 'id' | 'hasLogo' | 'version' | 'updatedAt'>) => api.put<Business>('/api/v1/business', body),
}

export const activity = {
  page: (page: number) => api.get<ActivityPage>(`/api/v1/activity?page=${page}&size=50`),
}

export const reauth = (password: string) => api.post<{ stepUpToken: string; expiresAt: string }>('/api/v1/auth/reauth', { password })
