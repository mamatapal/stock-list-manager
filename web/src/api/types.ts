export interface Supplier {
  id: string; name: string; contactPerson: string | null; mobile: string | null; altPhone: string | null
  address: string | null; city: string | null; gstin: string | null; notes: string | null
  active: boolean; version: number; deleted: boolean
}
export interface Unit { id: string; code: string; name: string; builtIn: boolean; version: number; deleted: boolean }
export interface Item {
  id: string; name: string; category: string | null; defaultUnitId: string
  latestPurchasePrice: string | null; latestSellingPrice: string | null; lastPurchaseDate: string | null
  preferredSupplierId: string | null; notes: string | null; active: boolean; version: number; deleted: boolean
}
export interface ListLine {
  id: string; itemId: string | null; itemName: string | null; description: string | null; quantity: string; unitId: string
  priceBasis: 'PER_UNIT' | 'TOTAL'; enteredPrice: string | null; lineTotal: string | null; effectiveUnitPrice: string | null
  sortOrder: number; receivedQuantity: string | null; remainingQuantity: string | null; overBy: string | null
  receivingStatus: string; unitMismatch: boolean
}
export interface StockList {
  id: string; title: string; listDate: string; supplierId: string | null; notes: string | null
  status: 'DRAFT' | 'SAVED'; receivingStatus: string; totalAmount: string | null; lines: ListLine[]; version: number; deleted: boolean
}
