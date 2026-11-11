package com.group11.hometide.data.local.entity

// 規格 7.1 的固定代碼欄位；Room 以 enum 名稱存成 TEXT

enum class Operator { TRA, THSR, BUS }

enum class SearchSource { MANUAL, FAVORITE, AI }

enum class ReminderStatus { SCHEDULED, FIRED, CANCELLED }

enum class FeedbackCategory { SUGGESTION, BUG, DATA_ERROR, OTHER }

enum class AiStatus { PENDING, DONE, FAILED }

enum class Sentiment { POSITIVE, NEUTRAL, NEGATIVE }

enum class AiQueryResult { SUCCESS, STATION_NOT_FOUND, INVALID_DATE, PARSE_ERROR, TIMEOUT, NETWORK_ERROR, RATE_LIMITED }
