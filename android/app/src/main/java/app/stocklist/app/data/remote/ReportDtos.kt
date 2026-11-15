package app.stocklist.app.data.remote

import kotlinx.serialization.Serializable

// Server reports (GET /api/v1/reports/...). Profit fields are null unless the server allowed them.

@Serializable
data class PeriodDto(val from: String? = null, val to: String? = null)

@Serializable
data class PurchaseReportRowDto(
    val purchaseId: String, val purchaseDate: String, val invoiceNo: String? = null, val supplierId: String,
    val supplierName: String? = null, val status: String, val itemCount: Int, val totalCost: String? = null,
    val expectedSales: String? = null, val expectedProfit: String? = null,
)

@Serializable
data class PurchaseReportDto(
    val period: PeriodDto, val purchaseCount: Int, val cancelledCount: Int, val totalCost: String,
    val expectedSales: String? = null, val expectedProfit: String? = null, val profitIncluded: Boolean = false,
    val profitNote: String? = null, val rows: List<PurchaseReportRowDto> = emptyList(),
)

@Serializable
data class SupplierTotalDto(
    val supplierId: String, val supplierName: String? = null, val purchaseCount: Int, val totalCost: String,
    val sharePercent: String? = null, val lastPurchaseDate: String? = null, val expectedProfit: String? = null,
)

@Serializable
data class SupplierTotalsDto(
    val period: PeriodDto, val purchaseCount: Int, val totalCost: String, val profitIncluded: Boolean = false,
    val profitNote: String? = null, val rows: List<SupplierTotalDto> = emptyList(),
)

@Serializable
data class ItemPriceDto(
    val itemId: String, val itemName: String, val unitCode: String, val purchaseCount: Int, val totalQuantity: String,
    val totalCost: String? = null, val lowestUnitPrice: String? = null, val highestUnitPrice: String? = null,
    val averageUnitPrice: String? = null, val latestUnitPrice: String? = null, val latestDate: String? = null,
    val latestSupplierName: String? = null, val latestSellingPrice: String? = null, val expectedProfit: String? = null,
)

@Serializable
data class ItemPricesDto(
    val period: PeriodDto, val profitIncluded: Boolean = false, val profitNote: String? = null,
    val rows: List<ItemPriceDto> = emptyList(),
)

@Serializable
data class ProfitRowDto(
    val id: String, val name: String? = null, val cost: String, val expectedSales: String, val expectedProfit: String,
    val marginPercent: String? = null,
)

@Serializable
data class ExpectedProfitReportDto(
    val period: PeriodDto, val purchaseCount: Int, val totalCost: String, val pricedCost: String,
    val unpricedCost: String, val expectedSales: String, val expectedProfit: String, val marginPercent: String? = null,
    val note: String = "", val bySupplier: List<ProfitRowDto> = emptyList(), val byItem: List<ProfitRowDto> = emptyList(),
)
