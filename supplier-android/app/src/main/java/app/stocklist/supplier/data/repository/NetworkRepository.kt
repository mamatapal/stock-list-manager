package app.stocklist.supplier.data.repository

import app.stocklist.supplier.data.remote.ApiResult
import app.stocklist.supplier.data.remote.ConnectionDto
import app.stocklist.supplier.data.remote.ListingDto
import app.stocklist.supplier.data.remote.ListingRequest
import app.stocklist.supplier.data.remote.NetworkApi
import app.stocklist.supplier.data.remote.OrderDto
import app.stocklist.supplier.data.remote.ProductDto
import app.stocklist.supplier.data.remote.ProductRequest
import app.stocklist.supplier.data.remote.ReasonRequest
import app.stocklist.supplier.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supplier Network: directory listing, shop connections, the supplier's catalogue and incoming purchase orders.
 * Every call goes to the server and its answer is the only record: nothing is stored or queued on the phone, so a
 * change is shown only once the server has confirmed it. Without internet a call fails with a retryable error.
 * The supplier never creates connections (shops ask) and never touches a shop's stock (the shop receives the goods).
 */
@Singleton
class NetworkRepository @Inject constructor(private val api: NetworkApi) {

    suspend fun listing(): ApiResult<ListingDto> = apiCall { api.listing() }

    suspend fun setListing(listed: Boolean): ApiResult<ListingDto> = apiCall { api.setListing(ListingRequest(listed)) }

    suspend fun connections(): ApiResult<List<ConnectionDto>> = apiCall { api.connections() }

    suspend fun connection(id: String): ApiResult<ConnectionDto> = apiCall { api.connection(id) }

    suspend fun acceptConnection(id: String): ApiResult<ConnectionDto> = apiCall { api.acceptConnection(id) }

    suspend fun declineConnection(id: String, reason: String?): ApiResult<ConnectionDto> =
        apiCall { api.declineConnection(id, ReasonRequest(clean(reason))) }

    suspend fun endConnection(id: String, reason: String?): ApiResult<ConnectionDto> =
        apiCall { api.endConnection(id, ReasonRequest(clean(reason))) }

    suspend fun products(): ApiResult<List<ProductDto>> = apiCall { api.products() }

    /** Saves a new product (id null) or changes an existing one. */
    suspend fun saveProduct(id: String?, request: ProductRequest): ApiResult<ProductDto> = apiCall {
        if (id == null) api.createProduct(request) else api.updateProduct(id, request)
    }

    suspend fun setProductActive(id: String, active: Boolean): ApiResult<ProductDto> = apiCall {
        if (active) api.activateProduct(id) else api.deactivateProduct(id)
    }

    suspend fun orders(): ApiResult<List<OrderDto>> = apiCall { api.orders() }

    suspend fun order(id: String): ApiResult<OrderDto> = apiCall { api.order(id) }

    suspend fun acceptOrder(id: String): ApiResult<OrderDto> = apiCall { api.acceptOrder(id) }

    suspend fun rejectOrder(id: String, reason: String): ApiResult<OrderDto> =
        apiCall { api.rejectOrder(id, ReasonRequest(clean(reason))) }

    suspend fun dispatchOrder(id: String, note: String?): ApiResult<OrderDto> =
        apiCall { api.dispatchOrder(id, ReasonRequest(clean(note))) }

    private fun clean(text: String?): String? = text?.trim()?.takeIf { it.isNotEmpty() }
}
