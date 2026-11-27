package com.group11.hometide.data.ai

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.firebase.ai.type.Schema
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/** 6-3 連通測試：App Check（Debug）＋ AI Logic＋結構化輸出；會實際呼叫 Gemini 一次 */
@RunWith(AndroidJUnit4::class)
class GeminiSmokeTest {
    @Test
    fun structuredOutput_roundTrip() = runBlocking {
        val schema = Schema.obj(mapOf("answer" to Schema.integer()))
        val text = GeminiClient.jsonModel(schema).generateContent("1 + 2 = ? Reply with the number in answer.").text
        assertEquals(3, JSONObject(text!!).getInt("answer"))
    }
}
