package com.group11.hometide.data.repository

import com.group11.hometide.data.local.dao.AiQueryLogDao
import com.group11.hometide.data.local.entity.AiQueryLog
import com.group11.hometide.data.local.entity.AiQueryResult
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import com.group11.hometide.util.StationNameNormalizer
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

/** FR-AI-02～05、FR-BUS-06、9.5；TC-08、10、11、41（資料層） */
class AiRepositoryTest {

    private val today = LocalDate.of(2026, 11, 6) // 9.6 基準：週五
    private var now = 1_000_000L
    private var window = ""
    private var reply: suspend (String) -> String? = { "{}" }
    private val prompts = mutableListOf<String>()

    private val stations = listOf(
        Operator.TRA to "桃園", Operator.TRA to "臺南", Operator.THSR to "左營", Operator.THSR to "臺北",
        Operator.BUS to "臺北轉運站", Operator.BUS to "臺南轉運站",
    ).map { (op, name) -> Station(op, "$op-$name", name, null, null, 0L) }

    private class FakeLogDao : AiQueryLogDao {
        val rows = mutableListOf<AiQueryLog>()
        override suspend fun insert(log: AiQueryLog): Long {
            rows += log.copy(id = rows.size + 1L)
            return rows.size.toLong()
        }
        override suspend fun markEdited(id: Long) {
            rows[id.toInt() - 1] = rows[id.toInt() - 1].copy(userEdited = true)
        }
    }

    private val dao = FakeLogDao()
    private val repo = AiRepository(
        logDao = dao,
        // 同 TransitRepository.findStation：先比正規化全名，再比去掉「站」的查詢字
        findStation = { op, name ->
            stations.firstOrNull { it.operator == op && it.nameZh in setOf(StationNameNormalizer.normalize(name), StationNameNormalizer.normalizeQuery(name)) }
        },
        loadWindow = { window },
        saveWindow = { window = it },
        generate = { prompts += it; reply(it) },
        now = { now },
        today = { today },
    )

    private fun parse(json: String, op: Operator = Operator.TRA, pax: Passengers = Passengers.DEFAULT): AiOutcome {
        reply = { json }
        return (runBlocking { repo.parse("句子", null, op, pax) } as Result.Success).data
    }

    private fun draft(json: String, op: Operator = Operator.TRA) = (parse(json, op) as AiOutcome.Ticket).draft

    /** TC-08、FR-AI-02：提示詞帶入今天日期、星期與預設運具 */
    @Test
    fun tc08_defaultOperatorAndStations() {
        val d = draft("""{"origin":"桃園","destination":"台南","date":"2026-11-07","timeFrom":"12:00","timeTo":"18:00"}""")
        assertEquals(Operator.TRA, d.operator)
        assertEquals("桃園", d.origin?.nameZh)
        assertEquals("臺南", d.dest?.nameZh)
        assertEquals(LocalDate.of(2026, 11, 7), d.date)
        assertEquals("12:00" to "18:00", d.timeFrom to d.timeTo)
        assertTrue(d.canSearch)
        assertTrue(prompts.single().contains("今天是 2026-11-06（星期五），使用者的預設運具是 TRA"))
        assertEquals(AiQueryResult.SUCCESS, dao.rows.single().result)
    }

    /** TC-41、FR-BUS-06：縣市名對應主要轉運站 */
    @Test
    fun tc41_busCityToTerminal() {
        val d = draft("""{"operator":"BUS","origin":"台北","destination":"臺南"}""")
        assertEquals(Operator.BUS, d.operator)
        assertEquals("臺北轉運站" to "臺南轉運站", d.origin?.nameZh to d.dest?.nameZh)
        assertEquals(today, d.date)
        assertFalse(d.dateReset)
    }

    /** 9.6 第 30 題：高鐵「高雄」→ 左營 */
    @Test
    fun thsrAlias() = assertEquals("左營", draft("""{"operator":"THSR","origin":"高雄","destination":"台北"}""").origin?.nameZh)

