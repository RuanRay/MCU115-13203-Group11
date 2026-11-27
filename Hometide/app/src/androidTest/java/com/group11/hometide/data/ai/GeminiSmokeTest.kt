package com.group11.hometide.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.Tasks
import com.google.firebase.Firebase
import com.google.firebase.ai.type.Schema
import com.google.firebase.appcheck.appCheck
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit
import org.junit.runner.RunWith

/** 6-3 連通測試：App Check（Debug）＋ AI Logic＋結構化輸出；會實際呼叫 Gemini 一次 */
@RunWith(AndroidJUnit4::class)
class GeminiSmokeTest {
    /** debug token 未登記時這裡會失敗（403），與 Gemini 無關 */
    @Test
    fun appCheck_tokenExchange() {
        Tasks.await(Firebase.appCheck.getAppCheckToken(true), 30, TimeUnit.SECONDS)
    }

    @Test
    fun structuredOutput_roundTrip() = runBlocking {
        val schema = Schema.obj(mapOf("answer" to Schema.integer()))
        val text = GeminiClient.jsonModel(schema).generateContent("1 + 2 = ? Reply with the number in answer.").text
        assertEquals(3, JSONObject(text!!).getInt("answer"))
    }
}
