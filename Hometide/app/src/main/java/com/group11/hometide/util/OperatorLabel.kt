package com.group11.hometide.util

import android.content.Context
import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.FareClass
import com.group11.hometide.data.local.entity.Operator
import java.text.NumberFormat
import java.util.Locale

/** 運具顯示名稱：台鐵、高鐵、國道客運 */
@get:StringRes
val Operator.labelRes: Int
    get() = when (this) {
        Operator.TRA -> R.string.operator_tra
        Operator.THSR -> R.string.operator_thsr
        Operator.BUS -> R.string.operator_bus
    }

/** CR-01 票種顯示名稱：全票、學生、孩童、敬老、愛心 */
@get:StringRes
val FareClass.labelRes: Int
    get() = when (this) {
        FareClass.ADULT -> R.string.fare_class_adult
        FareClass.STUDENT -> R.string.fare_class_student
        FareClass.CHILD -> R.string.fare_class_child
        FareClass.SENIOR -> R.string.fare_class_senior
        FareClass.DISABLED -> R.string.fare_class_disabled
    }

/** 乘客組合文字：[itemRes] 為 pax_combo_item（「全票 2」）或 pax_mix_item（「全票2」），以「・」連接 */
fun Context.passengerText(p: Passengers, @StringRes itemRes: Int = R.string.pax_combo_item): String =
    p.counts.entries.joinToString(getString(R.string.pax_separator)) { (c, n) -> getString(itemRes, getString(c.labelRes), n) }

/** 「$1,848」「約$1,848」；null 為「—」 */
fun Context.priceText(price: Int?, approx: Boolean): String {
    if (price == null) return getString(R.string.fare_unknown)
    val amount = NumberFormat.getIntegerInstance(Locale.TAIWAN).format(price)
    return getString(if (approx) R.string.fare_price_approx else R.string.fare_price, amount)
}