    /** FR-AI-03、FR-SRCH-06：無地名或查無此站 → 不能查詢，記 STATION_NOT_FOUND */
    @Test
    fun stationNotFound() {
        val none = draft("""{"origin":null,"destination":null}""")
        assertFalse(none.canSearch)
        val mars = draft("""{"origin":"火星","destination":"台南"}""")
        assertEquals("火星", mars.originText)
        assertNull(mars.origin)
        assertEquals(listOf(AiQueryResult.STATION_NOT_FOUND, AiQueryResult.STATION_NOT_FOUND), dao.rows.map { it.result })
    }

    @Test
    fun sameStation_cannotSearch() {
        val d = draft("""{"origin":"台南","destination":"臺南"}""")
        assertTrue(d.sameStation)
        assertFalse(d.canSearch)
    }

    /** 9.5：日期早於今天或晚於 60 天後 → 改為今天，記 INVALID_DATE */
    @Test
    fun invalidDate_resetsToToday() {
        for (date in listOf("2025-01-01", "2027-01-06", "11/20")) {
            val d = draft("""{"origin":"桃園","destination":"台南","date":"$date"}""")
            assertEquals(today, d.date)
            assertTrue(d.dateReset)
        }
        assertFalse(draft("""{"origin":"桃園","destination":"台南","date":"2027-01-05"}""").dateReset) // 第 60 天
        assertEquals(AiQueryResult.INVALID_DATE, dao.rows.first().result)
    }

    /** FR-AI-03：乘客合計超過 6 或負數 → 預設乘客並提示 */
    @Test
    fun passengers() {
        val ok = draft("""{"origin":"桃園","destination":"台南","passengers":{"ADULT":1,"CHILD":2}}""").passengers
        assertEquals(2, ok[FareClass.CHILD])
        val bad = draft("""{"origin":"桃園","destination":"台南","passengers":{"CHILD":7}}""")
        assertEquals(Passengers.DEFAULT, bad.passengers)
        assertTrue(bad.passengersReset)
        assertFalse(draft("""{"origin":"桃園","destination":"台南"}""").passengersReset)
    }

    @Test
    fun invalidTime_ignored() {
        val d = draft("""{"origin":"桃園","destination":"台南","timeFrom":"25:00","timeTo":"晚上"}""")
        assertEquals(null to null, d.timeFrom to d.timeTo)
    }

    /** 9.5：無關的句子 */
    @Test
    fun unrelated() = assertEquals(AiOutcome.Unrelated, parse("""{"unrelated":true}"""))

    /** FR-AI-05、TC-10：格式錯誤、斷線、逾時都顯示同一個失敗訊息，各自記錄代碼 */
    @Test
    fun failures_loggedWithCode() {
        assertEquals(AiOutcome.Failed, parse("not json"))
        reply = { throw RuntimeException("wrapped", IOException("offline")) }
        assertEquals(AiOutcome.Failed, (runBlocking { repo.parse("句子", null, Operator.TRA, Passengers.DEFAULT) } as Result.Success).data)
        reply = { withTimeout(1) { delay(1_000) }; "{}" }
        assertEquals(AiOutcome.Failed, (runBlocking { repo.parse("句子", null, Operator.TRA, Passengers.DEFAULT) } as Result.Success).data)
        assertEquals(listOf(AiQueryResult.PARSE_ERROR, AiQueryResult.NETWORK_ERROR, AiQueryResult.TIMEOUT), dao.rows.map { it.result })
    }

    /** TC-11：1 分鐘內第 6 次被擋（不呼叫模型），60 秒後恢復 */
    @Test
    fun tc11_rateLimit() {
        repeat(5) { assertTrue(parse("{}") !is AiOutcome.RateLimited); now += 1_000 }
        assertEquals(AiOutcome.RateLimited, parse("{}"))
        assertEquals(5, prompts.size)
        assertEquals(AiQueryResult.RATE_LIMITED, dao.rows.last().result)
        now = 1_000_000L + 60_000
        assertTrue(parse("{}") !is AiOutcome.RateLimited)
    }

    /** TC-09（資料層）：按「修改」→ user_edited = 1 */
    @Test
    fun markEdited() {
        val t = parse("""{"origin":"桃園","destination":"台南"}""") as AiOutcome.Ticket
        runBlocking { repo.markEdited(t.logId) }
        assertTrue(dao.rows.single().userEdited)
    }
}
