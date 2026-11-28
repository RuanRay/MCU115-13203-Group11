package com.group11.hometide.ui.base

import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityOptions
import android.content.Intent
import android.view.View
import android.view.Window
import java.lang.ref.WeakReference
import androidx.core.content.ContextCompat
import com.google.android.material.transition.platform.MaterialContainerTransform
import com.google.android.material.transition.platform.MaterialContainerTransformSharedElementCallback
import com.group11.hometide.R

/**
 * AN-03 車票展開成結果頁（300 ms）：MaterialContainerTransform 共享元素轉場。
 * 開啟端在 super.onCreate 前呼叫 [enableExpandExit]，結果頁呼叫 [enableExpandEnter]，用 [startExpanding] 開啟。
 * 系統「移除動畫」時一般開啟（TC-33）。
 */
object ExpandTransition {
    private const val NAME = "route_ticket"
    private const val ENTER_MS = 300L
    private const val RETURN_MS = 250L
    private var last: WeakReference<View>? = null

    fun enableExpandExit(activity: Activity) {
        activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        activity.setExitSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        activity.window.sharedElementsUseOverlay = false
    }

    fun enableExpandEnter(activity: Activity) {
        activity.window.requestFeature(Window.FEATURE_ACTIVITY_TRANSITIONS)
        activity.findViewById<View>(android.R.id.content).transitionName = NAME
        activity.setEnterSharedElementCallback(MaterialContainerTransformSharedElementCallback())
        val background = ContextCompat.getColor(activity, R.color.color_background)
        activity.window.sharedElementEnterTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = ENTER_MS
            setAllContainerColors(background)
        }
        activity.window.sharedElementReturnTransition = MaterialContainerTransform().apply {
            addTarget(android.R.id.content)
            duration = RETURN_MS
            setAllContainerColors(background)
        }
    }

    /** [from] 為被點的車票；為 null 或動畫關閉時一般開啟 */
    fun startExpanding(activity: Activity, intent: Intent, from: View?) {
        if (from == null || !from.isAttachedToWindow || !ValueAnimator.areAnimatorsEnabled()) {
            activity.startActivity(intent)
            return
        }
        // RecyclerView 會重用車票：先清掉上一張的名稱，避免返回轉場找到別張
        last?.get()?.takeIf { it !== from }?.transitionName = null
        last = WeakReference(from)
        from.transitionName = NAME
        activity.startActivity(intent, ActivityOptions.makeSceneTransitionAnimation(activity, from, NAME).toBundle())
    }
}
