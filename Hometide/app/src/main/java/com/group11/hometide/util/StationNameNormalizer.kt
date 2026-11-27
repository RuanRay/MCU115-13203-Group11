package com.group11.hometide.util

import com.group11.hometide.data.local.entity.Operator

/** 站名正規化（規格 8.7、FR-SRCH-02、9.5） */
object StationNameNormalizer {

    private val WHITESPACE = Regex("\\s+")

    /** 存入與顯示用（中文站名）：「台」→「臺」、去除空白 */
    fun normalize(name: String): String = name.replace('台', '臺').replace(WHITESPACE, "")

    /**
     * 使用者輸入的查詢字：「台」→「臺」、去掉結尾的「站」。
     * 含英文字母時只合併空白（保留「Taipei Main」這類多字英文站名），否則全部移除。
     */
    fun normalizeQuery(input: String): String {
        val tai = input.replace('台', '臺')
        val spaced = if (tai.any { it in 'A'..'Z' || it in 'a'..'z' }) {
            tai.trim().replace(WHITESPACE, " ")
        } else {
            tai.replace(WHITESPACE, "")
        }
        return spaced.removeSuffix("站").trimEnd()
    }

    /**
     * AI 查詢的站名別名（9.6 第 30 題、FR-BUS-06）：輸入為 [normalizeQuery] 的結果，查不到原名時才用。
     * - 高鐵：高雄 → 左營
     * - 國道客運：縣市名 → 該縣市主要轉運站（同 worker/src/static/city-transport.json 的 bus 欄位）
     */
    fun alias(operator: Operator, name: String): String? = when (operator) {
        Operator.THSR -> THSR_ALIAS[name]
        Operator.BUS -> BUS_CITY[name] ?: BUS_CITY[name.removeSuffix("市").removeSuffix("縣")]
        Operator.TRA -> null
    }

    private val THSR_ALIAS = mapOf("高雄" to "左營")

    private val BUS_CITY = mapOf(
        "基隆" to "基隆轉運站", "臺北" to "臺北轉運站", "新北" to "板橋客運站", "桃園" to "桃園長庚轉運站",
        "新竹" to "新竹轉運站", "臺中" to "臺中轉運中心", "彰化" to "彰化站", "南投" to "南投", "雲林" to "西螺",
        "嘉義" to "嘉義市轉運中心", "嘉義縣" to "縣政府", "臺南" to "臺南轉運站", "高雄" to "建國客運站",
        "屏東" to "屏東轉運站", "宜蘭" to "宜蘭轉運站", "花蓮" to "花蓮轉運站",
    )
}
