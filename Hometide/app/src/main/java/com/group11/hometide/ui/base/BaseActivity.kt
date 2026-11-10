package com.group11.hometide.ui.base

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * 所有頁面的父類別（規格 4.1）。
 * TODO(第 7 週，FR-USG-02)：頁面停留紀錄
 * TODO：共用頂部標題列、Snackbar 工具方法
 */
abstract class BaseActivity : AppCompatActivity() {

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
