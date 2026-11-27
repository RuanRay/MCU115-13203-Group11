package com.group11.hometide.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig

/** 規格 9.1：Firebase AI Logic（Gemini Developer API），一律結構化輸出 */
object GeminiClient {
    /**
     * Firebase AI Logic 模型頁（2026-11-27 查證）的穩定版。
     * 原訂 gemini-3.8-flash 當天持續回「high demand」，實作者改選 Flash-Lite。
     */
    const val MODEL = "gemini-3.5-flash-lite"

    fun jsonModel(schema: Schema): GenerativeModel = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
        modelName = MODEL,
        generationConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = schema
        },
    )
}
