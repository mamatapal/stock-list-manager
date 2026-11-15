package app.stocklist.app.data.repository

import app.stocklist.app.data.remote.ActivityPageDto
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.apiCall
import javax.inject.Inject
import javax.inject.Singleton

/** Owner-only audit log (the server refuses everyone else). */
@Singleton
class ActivityRepository @Inject constructor(private val api: ApiService) {
    suspend fun page(page: Int, size: Int = 30): ApiResult<ActivityPageDto> = apiCall { api.activity(page, size) }
}
