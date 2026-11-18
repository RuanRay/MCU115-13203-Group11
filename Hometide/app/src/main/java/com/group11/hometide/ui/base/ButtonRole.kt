package com.group11.hometide.ui.base

import android.view.View
import android.widget.Button
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/** NFR-07：文字樣式的可點元件，讀屏時仍讀成「按鈕」 */
object ButtonRole : AccessibilityDelegateCompat() {
    override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
        super.onInitializeAccessibilityNodeInfo(host, info)
        info.className = Button::class.java.name
    }
}

fun View.asButtonForAccessibility() = ViewCompat.setAccessibilityDelegate(this, ButtonRole)
