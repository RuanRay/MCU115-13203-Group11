package com.group11.hometide.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.IdRes
import androidx.fragment.app.Fragment
import androidx.fragment.app.commitNow
import com.group11.hometide.R
import com.group11.hometide.databinding.ActivityMainBinding
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.ui.highway.HighwayFragment
import com.group11.hometide.ui.next.NextTrainFragment
import com.group11.hometide.ui.profile.ProfileFragment
import com.group11.hometide.ui.routes.MyRoutesFragment
import com.group11.hometide.ui.search.SearchFragment

/**
 * 五個分頁的主頁。底部導覽列固定不重建，分頁以 Fragment show／hide 切換並保留狀態；
 * 切換時播放 AN-02，不保留返回堆疊（規格 4.1、4.10）。
 */
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root, padBottom = false)

        // 重建時 FragmentManager 與 BottomNavigationView 會自行還原狀態
        if (savedInstanceState == null) switchTo(R.id.navSearch, animate = false)
        handleIntent(intent)

        binding.bottomNav.setOnItemSelectedListener { item ->
            switchTo(item.itemId, animate = true)
            true
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** FR-RMD-03：點提醒通知 → P5「出發提醒」分頁 */
    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_REMINDERS, false) != true) return
        intent.removeExtra(EXTRA_OPEN_REMINDERS)
        // 直接切分頁並勾選導覽列（onCreate 期間設 selectedItemId 不一定會觸發監聽器）
        switchTo(R.id.navRoutes, animate = false)
        binding.bottomNav.menu.findItem(R.id.navRoutes).isChecked = true
        // 分頁畫面可能還沒建立：MyRoutesFragment 會先記下，畫面建立後再切到提醒分頁
        (supportFragmentManager.findFragmentByTag(TAGS[3]) as? MyRoutesFragment)?.showReminders()
    }

    /** P5「＋ 新增」→ P3 並聚焦手動查詢（規格 4.7） */
    fun openManualSearch() {
        binding.bottomNav.selectedItemId = R.id.navSearch // 觸發監聽器切換分頁（commitNow 已完成）
        (supportFragmentManager.findFragmentByTag(TAGS[0]) as? SearchFragment)?.focusManualSearch()
    }

    private fun switchTo(@IdRes tab: Int, animate: Boolean) {
        val fm = supportFragmentManager
        // 目前分頁以「沒被隱藏的分頁 Fragment」為準，不依賴 bottomNav 狀態（重建時它可能尚未還原）
        val current = fm.fragments.firstOrNull { !it.isHidden && it.tag in TAGS }
        val to = TABS.indexOf(tab)
        if (current?.tag == TAGS[to]) return
        val from = TAGS.indexOf(current?.tag)
        // commitNow：連點兩個分頁時，第二次才找得到第一次加入的 Fragment，不會重複 add
        fm.commitNow {
            setReorderingAllowed(true)
            if (animate && animationsEnabled) {
                // AN-02：新頁從點擊方向滑入，舊頁往反方向滑出並變淡
                if (to > from) {
                    setCustomAnimations(R.anim.nav_enter_from_right, R.anim.nav_exit_to_left)
                } else {
                    setCustomAnimations(R.anim.nav_enter_from_left, R.anim.nav_exit_to_right)
                }
            }
            current?.let { hide(it) }
            val existing = fm.findFragmentByTag(TAGS[to])
            if (existing == null) add(R.id.fragmentContainer, newTab(tab), TAGS[to]) else show(existing)
        }
    }

    private fun newTab(@IdRes tab: Int): Fragment = when (tab) {
        R.id.navSearch -> SearchFragment()
        R.id.navNext -> NextTrainFragment()
        R.id.navHighway -> HighwayFragment()
        R.id.navRoutes -> MyRoutesFragment()
        R.id.navProfile -> ProfileFragment()
        else -> error("Unknown tab $tab")
    }

    companion object {
        private const val EXTRA_OPEN_REMINDERS = "open_reminders"

        /** 提醒通知的點擊目的地（FR-RMD-03） */
        fun intentForReminders(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_REMINDERS, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** 導覽列由左到右的順序，用來判斷滑動方向。 */
        private val TABS = listOf(R.id.navSearch, R.id.navNext, R.id.navHighway, R.id.navRoutes, R.id.navProfile)

        /** Fragment tag 用固定字串，不用資源 ID（ID 每次建置可能不同，會影響狀態還原）。 */
        private val TAGS = listOf("tab_search", "tab_next", "tab_highway", "tab_routes", "tab_profile")
    }
}
