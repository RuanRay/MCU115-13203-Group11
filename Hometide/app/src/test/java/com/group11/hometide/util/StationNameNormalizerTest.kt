package com.group11.hometide.util

import org.junit.Assert.assertEquals
import org.junit.Test

/** FR-SRCH-02、8.7；TC-06（單元部分） */
class StationNameNormalizerTest {

    @Test
    fun tc06_taiToTai() = assertEquals("臺南", StationNameNormalizer.normalize("台南"))

    @Test
    fun normalize_removesWhitespace_keepsOtherChars() {
        assertEquals("臺北", StationNameNormalizer.normalize(" 台 北 "))
        assertEquals("臺北-環島", StationNameNormalizer.normalize("台北-環島"))
        assertEquals("南方小站", StationNameNormalizer.normalize("南方小站")) // 站名本身的「站」保留
    }

    @Test
    fun normalizeQuery_alsoDropsTrailingStation() {
        assertEquals("臺南", StationNameNormalizer.normalizeQuery("台南站"))
        assertEquals("臺南", StationNameNormalizer.normalizeQuery("台南 站 "))
        assertEquals("", StationNameNormalizer.normalizeQuery(""))
    }

    @Test
    fun normalizeQuery_keepsSpacesInEnglish() {
        assertEquals("Taipei Main", StationNameNormalizer.normalizeQuery("  Taipei   Main "))
        assertEquals("tainan", StationNameNormalizer.normalizeQuery("tainan"))
    }

    /** 9.6 第 30 題、FR-BUS-06 */
    @Test
    fun alias_thsrAndBusCity() {
        assertEquals("左營", StationNameNormalizer.alias(com.group11.hometide.data.local.entity.Operator.THSR, "高雄"))
        assertEquals("臺南轉運站", StationNameNormalizer.alias(com.group11.hometide.data.local.entity.Operator.BUS, "臺南市"))
        assertEquals("縣政府", StationNameNormalizer.alias(com.group11.hometide.data.local.entity.Operator.BUS, "嘉義縣"))
        assertEquals(null, StationNameNormalizer.alias(com.group11.hometide.data.local.entity.Operator.TRA, "高雄"))
    }
}
