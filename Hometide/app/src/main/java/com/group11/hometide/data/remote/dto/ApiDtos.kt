package com.group11.hometide.data.remote.dto

// Worker 回應格式（規格 8.3、GUIDE 附錄 E）。Gson 不保證非 null，使用前須驗證。

/** 成功外殼 {updatedAt, source, data} */
data class ApiEnvelope<T>(
    val updatedAt: Long?,
    val source: String?,
    val data: T?,
)

/** 錯誤外殼 {error: {code, message}}（E-6） */
data class ApiErrorBody(val error: ApiErrorDetail?)

data class ApiErrorDetail(val code: String?, val message: String?)

/** E-7 /api/stations */
data class StationsData(
    val operator: String?,
    val stations: List<StationDto>?,
)

data class StationDto(
    val id: String?,
    val nameZh: String?,
    val nameEn: String?,
    val city: String?,
    val sortKey: Int?,
)
