package app.stocklist.app.data.repository

import app.stocklist.app.core.session.StepUpCache
import app.stocklist.app.data.remote.ApiResult
import app.stocklist.app.data.remote.ApiService
import app.stocklist.app.data.remote.CreateStaffRequest
import app.stocklist.app.data.remote.CreateStaffResponse
import app.stocklist.app.data.remote.PermissionsDto
import app.stocklist.app.data.remote.SessionInfoDto
import app.stocklist.app.data.remote.SetPermissionsRequest
import app.stocklist.app.data.remote.StaffDto
import app.stocklist.app.data.remote.TemporaryPasswordDto
import app.stocklist.app.data.remote.UpdateStaffRequest
import app.stocklist.app.data.remote.apiCall
import app.stocklist.app.domain.permission.Permission
import javax.inject.Inject
import javax.inject.Singleton

/** Staff management is online-only by design (see the plan's offline table). */
@Singleton
class StaffRepository @Inject constructor(
    private val api: ApiService,
    private val stepUp: StepUpCache,
) {
    suspend fun list(): ApiResult<List<StaffDto>> = apiCall { api.staff() }
    suspend fun get(id: String): ApiResult<StaffDto> = apiCall { api.staffMember(id) }
    suspend fun permissions(id: String): ApiResult<PermissionsDto> = apiCall { api.staffPermissions(id) }
    suspend fun sessions(id: String): ApiResult<List<SessionInfoDto>> = apiCall { api.staffSessions(id) }

    suspend fun create(fullName: String, username: String, mobile: String, permissions: Set<Permission>?):
        ApiResult<CreateStaffResponse> = withStepUp(stepUp) { token ->
        api.createStaff(
            token,
            CreateStaffRequest(fullName.trim(), username.trim(), mobile.trim().ifBlank { null },
                permissions?.map { it.name }?.sorted()),
        )
    }

    suspend fun update(id: String, fullName: String, mobile: String): ApiResult<StaffDto> =
        withStepUp(stepUp) { token -> api.updateStaff(token, id, UpdateStaffRequest(fullName.trim(), mobile.trim().ifBlank { null })) }

    suspend fun setActive(id: String, active: Boolean): ApiResult<StaffDto> = withStepUp(stepUp) { token ->
        if (active) api.activateStaff(token, id) else api.deactivateStaff(token, id)
    }

    suspend fun resetPassword(id: String): ApiResult<TemporaryPasswordDto> =
        withStepUp(stepUp) { token -> api.resetStaffPassword(token, id) }

    suspend fun setPermissions(id: String, permissions: Set<Permission>): ApiResult<PermissionsDto> =
        withStepUp(stepUp) { token -> api.setStaffPermissions(token, id, SetPermissionsRequest(permissions.map { it.name }.sorted())) }

    suspend fun revokeSessions(id: String): ApiResult<Unit> = withStepUp(stepUp) { token ->
        val r = api.revokeStaffSessions(token, id)
        if (!r.isSuccessful) throw retrofit2.HttpException(r)
    }
}
