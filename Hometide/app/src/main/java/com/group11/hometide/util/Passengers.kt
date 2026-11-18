package com.group11.hometide.util

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass

/**
 * CR-01 乘客組合（FR-SRCH-08、FR-SET-02）：每種 0～6 人、合計 1～6 人。
 * 只保留人數 > 0 的票種，依 [FareClass] 順序排列；存成 JSON 例 `{"ADULT":2,"CHILD":1}`（7.1.3、7.3）。
 */
class Passengers private constructor(val counts: Map<FareClass, Int>) {

    val total: Int get() = counts.values.sum()

    operator fun get(cls: FareClass): Int = counts[cls] ?: 0

    /** 只有一種票種一人 */
    val isSingle: Boolean get() = total == 1

    fun canAdd(): Boolean = total < MAX
    fun canRemove(cls: FareClass): Boolean = get(cls) > 0 && total > 1

    fun plus(cls: FareClass): Passengers = if (canAdd()) of(counts + (cls to get(cls) + 1)) else this
    fun minus(cls: FareClass): Passengers = if (canRemove(cls)) of(counts + (cls to get(cls) - 1)) else this

    fun toJson(): String = Gson().toJson(counts.mapKeys { it.key.name })

    override fun equals(other: Any?) = other is Passengers && other.counts == counts
    override fun hashCode() = counts.hashCode()
    override fun toString() = toJson()

    companion object {
        const val MAX = 6
        val DEFAULT = Passengers(mapOf(FareClass.ADULT to 1))

        /** 不合法（任一 < 0、超過 6、合計不在 1～6）回 null */
        fun ofOrNull(counts: Map<FareClass, Int>): Passengers? {
            if (counts.values.any { it !in 0..MAX } || counts.values.sum() !in 1..MAX) return null
            return Passengers(FareClass.entries.mapNotNull { c -> counts[c]?.takeIf { it > 0 }?.let { c to it } }.toMap())
        }

        private fun of(counts: Map<FareClass, Int>) = ofOrNull(counts) ?: DEFAULT

        /** NULL、格式錯誤或不合法時視為全票 1（7.1.3） */
        fun fromJson(json: String?): Passengers {
            if (json.isNullOrBlank()) return DEFAULT
            val raw = runCatching { Gson().fromJson<Map<String, Int?>>(json, object : TypeToken<Map<String, Int?>>() {}.type) }.getOrNull()
                ?: return DEFAULT
            // Gson 會把 {"ADULT":null} 解析成 null 值，先濾掉
            val counts = raw.mapNotNull { (k, v) -> FareClass.entries.firstOrNull { it.name == k }?.let { c -> v?.let { c to it } } }.toMap()
            return ofOrNull(counts) ?: DEFAULT
        }
    }
}

/** 票價明細一列（FR-RES-07）：單價查無時 [unit] 為 null */
data class FareLine(val fareClass: FareClass, val count: Int, val unit: Int?, val approx: Boolean) {
    val subtotal: Int? get() = unit?.times(count)
}

/** FR-RES-02：總價＝Σ（單價 × 人數）；任一票種查無時總價為 null；任一單價為約略值時總價也是約略值 */
data class FareTotal(val lines: List<FareLine>) {
    val missing: List<FareClass> get() = lines.filter { it.unit == null }.map { it.fareClass }
    val total: Int? get() = if (missing.isEmpty()) lines.sumOf { it.subtotal!! } else null
    val approx: Boolean get() = lines.any { it.approx && it.unit != null }

    companion object {
        /** [fares] 為同一車種（國道客運為路線）的各票種單價 */
        fun of(fares: List<FareCache>, passengers: Passengers): FareTotal {
            val byClass = fares.associateBy { it.fareClass }
            return FareTotal(passengers.counts.map { (cls, n) -> byClass[cls].let { FareLine(cls, n, it?.price, it?.isApprox ?: false) } })
        }
    }
}
