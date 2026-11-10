package com.group11.hometide.ui.base

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.annotation.IdRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.group11.hometide.R
import com.group11.hometide.ui.highway.HighwayActivity
import com.group11.hometide.ui.next.NextTrainActivity
import com.group11.hometide.ui.profile.ProfileActivity
import com.group11.hometide.ui.routes.MyRoutesActivity
import com.group11.hometide.ui.search.SearchActivity

/**
 * 所有頁面的父類別（規格 4.1）。
 * TODO(第 7 週，FR-USG-02)：頁面停留紀錄
 * TODO：共用頂部標題列、Snackbar 工具方法、動畫開關判斷（系統「移除動畫」時停用自訂動畫）
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
    }

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

    /** 五個分頁互相切換，不保留返回堆疊（規格 4.10）。 */
    protected fun setupBottomNav(nav: BottomNavigationView, @IdRes current: Int) {
        nav.selectedItemId = current
        nav.setOnItemSelectedListener { item ->
            if (item.itemId != current) {
                startActivity(Intent(this, TABS.getValue(item.itemId)))
                // TODO(第 7 週，AN-02)：左右滑動切換動畫
                finish()
            }
            true
        }
    }

    private companion object {
        val TABS = mapOf(
            R.id.navSearch to SearchActivity::class.java,
            R.id.navNext to NextTrainActivity::class.java,
            R.id.navHighway to HighwayActivity::class.java,
            R.id.navRoutes to MyRoutesActivity::class.java,
            R.id.navProfile to ProfileActivity::class.java,
        )
    }
}
