package com.group11.hometide.util

/** 站名正規化（規格 8.7、FR-SRCH-02、9.x）。別名（例：高雄→左營）第 6 週 AI 查詢時再加。 */
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
}
