package com.group11.hometide.ui.base

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable
import com.google.android.material.snackbar.Snackbar
import com.group11.hometide.R

/**
 * 所有頁面的父類別（規格 4.1）。
 * TODO(第 7 週，FR-USG-02)：頁面停留紀錄
 */
abstract class BaseActivity : AppCompatActivity() {

    /** Snackbar：3 秒、藏青底（規格 4.1） */
    fun showSnackbar(@StringRes message: Int, anchor: View? = null) {
        Snackbar.make(findViewById(android.R.id.content), message, SNACKBAR_MS)
            .setBackgroundTint(ContextCompat.getColor(this, R.color.color_primary))
            .setTextColor(ContextCompat.getColor(this, R.color.color_on_primary))
            .setAnchorView(anchor)
            .show()
    }

    private companion object {
        const val SNACKBAR_MS = 3_000
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

    /** 系統「移除動畫」開啟時為 false，所有自訂動畫都要先檢查（規格 4.1）。 */
    protected val animationsEnabled: Boolean
        get() = ValueAnimator.areAnimatorsEnabled()

    /** 內容避開系統列。有底部導覽列的頁面不加底部內距，由 BottomNavigationView 自己處理。 */
    protected fun applySystemBarInsets(root: View, padBottom: Boolean = true) {
        // 疊加在 layout 原本的內距上（例如頁面左右邊距），不覆蓋
        val left = root.paddingLeft
        val top = root.paddingTop
        val right = root.paddingRight
        val bottom = root.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                left + bars.left,
                top + bars.top,
                right + bars.right,
                bottom + if (padBottom) bars.bottom else 0,
            )
            insets
        }
    }
}

/**
 * 按鈕處理中：停用並以轉圈取代文字（FR-AUTH-01「按鈕顯示進度並停用」）。
 * TODO(第 7 週)：規格 4.2 的「三點跳動」動畫
 */
fun MaterialButton.showLoading(loading: Boolean, @StringRes text: Int) {
    isEnabled = !loading
    if (loading) {
        val spec = CircularProgressIndicatorSpec(context, null, 0, com.google.android.material.R.style.Widget_Material3_CircularProgressIndicator_ExtraSmall)
        icon = IndeterminateDrawable.createCircularDrawable(context, spec)
        iconGravity = MaterialButton.ICON_GRAVITY_TEXT_START
        iconPadding = 0
        this.text = ""
    } else {
        icon = null
        setText(text)
    }
}
