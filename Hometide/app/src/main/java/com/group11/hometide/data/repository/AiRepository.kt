package com.group11.hometide.data.repository

import android.util.Log
import com.google.gson.Gson
import com.group11.hometide.data.ai.GeminiClient
import com.group11.hometide.data.local.dao.AiQueryLogDao
import com.group11.hometide.data.local.entity.AiQueryLog
import com.group11.hometide.data.local.entity.AiQueryResult
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.Station
import com.group11.hometide.util.Passengers
import com.group11.hometide.util.Result
import com.group11.hometide.util.StationNameNormalizer
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException
import java.time.LocalDate
import kotlin.coroutines.cancellation.CancellationException

/**
 * 一句話查詢（FR-AI-02～05、FR-BUS-06、規格 9.5）：限流 → 呼叫 Gemini → Gson 解析 → 驗證 → 寫 ai_query_log。
 * [generate] 傳入提示詞、回傳模型的 JSON 文字；[loadWindow]／[saveWindow] 讀寫 DataStore 的 ai_calls_window（7.3）。
 */
class AiRepository(
    private val logDao: AiQueryLogDao,
    private val findStation: suspend (Operator, String) -> Station?,
    private val loadWindow: suspend () -> String,
    private val saveWindow: suspend (String) -> Unit,
    private val generate: suspend (String) -> String?,
    private val gson: Gson = Gson(),
    private val now: () -> Long = System::currentTimeMillis,
    private val today: () -> LocalDate = LocalDate::now,
) {

    suspend fun parse(input: String, uid: String?, defaultOperator: Operator, defaultPassengers: Passengers): Result<AiOutcome> = try {
        val text = input.trim()
        if (!acquire()) {
            log(uid, text, null, AiQueryResult.RATE_LIMITED, 0)
            Result.Success(AiOutcome.RateLimited)
        } else {
            val start = now()
            val (json, error) = call(GeminiClient.tripPrompt(text, today(), defaultOperator))
            val dto = json?.let { runCatching { gson.fromJson(it, TripDto::class.java) }.getOrNull() }
            val latency = now() - start
            when {
                error != null || dto == null -> {
                    log(uid, text, json, error ?: AiQueryResult.PARSE_ERROR, latency)
                    Result.Success(AiOutcome.Failed)
                }
                dto.unrelated == true -> {
                    // 7.1.11 沒有「無關」代碼：模型有正常回應，記 SUCCESS，parsed_json 可看出 unrelated
                    log(uid, text, json, AiQueryResult.SUCCESS, latency)
                    Result.Success(AiOutcome.Unrelated)
                }
                else -> {
                    val draft = validate(dto, text, defaultOperator, defaultPassengers)
                    val id = log(uid, text, json, draft.logResult, latency)
                    Result.Success(AiOutcome.Ticket(id, draft))
                }
            }
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // FR-AI-05：任何失敗都顯示同一則「無法解析這句話」
        Log.w(TAG, "AI parse failed ${e.javaClass.simpleName}")
        Result.Success(AiOutcome.Failed)
    }

    /** 9.5：使用者在確認卡按「修改」 */
    suspend fun markEdited(logId: Long) {
        runCatching { logDao.markEdited(logId) }.onFailure { Log.w(TAG, "markEdited failed ${it.javaClass.simpleName}") }
    }

    /** FR-AI-05：同一裝置每分鐘最多 [MAX_PER_MINUTE] 次；被擋下的那次不計入 */
    private suspend fun acquire(): Boolean {
        val t = now()
        val recent = loadWindow().split(',').mapNotNull { it.toLongOrNull() }.filter { t - it < WINDOW_MS }
        if (recent.size >= MAX_PER_MINUTE) return false
        saveWindow((recent + t).joinToString(","))
        return true
    }

    /** 回傳（模型文字, 失敗代碼）；逾時 8 秒（FR-AI-02） */
    private suspend fun call(prompt: String): Pair<String?, AiQueryResult?> = try {
        withTimeout(GeminiClient.TIMEOUT_MS) { generate(prompt) } to null
    } catch (e: TimeoutCancellationException) {
        null to AiQueryResult.TIMEOUT
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // SDK 會把連線錯誤包在自己的例外裡：往 cause 找 IOException；其他（伺服器忙、拒答）一律視為解析失敗
        Log.w(TAG, "Gemini call failed ${e.javaClass.simpleName}")
        null to when {
            generateSequence<Throwable>(e) { it.cause }.any { it is IOException } -> AiQueryResult.NETWORK_ERROR
            e.javaClass.simpleName == "RequestTimeoutException" -> AiQueryResult.TIMEOUT
            else -> AiQueryResult.PARSE_ERROR
        }
    }

    /** FR-AI-03 */
    private suspend fun validate(d: TripDto, input: String, defaultOp: Operator, defaultPax: Passengers): TripDraft {
        val op = Operator.entries.firstOrNull { it.name == d.operator } ?: defaultOp
        val today = today()
        val parsedDate = d.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val dateOk = parsedDate != null && !parsedDate.isBefore(today) && !parsedDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))
        val paxCounts = d.passengers?.mapNotNull { (k, v) -> FareClass.entries.firstOrNull { it.name == k }?.let { it to v } }?.toMap()
        val pax = paxCounts?.let { Passengers.ofOrNull(it) }
        // 全部為 0 視同沒提到；有負數或超過 6 才提示「已改為預設」
        val paxReset = paxCounts != null && pax == null && paxCounts.values.any { it != 0 }
        return TripDraft(
            input = input,
            operator = op,
            originText = d.origin?.trim()?.ifEmpty { null },
            origin = resolve(op, d.origin),
            destText = d.destination?.trim()?.ifEmpty { null },
            dest = resolve(op, d.destination),
            date = if (dateOk) parsedDate!! else today,
            dateReset = d.date != null && !dateOk,
            timeFrom = d.timeFrom?.takeIf { TIME.matches(it) },
            timeTo = d.timeTo?.takeIf { TIME.matches(it) },
            passengers = pax ?: defaultPax,
            passengersReset = paxReset,
        )
    }

    private suspend fun resolve(op: Operator, name: String?): Station? {
        val text = name?.trim()?.ifEmpty { null } ?: return null
        return findStation(op, text)
            ?: StationNameNormalizer.alias(op, StationNameNormalizer.normalizeQuery(text))?.let { findStation(op, it) }
    }

    private suspend fun log(uid: String?, input: String, json: String?, result: AiQueryResult, latency: Long): Long =
        runCatching { logDao.insert(AiQueryLog(uid = uid, inputText = input, parsedJson = json, result = result, latencyMs = latency, createdAt = now())) }
            .onFailure { Log.w(TAG, "ai_query_log insert failed ${it.javaClass.simpleName}") }
            .getOrDefault(0L)

    private data class TripDto(
        val operator: String?,
        val origin: String?,
        val destination: String?,
        val date: String?,
        val timeFrom: String?,
        val timeTo: String?,
        val unrelated: Boolean?,
        val passengers: Map<String, Int>?,
    )

    companion object {
        private const val TAG = "AiRepository"
        const val MAX_PER_MINUTE = 5
        private const val WINDOW_MS = 60_000L
        private const val MAX_DAYS_AHEAD = 60L
        private val TIME = Regex("([01]\\d|2[0-3]):[0-5]\\d")
    }
}

