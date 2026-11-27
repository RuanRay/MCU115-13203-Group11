package com.group11.hometide

import com.google.firebase.Firebase
import com.google.firebase.appcheck.appCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/** 規格 9.1：開發與 Demo 用 Debug Provider；Logcat 印出的 debug token 要登記到 Firebase 主控台 */
object AppCheckInstaller {
    fun install() = Firebase.appCheck.installAppCheckProviderFactory(DebugAppCheckProviderFactory.getInstance())
}
