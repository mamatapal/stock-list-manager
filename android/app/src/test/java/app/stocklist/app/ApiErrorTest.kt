package app.stocklist.app

import app.stocklist.app.data.remote.toApiError
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ApiErrorTest {
    private val json = "application/problem+json".toMediaType()

    @Test
    fun readsServerProblemDetails() {
        val body = """{"status":400,"code":"VALIDATION_FAILED","detail":"Some fields are invalid",
            "errors":[{"field":"pinCode","message":"6-digit PIN code"}]}""".toResponseBody(json)
        val error = Response.error<Any>(400, body).toApiError(400)
        assertEquals("VALIDATION_FAILED", error.code)
        assertEquals("6-digit PIN code", error.fieldErrors["pinCode"])
    }

    @Test
    fun recognisesStepUpRequired() {
        val body = """{"status":403,"code":"STEP_UP_REQUIRED","detail":"Please re-enter your password"}""".toResponseBody(json)
        assertTrue(Response.error<Any>(403, body).toApiError(403).needsStepUp)
    }

    @Test
    fun fallsBackWhenBodyIsNotJson() {
        val error = Response.error<Any>(502, "<html>Bad gateway</html>".toResponseBody("text/html".toMediaType()))
            .toApiError(502)
        assertEquals("HTTP_502", error.code)
        assertTrue(error.message.contains("server"))
    }
}
