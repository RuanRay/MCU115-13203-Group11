package com.group11.hometide.data.repository

import android.util.Log
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.group11.hometide.data.local.SettingsStore
import com.group11.hometide.data.local.dao.UserProfileDao
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.data.local.entity.UserProfile
import com.group11.hometide.util.AppError
import com.group11.hometide.util.Result
import com.group11.hometide.util.await
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.cancellation.CancellationException

/** 帳號（FR-AUTH-01～05）：Firebase Auth＋Firestore users/{uid}＋Room user_profile＋DataStore */
class AuthRepository(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val profiles: UserProfileDao,
    private val settings: SettingsStore,
) {

    /** FR-AUTH-03：以 Firebase currentUser 判斷是否已登入 */
    val currentUid: String? get() = auth.currentUser?.uid

    /** 以 Firebase 登入狀態為準（與 FR-AUTH-03 自動登入一致），不依賴 DataStore 旗標 */
    private val uid: Flow<String?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.uid) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    /** 目前登入者的資料；訪客或未登入為 null。本機沒有資料時以 Email 前綴暫代暱稱 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val profile: Flow<UserProfile?> = uid.distinctUntilChanged().flatMapLatest { uid ->
        if (uid == null) flowOf(null) else profiles.observe(uid).map { it ?: fallbackProfile(uid) }
    }

    /** FR-AUTH-01：Auth 成功即算註冊成功；本機與 Firestore 寫入失敗不影響（Firestore 離線時 SDK 排隊重送） */
    suspend fun register(nickname: String, email: String, password: String, operator: Operator): Result<UserProfile> =
        runAuth(isRegister = true) {
            val uid = requireNotNull(auth.createUserWithEmailAndPassword(email, password).await().user).uid
            val profile = UserProfile(uid, email, nickname, operator, System.currentTimeMillis())
            userDoc(uid).set(profile.toFirestore())
                .addOnFailureListener { Log.w(TAG, "Firestore write failed: ${it.javaClass.simpleName}") }
            saveLocally(profile)
            profile
        }

    /** FR-AUTH-02：Firestore 5 秒內取不到時用本機資料；本機也沒有時暫用 Email 前綴當暱稱 */
    suspend fun login(email: String, password: String): Result<UserProfile> = runAuth {
        val uid = requireNotNull(auth.signInWithEmailAndPassword(email, password).await().user).uid
        val remote = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
            try {
                userDoc(uid).get().await().toProfile(uid)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Firestore read failed: ${e.javaClass.simpleName}")
                null
            }
        }
        val profile = remote ?: profiles.get(uid) ?: fallbackProfile(uid)
        saveLocally(profile)
        profile
    }

    private fun fallbackProfile(uid: String): UserProfile {
        val email = auth.currentUser?.email.orEmpty()
        return UserProfile(uid, email, email.substringBefore('@'), Operator.TRA, System.currentTimeMillis())
    }

    /** 帳號已在 Firebase 建立／登入成功後，本機寫入失敗不可讓整體變成失敗（否則重試會得到 EmailInUse） */
    private suspend fun saveLocally(profile: UserProfile) {
        try {
            profiles.upsert(profile)
            markLoggedIn()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Local save failed: ${e.javaClass.simpleName}")
        }
    }

    /** FR-AUTH-04 */
    suspend fun enterGuest() {
        settings.setLoggedIn(false)
        settings.setGuestMode(true)
    }

    /** FR-AUTH-05：本地收藏與紀錄保留（以 uid 區分） */
    suspend fun logout() {
        // TODO(第 5 週，FR-AUTH-05)：取消該使用者所有 SCHEDULED 提醒的 AlarmManager 排程（ReminderScheduler）
        // 先清本地旗標再登出 Firebase：中途被取消時頂多是「旗標已清、仍登入」，重開 App 仍是一致狀態
        settings.setLoggedIn(false)
        settings.setGuestMode(false)
        auth.signOut()
    }

    private suspend fun markLoggedIn() {
        settings.setLoggedIn(true)
        settings.setGuestMode(false)
    }

    private fun userDoc(uid: String) = firestore.collection("users").document(uid)

    private inline fun <T> runAuth(isRegister: Boolean = false, block: () -> T): Result<T> = try {
        Result.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.Failure(e.toAppError(isRegister))
    }

    /** 「Email 或密碼不正確」只用於登入；註冊時的同類例外（格式已在本機驗證過）視為未知錯誤 */
    private fun Exception.toAppError(isRegister: Boolean): AppError = when {
        this is FirebaseAuthUserCollisionException -> AppError.EmailInUse
        !isRegister && (this is FirebaseAuthInvalidCredentialsException || this is FirebaseAuthInvalidUserException) ->
            AppError.InvalidCredentials
        this is FirebaseTooManyRequestsException -> AppError.TooManyAttempts
        this is FirebaseNetworkException -> AppError.NetworkError
        else -> AppError.Unknown.also { Log.w(TAG, "Auth failed: ${javaClass.simpleName}") }
    }

    private companion object {
        const val TAG = "AuthRepository"
        const val FIRESTORE_TIMEOUT_MS = 5_000L
    }
}

// 7.4 users/{uid}：nickname、email、defaultOperator、createdAt
private fun UserProfile.toFirestore() = mapOf(
    "nickname" to nickname,
    "email" to email,
    "defaultOperator" to defaultOperator.name,
    "createdAt" to createdAt,
)

private fun DocumentSnapshot.toProfile(uid: String): UserProfile? {
    if (!exists()) return null
    return UserProfile(
        uid = uid,
        email = getString("email") ?: return null,
        nickname = getString("nickname") ?: return null,
        defaultOperator = getString("defaultOperator")
            ?.let { runCatching { Operator.valueOf(it) }.getOrNull() } ?: Operator.TRA,
        createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
    )
}
