package com.group11.hometide.provider

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.group11.hometide.HometideApp
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.util.StationNameNormalizer

/**
 * 車站查詢（規格 10.3、FR-SRCH-02）。唯讀、不對外開放。
 * 查詢條件只從 URI 參數 operator、q 取得並以參數綁定查詢，忽略 selection。
 * Room 不允許主執行緒查詢：請在背景執行緒呼叫（SimpleCursorAdapter 的 FilterQueryProvider 本來就在背景）。
 */
class StationProvider : ContentProvider() {

    // Provider 的 onCreate 早於 Application.onCreate，所以延到查詢時才取 container
    override fun onCreate(): Boolean = true

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?,
    ): Cursor {
        require(MATCHER.match(uri) == STATIONS) { "Unknown URI" }
        val operator = uri.getQueryParameter(PARAM_OPERATOR)
            ?.let { runCatching { Operator.valueOf(it) }.getOrNull() }
            ?: throw IllegalArgumentException("Missing or invalid operator")
        val keyword = StationNameNormalizer.normalizeQuery(uri.getQueryParameter(PARAM_QUERY).orEmpty())
        val ctx = requireNotNull(context)
        val dao = (ctx.applicationContext as HometideApp).container.database.stationDao()
        return dao.searchCursor(operator, keyword).apply { setNotificationUri(ctx.contentResolver, uri) }
    }

    override fun getType(uri: Uri): String? =
        if (MATCHER.match(uri) == STATIONS) "vnd.android.cursor.dir/vnd.com.group11.hometide.station" else null

    override fun insert(uri: Uri, values: ContentValues?): Uri = throw UnsupportedOperationException("Read-only")

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only")

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int =
        throw UnsupportedOperationException("Read-only")

    companion object {
        const val AUTHORITY = "com.group11.hometide.stations"
        val CONTENT_URI: Uri = Uri.parse("content://$AUTHORITY/stations")
        const val PARAM_OPERATOR = "operator"
        const val PARAM_QUERY = "q"

        private const val STATIONS = 1
        private val MATCHER = UriMatcher(UriMatcher.NO_MATCH).apply { addURI(AUTHORITY, "stations", STATIONS) }

        fun queryUri(operator: Operator, keyword: String): Uri = CONTENT_URI.buildUpon()
            .appendQueryParameter(PARAM_OPERATOR, operator.name)
            .appendQueryParameter(PARAM_QUERY, keyword)
            .build()
    }
}
