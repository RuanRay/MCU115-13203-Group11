package com.group11.hometide.data.remote

import com.google.gson.Gson
import com.group11.hometide.BuildConfig
import com.group11.hometide.data.remote.dto.ApiEnvelope
import com.group11.hometide.data.remote.dto.StationsData
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** 只呼叫自建 Worker（規格 6.2、8.3）；App 不得直接呼叫 TDX。 */
interface HometideApi {

    /** E-7；operator = TRA／THSR */
    @GET("api/stations")
    suspend fun stations(@Query("operator") operator: String): ApiEnvelope<StationsData>

    companion object {
        private const val TIMEOUT_S = 15L

        /** baseUrl 為空（local.properties 未設定）時用不可連線的網址，呼叫會回 NetworkError 而不是當機 */
        fun create(baseUrl: String, gson: Gson): HometideApi {
            val client = OkHttpClient.Builder()
                .connectTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .readTimeout(TIMEOUT_S, TimeUnit.SECONDS)
                .apply {
                    // 只記錄方法、網址、狀態碼與耗時（不含內容）
                    if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
                }
                .build()
            return Retrofit.Builder()
                .baseUrl(baseUrl.ifBlank { "https://worker-url-not-set.invalid" }.trimEnd('/') + "/")
                .client(client)
                .addConverterFactory(GsonConverterFactory.create(gson))
                .build()
                .create(HometideApi::class.java)
        }
    }
}
