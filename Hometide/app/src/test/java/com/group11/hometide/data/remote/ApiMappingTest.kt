package com.group11.hometide.data.remote

import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.remote.dto.StationDto
import com.group11.hometide.data.repository.toEntity
import com.group11.hometide.util.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Worker 錯誤代碼對應（8.3、6.3）與車站 DTO 轉換 */
class ApiMappingTest {

    @Test
    fun errorCodes() {
        assertEquals(AppError.NetworkError, errorCodeOf("NETWORK"))
        assertEquals(AppError.AuthError, errorCodeOf("AUTH"))
        assertEquals(AppError.QuotaExceeded, errorCodeOf("QUOTA"))
        assertEquals(AppError.NotFound, errorCodeOf("NOT_FOUND"))
        assertEquals(AppError.ParseError, errorCodeOf("PARSE"))
        assertEquals(AppError.Unknown, errorCodeOf("UNKNOWN"))
        assertNull(errorCodeOf("SOMETHING_ELSE"))
        assertNull(errorCodeOf(null))
    }

    @Test
    fun stationDto_toEntity_normalizesAndValidates() {
        val s = StationDto("4220", "台南", "Tainan", "臺南市", 3).toEntity(Operator.TRA, 100)!!
        assertEquals("臺南", s.nameZh)
        assertEquals("4220", s.stationId)
        assertEquals(3, s.sortKey)
        assertEquals(100L, s.updatedAt)
        assertNull(StationDto(null, "臺南", null, null, null).toEntity(Operator.TRA, 0))
        assertNull(StationDto("1", " ", null, null, null).toEntity(Operator.TRA, 0))
    }
}
