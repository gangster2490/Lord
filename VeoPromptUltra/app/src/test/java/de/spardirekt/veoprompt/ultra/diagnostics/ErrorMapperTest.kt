package de.spardirekt.veoprompt.ultra.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorMapperTest {
    @Test
    fun quotaIsNotSuccess() {
        val err = ErrorMapper.fromHttp(429, """{"error":{"code":"insufficient_quota"}}""")
        assertTrue(err is AppError.QuotaOrBilling)
        assertFalse(err.retryable)
    }

    @Test
    fun authFails() {
        assertTrue(ErrorMapper.fromHttp(401, "invalid") is AppError.InvalidApiKey)
    }

    @Test
    fun neverLogsFullKey() {
        val safe = ErrorMapper.sanitize("Bearer sk-abc1234567890secret")
        assertFalse(safe.contains("sk-abc1234567890secret"))
        assertTrue(safe.contains("••••"))
    }
}
