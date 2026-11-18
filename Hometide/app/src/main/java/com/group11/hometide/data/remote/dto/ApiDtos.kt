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

/** E-1／E-2 /api/timetable */
data class TimetableData(
    val operator: String?,
    val trips: List<TripDto>?,
)

data class TripDto(
    val tripId: String?,
    val typeCode: String?,
    val typeName: String?,
    val line: Int?,
    val departTime: String?,
    val arriveTime: String?,
    val durationMin: Int?,
    val arriveEstimated: Boolean?,
    val reservedSeat: Boolean?,
    val bikeAllowed: Boolean?,
)

/** E-8 /api/fare（CR-01：每個車種／路線 × 票種一筆；查無的票種不出現） */
data class FareData(
    val operator: String?,
    val fares: List<FareDto>?,
)

data class FareDto(
    val typeCode: String?,
    val fareClass: String?,
    val price: Int?,
    val approx: Boolean?,
)

/** E-3 /api/live：台鐵 delayMin、國道客運 etaMin */
data class LiveData(val items: List<LiveItemDto>?)

data class LiveItemDto(
    val tripId: String?,
    val delayMin: Int?,
    val etaMin: Int?,
)

data class StationDto(
    val id: String?,
    val nameZh: String?,
    val nameEn: String?,
    val city: String?,
    val sortKey: Int?,
)
