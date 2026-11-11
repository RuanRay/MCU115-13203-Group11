package com.group11.hometide.util

import androidx.annotation.StringRes
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Operator

/** 運具顯示名稱：台鐵、高鐵、國道客運 */
@get:StringRes
val Operator.labelRes: Int
    get() = when (this) {
        Operator.TRA -> R.string.operator_tra
        Operator.THSR -> R.string.operator_thsr
        Operator.BUS -> R.string.operator_bus
    }