sealed interface AiOutcome {
    /** 顯示確認卡；[logId] 給「修改」時更新 user_edited */
    data class Ticket(val logId: Long, val draft: TripDraft) : AiOutcome
    /** 9.5：「這句話好像不是查詢車班，請換個說法」 */
    data object Unrelated : AiOutcome
    /** FR-AI-05：「操作太頻繁，請稍後再試」 */
    data object RateLimited : AiOutcome
    /** FR-AI-05：「無法解析這句話，請改用手動查詢」 */
    data object Failed : AiOutcome
}

/** 確認卡內容（FR-AI-04）；[origin]／[dest] 為 null 表示找不到車站（[originText] 為模型給的原名，可能為 null） */
data class TripDraft(
    val input: String,
    val operator: Operator,
    val originText: String?,
    val origin: Station?,
    val destText: String?,
    val dest: Station?,
    val date: LocalDate,
    /** 日期超出今天～60 天後，已改為今天（9.5 INVALID_DATE） */
    val dateReset: Boolean,
    val timeFrom: String?,
    val timeTo: String?,
    val passengers: Passengers,
    /** 乘客不合法，已改為預設（11.2「乘客人數已改為預設」） */
    val passengersReset: Boolean,
) {
    val sameStation: Boolean get() = origin != null && origin.stationId == dest?.stationId

    /** 「查詢班次」可按 */
    val canSearch: Boolean get() = origin != null && dest != null && !sameStation

    val logResult: AiQueryResult
        get() = when {
            origin == null || dest == null -> AiQueryResult.STATION_NOT_FOUND
            dateReset -> AiQueryResult.INVALID_DATE
            else -> AiQueryResult.SUCCESS
        }
}
