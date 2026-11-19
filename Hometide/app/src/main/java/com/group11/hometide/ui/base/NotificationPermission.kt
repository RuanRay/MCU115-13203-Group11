package com.group11.hometide.ui.base

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** 通知權限（FR-RMD-05） */
object NotificationPermission {

    /** 通知是否能送達：Android 13 以上的執行期權限，或在系統設定裡關掉通知都算未開啟 */
    fun isEnabled(context: Context) = NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** P5 橫幅「前往設定 ›」：本 App 的通知設定頁 */
    fun settingsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    /**
     * 註冊「首次設定提醒」的說明與請求：回傳的函式在提醒建立成功後呼叫。
     * Android 13 以上、尚未授權、且從未請求過時，先顯示說明對話框，按「繼續」才請求 POST_NOTIFICATIONS；
     * 按「稍後再說」不請求，下次設定提醒時再說明。必須在 Activity／Fragment 建立時（屬性初始化）呼叫。
     */
    fun register(caller: ActivityResultCaller, owner: LifecycleOwner, context: () -> Context): () -> Unit {
        val launcher = caller.registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
        return prompt@{
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@prompt
            val ctx = context()
            if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return@prompt
            val settings = (ctx.applicationContext as HometideApp).container.settingsStore
            owner.lifecycleScope.launch {
                if (settings.notificationPermissionAsked.first()) return@launch
                MaterialAlertDialogBuilder(ctx)
                    .setTitle(R.string.notify_permission_title)
                    .setMessage(R.string.notify_permission_body)
                    .setNegativeButton(R.string.btn_later, null)
                    .setPositiveButton(R.string.btn_continue) { _, _ ->
                        owner.lifecycleScope.launch { settings.setNotificationPermissionAsked(true) }
                        launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    .show()
            }
        }
    }
}
