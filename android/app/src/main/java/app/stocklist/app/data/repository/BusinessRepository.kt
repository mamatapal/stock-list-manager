package app.stocklist.app.data.repository

import app.stocklist.app.core.session.StepUpCache
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.BusinessDto
import app.stocklist.app.data.remote.UpdateBusinessRequest
import app.stocklist.app.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessRepository @Inject constructor(
    private val api: ApiService,
    private val stepUp: StepUpCache,
) {
    suspend fun get(): ApiResult<BusinessDto> = apiCall { api.business() }

    suspend fun update(body: UpdateBusinessRequest): ApiResult<BusinessDto> =
        withStepUp(stepUp) { token -> api.updateBusiness(token, body) }
}
