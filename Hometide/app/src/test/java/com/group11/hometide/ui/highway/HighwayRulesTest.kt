package com.group11.hometide.ui.highway

import com.group11.hometide.data.repository.Freeway
import com.group11.hometide.data.repository.FreewayDirection
import com.group11.hometide.data.repository.HighwayQuery
import com.group11.hometide.data.repository.RoadLevel
import com.group11.hometide.data.repository.RoadSegment
import com.group11.hometide.data.repository.Traffic
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.defaultSelection
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.entriesOf
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.exitsOf
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.extraMin
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.jamCount
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.partialNoLive
import com.group11.hometide.ui.highway.HighwayViewModel.Companion.titleOf
import com.group11.hometide.ui.highway.HighwayViewModel.Title
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 國道表單預設與連動（實作者確認）、標題規則（FR-HWY-02；全無即時資料為實作者確認） */
class HighwayRulesTest {

    private val s = listOf("基隆端", "桃園", "中壢", "永康", "高雄端")
    private val n = s.reversed()
    private val f1 = Freeway("000010", "國道 1 號", listOf(FreewayDirection("S", "南下", s), FreewayDirection("N", "北上", n)))
    private val f3 = Freeway("000030", "國道 3 號", listOf(FreewayDirection("S", "南下", listOf("基金", "大鵬灣端"))))

    @Test
    fun default_followsMockup() {
        assertEquals(HighwayQuery("000010", "S", "桃園", "永康"), defaultSelection(listOf(f3, f1), HighwayViewModel.DEFAULT))
    }

    @Test
    fun default_fallsBackToFirstAndLast() {
        assertEquals(HighwayQuery("000030", "S", "基金", "大鵬灣端"), defaultSelection(listOf(f3), HighwayViewModel.DEFAULT))
        // 下路不在上路之後 → 改最後一個
        assertEquals(HighwayQuery("000010", "S", "中壢", "高雄端"), defaultSelection(listOf(f1), HighwayQuery("000010", "S", "中壢", "桃園")))
    }

    @Test
    fun entriesAndExits() {
        assertEquals(listOf("基隆端", "桃園", "中壢", "永康"), entriesOf(s))
        assertEquals(listOf("永康", "高雄端"), exitsOf(s, "中壢"))
    }

    private fun traffic(est: Int, normal: Int, vararg levels: RoadLevel) = Traffic(
        "國道 1 號", "南下", "桃園", "永康", est, normal, 0,
        levels.map { RoadSegment("a", "b", null, it, null, false) },
    )

    @Test
    fun title_rules() {
        assertEquals(Title.Form, titleOf(null))
        assertEquals(Title.Delay(25), titleOf(traffic(185, 160, RoadLevel.JAM)))
        assertEquals(Title.Smooth, titleOf(traffic(165, 160, RoadLevel.SMOOTH)))
        assertEquals(Title.Smooth, titleOf(traffic(150, 160, RoadLevel.SMOOTH)))
        assertEquals(Title.NoLive, titleOf(traffic(160, 160, RoadLevel.UNKNOWN, RoadLevel.UNKNOWN)))
    }

    /** TC-35：「壅塞 N 段」＝紅色段數；11.2：部分（非全部）路段無資料才標示 */
    @Test
    fun jamCount_andPartialNoLive() {
        assertEquals(2, jamCount(traffic(200, 160, RoadLevel.JAM, RoadLevel.SMOOTH, RoadLevel.JAM, RoadLevel.BUSY)))
        assertEquals(true, partialNoLive(traffic(170, 160, RoadLevel.SMOOTH, RoadLevel.UNKNOWN)))
        assertEquals(false, partialNoLive(traffic(170, 160, RoadLevel.SMOOTH, RoadLevel.BUSY)))
        assertEquals(false, partialNoLive(traffic(160, 160, RoadLevel.UNKNOWN, RoadLevel.UNKNOWN)))
    }

    @Test
    fun extraMin_onlyWhenSlowerAndHasLiveData() {
        assertEquals(25, extraMin(traffic(185, 160, RoadLevel.JAM)))
        assertEquals(3, extraMin(traffic(163, 160, RoadLevel.SMOOTH)))
        assertNull(extraMin(traffic(150, 160, RoadLevel.SMOOTH)))
        assertNull(extraMin(traffic(170, 160, RoadLevel.UNKNOWN)))
    }
}
