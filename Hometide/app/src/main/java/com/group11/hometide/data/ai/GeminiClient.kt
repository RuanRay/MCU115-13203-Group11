package com.group11.hometide.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.group11.hometide.data.local.entity.Operator
import java.time.LocalDate

/** 規格 9.1：Firebase AI Logic（Gemini Developer API），一律結構化輸出 */
object GeminiClient {
    /**
     * Firebase AI Logic 模型頁（2026-11-27 查證）的穩定版。
     * 原訂 gemini-3.8-flash 當天持續回「high demand」，實作者改選 Flash-Lite。
     */
    const val MODEL = "gemini-3.5-flash-lite"

    fun jsonModel(schema: Schema): GenerativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = MODEL,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        },
    )

    /** FR-AI-02：逾時 8 秒視為失敗 */
    const val TIMEOUT_MS = 8_000L

    /** 9.3（SDK 實際類別為 Schema） */
    val tripSchema: Schema = Schema.obj(
        properties = mapOf(
            // 規則寫「沒提到填 null」：欄位要標 nullable，否則模型會自己補值（實測會補 00:00–23:59、全票 1）
            "operator" to Schema.enumeration(listOf("TRA", "THSR", "BUS"), nullable = true),
            "origin" to Schema.string(nullable = true),
            "destination" to Schema.string(nullable = true),
            "date" to Schema.string(nullable = true),
            "timeFrom" to Schema.string(nullable = true),
            "timeTo" to Schema.string(nullable = true),
            "unrelated" to Schema.boolean(),
            "passengers" to Schema.obj(
                properties = PASSENGER_KEYS.associateWith { Schema.integer() },
                optionalProperties = PASSENGER_KEYS,
                nullable = true,
            ),
        ),
        optionalProperties = listOf("operator", "origin", "destination", "date", "timeFrom", "timeTo", "passengers"),
    )

    private val PASSENGER_KEYS get() = listOf("ADULT", "STUDENT", "CHILD", "SENIOR", "DISABLED")

    /** 9.2 提示詞（送給模型的指令，不是畫面文字）；只帶入日期、星期、預設運具與使用者原句（NFR-06） */
    fun tripPrompt(input: String, today: LocalDate, defaultOperator: Operator): String = """
        你是台灣返鄉交通查詢助理（台鐵、高鐵、國道客運）。請把使用者的一句話轉換成查詢條件，只輸出符合 schema 的 JSON。
        今天是 $today（${WEEKDAYS[today.dayOfWeek.value - 1]}），使用者的預設運具是 ${defaultOperator.name}。
        規則：
        1. operator：提到「高鐵」為 THSR；提到「台鐵、火車、自強、莒光、區間」為 TRA；
           提到「客運、國道客運、統聯、國光、和欣、阿羅哈」為 BUS；沒提到填 null。
        2. origin、destination：只填車站、轉運站或縣市名稱（不含「站」字），無法判斷填 null。
           「回家、回學校」等無地名的說法填 null，不要猜測。
        3. date：換算成 yyyy-MM-dd；「明天、後天、這週五、下週一」依今天日期計算；沒提到填 null。
        4. timeFrom、timeTo（HH:mm）：早上 06:00–12:00、中午 11:00–14:00、下午 12:00–18:00、
           傍晚 16:00–19:00、晚上 18:00–23:59；「X 點以後」只填 timeFrom；沒提到填 null。
        5. 與交通查詢無關的句子，所有欄位填 null，並把 unrelated 設為 true。
        6. passengers：句子提到人數或票種時填寫，例如「帶兩個小孩」→ CHILD 2、ADULT 1（說話者本人）；
           「跟我媽（敬老）」→ ADULT 1、SENIOR 1；沒提到填 null。
        使用者：$input
    """.trimIndent()

    private val WEEKDAYS = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")
}
