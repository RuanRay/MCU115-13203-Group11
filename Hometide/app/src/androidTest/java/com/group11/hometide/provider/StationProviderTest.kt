package com.group11.hometide.provider

import android.content.ContentValues
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.group11.hometide.HometideApp
import com.group11.hometide.data.local.entity.Operator
import com.group11.hometide.util.Result
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 【整合測試】TC-06 端到端：從正式 Worker 下載台鐵車站 → 經 StationProvider 查「台南」→ 出現「臺南」。
 * 會連到正式 Worker 並寫入 App 的資料庫（與 App 啟動時的背景下載相同），網路不穩時可能失敗。
 * 需要網路與 local.properties 的 WORKER_BASE_URL。
 */
@RunWith(AndroidJUnit4::class)
class StationProviderTest {

    private val app = ApplicationProvider.getApplicationContext<HometideApp>()
    private val resolver = app.contentResolver

    private fun names(operator: Operator, q: String): List<String> =
        resolver.query(StationProvider.queryUri(operator, q), null, null, null, null)!!.use { c ->
            val col = c.getColumnIndexOrThrow("name_zh")
            buildList { while (c.moveToNext()) add(c.getString(col)) }
        }

    @Test
    fun tc06_downloadThenQueryTaiNan() = runBlocking {
        val res = app.container.transitRepository.refreshStations(Operator.TRA, force = true)
        assertTrue("download failed: $res", res is Result.Success && res.data > 0)

        val found = names(Operator.TRA, "台南")
        assertTrue("got $found", "臺南" in found)
        assertTrue(found.size <= 20)
        // 前綴相符排前面
        assertEquals("臺南", found.first())
        // 英文站名也可比對（不分大小寫）
        assertTrue("臺南" in names(Operator.TRA, "tainan"))
        // 結尾「站」會被去掉
        assertTrue("臺南" in names(Operator.TRA, "台南站"))
    }

    @Test
    fun cursorColumns_matchSpec() {
        resolver.query(StationProvider.queryUri(Operator.TRA, ""), null, null, null, null)!!.use { c ->
            assertEquals(listOf("_id", "station_id", "name_zh", "name_en", "operator"), c.columnNames.toList())
        }
    }

    @Test
    fun getType() =
        assertEquals("vnd.android.cursor.dir/vnd.com.group11.hometide.station", resolver.getType(StationProvider.CONTENT_URI))

    @Test(expected = UnsupportedOperationException::class)
    fun insert_isReadOnly() {
        resolver.insert(StationProvider.CONTENT_URI, ContentValues())
    }

    @Test(expected = IllegalArgumentException::class)
    fun missingOperator_throws() {
        resolver.query(StationProvider.CONTENT_URI, null, null, null, null)
    }
}
