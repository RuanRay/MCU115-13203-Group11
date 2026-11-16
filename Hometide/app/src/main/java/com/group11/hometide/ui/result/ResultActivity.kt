package com.group11.hometide.ui.result

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.databinding.ActivityResultBinding
import com.group11.hometide.ui.base.BaseActivity
import java.time.LocalDate

/** P4 查詢結果（規格 4.6）。畫面於任務 4-2；4-1 只定義開啟方式（FR-SRCH-01：operator、originId、destId、date）。 */
class ResultActivity : BaseActivity() {

    private lateinit var binding: ActivityResultBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityResultBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
    }

    companion object {
        const val EXTRA_OPERATOR = "operator"
        const val EXTRA_ORIGIN_ID = "originId"
        const val EXTRA_DEST_ID = "destId"
        const val EXTRA_DATE = "date" // yyyy-MM-dd

        fun intent(context: Context, operator: Operator, originId: String, destId: String, date: LocalDate): Intent =
            Intent(context, ResultActivity::class.java)
                .putExtra(EXTRA_OPERATOR, operator.name)
                .putExtra(EXTRA_ORIGIN_ID, originId)
                .putExtra(EXTRA_DEST_ID, destId)
                .putExtra(EXTRA_DATE, date.toString())
    }
}
