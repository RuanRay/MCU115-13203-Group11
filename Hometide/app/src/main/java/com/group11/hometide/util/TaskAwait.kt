package com.group11.hometide.util

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 把 Firebase／Play services 的 Task 轉成 suspend（免加 kotlinx-coroutines-play-services 依賴） */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        when {
            task.isCanceled -> cont.cancel(CancellationException("Task cancelled"))
            task.isSuccessful -> cont.resume(task.result)
            else -> cont.resumeWithException(task.exception ?: IllegalStateException("Task failed"))
        }
    }
}
