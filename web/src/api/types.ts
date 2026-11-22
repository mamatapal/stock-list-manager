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

export type PurchaseStatus = 'DRAFT' | 'PENDING' | 'PARTIAL' | 'FULL' | 'CANCELLED'
export interface PurchaseLine {
  id: string; itemId: string; itemName: string; unitId: string; unitCode: string; quantity: string
  priceBasis: 'PER_UNIT' | 'TOTAL'; purchasePrice: string | null; purchaseUnitPrice: string | null; sellingPrice: string | null
  totalCost: string | null; expectedSales: string | null; expectedProfit: string | null; marginPercent: string | null
  markupPercent: string | null; sellingBelowCost: boolean; stockListItemId: string | null; updateCatalog: boolean
  sortOrder: number; receivedQuantity: string | null; remainingQuantity: string | null; overBy: string | null; receivingStatus: string
}
export interface DeliveryLine { id: string; purchaseLineId: string; kind: 'RECEIPT' | 'CORRECTION'; quantity: string; correctsId: string | null; reason: string | null; batchNo: string | null; expiryDate: string | null }
export interface Delivery { id: string; receivedDate: string; notes: string | null; lines: DeliveryLine[] }
export interface Purchase {
  id: string; supplierId: string; supplierName: string; invoiceNo: string | null; purchaseDate: string; receivedDate: string | null
  stockListId: string | null; notes: string | null; status: PurchaseStatus; overReceived: boolean; duplicateInvoice: boolean
  itemCount: number; totalCost: string | null; totalExpectedSales: string | null; totalExpectedProfit: string | null
  marginPercent: string | null; cancelReason: string | null; lines: PurchaseLine[]; deliveries: Delivery[]; version: number
}
export interface Batch {
  id: string; itemId: string; itemName: string; purchaseId: string; supplierName: string | null; invoiceNo: string | null
  batchNo: string | null; quantity: string; unitCode: string; expiryDate: string | null; daysToExpiry: number | null
  expiryLabel: string | null; cancelled: boolean
}
export interface ExpiryAlert { today: string; expired: number; within7: number; within30: number }
