package com.group11.hometide.util

import com.group11.hometide.data.local.entity.FareCache
import com.group11.hometide.data.local.entity.FareClass.ADULT
import com.group11.hometide.data.local.entity.FareClass.CHILD
import com.group11.hometide.data.local.entity.FareClass.STUDENT
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassengersTest {

    @Test
    fun json_roundTrip_andInvalidFallsBackToDefault() {
        val p = Passengers.DEFAULT.plus(ADULT).plus(CHILD)
        assertEquals("""{"ADULT":2,"CHILD":1}""", p.toJson())
        assertEquals(p, Passengers.fromJson(p.toJson()))
        for (bad in listOf(null, "", "x", """{"ADULT":7}""", """{"ADULT":0}""", """{"ADULT":-1,"CHILD":2}""", """{"GROUP":1}""", """{"ADULT":null}""")) {
            assertEquals(bad, Passengers.DEFAULT, Passengers.fromJson(bad))
        }
    }

    /** TC-44：合計 6 人後不能再加；合計 1 人時不能再減 */
    @Test
    fun limits_totalOneToSix() {
        assertFalse(Passengers.DEFAULT.canRemove(ADULT))
        assertEquals(Passengers.DEFAULT, Passengers.DEFAULT.minus(ADULT))
        assertFalse(Passengers.DEFAULT.canRemove(CHILD)) // 0 人的列
        var p = Passengers.DEFAULT
        repeat(10) { p = p.plus(CHILD) }
        assertEquals(6, p.total)
        assertFalse(p.canAdd())
        assertTrue(p.canRemove(ADULT))
        assertEquals(listOf(ADULT, CHILD), p.counts.keys.toList())
        assertEquals(mapOf(CHILD to 5), p.minus(ADULT).counts) // 減到 0 的票種移除
    }

    private fun fare(cls: FareClass, price: Int, approx: Boolean = false) =
        FareCache(Operator.TRA, "1080", "4220", "3", cls, price, approx, 0)

    /** TC-43：總價＝全票 × 2＋孩童 × 1 */
    @Test
    fun total_sumsByClass() {
        val p = Passengers.fromJson("""{"ADULT":2,"CHILD":1}""")
        val t = FareTotal.of(listOf(fare(ADULT, 739), fare(CHILD, 370)), p)
        assertEquals(1848, t.total)
        assertFalse(t.approx)
        assertEquals(listOf(1478, 370), t.lines.map { it.subtotal })
    }

    /** TC-46：任一票種查無 → 總價 null，列出查無票種；約略值帶到總價 */
    @Test
    fun total_missingAndApprox() {
        val p = Passengers.fromJson("""{"ADULT":1,"STUDENT":1}""")
        val t = FareTotal.of(listOf(fare(ADULT, 739, approx = true)), p)
        assertNull(t.total)
        assertEquals(listOf(STUDENT), t.missing)
        assertNull(t.lines[1].subtotal)
        assertTrue(FareTotal.of(listOf(fare(ADULT, 739, approx = true)), Passengers.DEFAULT).approx)
    }
}
