package com.group11.hometide.data.remote

import com.google.gson.Gson
import com.group11.hometide.BuildConfig
import com.group11.hometide.data.remote.dto.ApiEnvelope
import com.group11.hometide.data.remote.dto.CompareData
import com.group11.hometide.data.remote.dto.FreewayMetaData
import com.group11.hometide.data.remote.dto.TrafficData
import com.group11.hometide.data.remote.dto.FareData
import com.group11.hometide.data.remote.dto.LiveData
import com.group11.hometide.data.remote.dto.StationsData
import com.group11.hometide.data.remote.dto.TimetableData
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

/** 只呼叫自建 Worker（規格 6.2、8.3）；App 不得直接呼叫 TDX。 */
interface HometideApi {

    /** E-7；operator = TRA／THSR／BUS */
    @GET("api/stations")
    suspend fun stations(@Query("operator") operator: String): ApiEnvelope<StationsData>

    /** E-1／E-2；BUS 的 from／to 為站名，date＝yyyy-MM-dd */
    @GET("api/timetable")
    suspend fun timetable(
        @Query("operator") operator: String,
        @Query("from") from: String,
        @Query("to") to: String,
        @Query("date") date: String,
    ): ApiEnvelope<TimetableData>

    /** E-8；國道客運省略 route，回傳所有路線 */
    @GET("api/fare")
    suspend fun fare(
        @Query("operator") operator: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiEnvelope<FareData>

    /** E-3；只查今天 */
    @GET("api/live")
    suspend fun live(
        @Query("operator") operator: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiEnvelope<LiveData>

    /** E-9：國道、方向、交流道清單 */
    @GET("api/freeway/meta")
    suspend fun freewayMeta(): ApiEnvelope<FreewayMetaData>

    /** E-4；freeway 為 TDX RoadID，from／to 為交流道名稱 */
    @GET("api/freeway/traffic")
    suspend fun freewayTraffic(
        @Query("freeway") freeway: String,
        @Query("dir") dir: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiEnvelope<TrafficData>

    /** E-5；參數同 E-4 */
    @GET("api/compare")
    suspend fun compare(
        @Query("freeway") freeway: String,
        @Query("dir") dir: String,
        @Query("from") from: String,
        @Query("to") to: String,
    ): ApiEnvelope<CompareData>

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
