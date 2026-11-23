package com.group11.automobiledealer

import android.app.Activity
import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import com.group11.automobiledealer.MyDBHelper.Companion.TABLE

class MainActivity : Activity() {
    private val items = ArrayList<String>()
    private lateinit var adapter: ArrayAdapter<String>
    private lateinit var dbrw: SQLiteDatabase
    private lateinit var edBrand: EditText
    private lateinit var edModel: EditText
    private lateinit var edYear: EditText
    private lateinit var edPrice: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        dbrw = MyDBHelper(this).writableDatabase
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        findViewById<ListView>(R.id.listView).adapter = adapter

        edBrand = findViewById(R.id.edBrand)
        edModel = findViewById(R.id.edModel)
        edYear = findViewById(R.id.edYear)
        edPrice = findViewById(R.id.edPrice)
        setListener()
    }

    override fun onDestroy() {
        super.onDestroy()
        dbrw.close()
    }

    private fun brand() = edBrand.text.toString().trim()

    private fun setListener() {
        findViewById<Button>(R.id.btnInsert).setOnClickListener {
            val cv = values() ?: return@setOnClickListener
            try {
                dbrw.insertOrThrow(TABLE, null, cv)
                showToast("新增:${brand()}")
                cleanEditText()
            } catch (e: Exception) {
                showToast("新增失敗(廠牌可能重複):${e.message}")
            }
        }

        findViewById<Button>(R.id.btnUpdate).setOnClickListener {
            val cv = values() ?: return@setOnClickListener
            val n = dbrw.update(TABLE, cv, "brand = ?", arrayOf(brand()))
            showToast(if (n > 0) "修改:${brand()}" else "查無廠牌:${brand()}")
            if (n > 0) cleanEditText()
        }

        findViewById<Button>(R.id.btnDelete).setOnClickListener {
            if (brand().isEmpty()) {
                showToast("廠牌請勿留空")
                return@setOnClickListener
            }
            val n = dbrw.delete(TABLE, "brand = ?", arrayOf(brand()))
            showToast(if (n > 0) "刪除:${brand()}" else "查無廠牌:${brand()}")
            if (n > 0) cleanEditText()
        }

        findViewById<Button>(R.id.btnQuery).setOnClickListener {
            // 廠牌留空查全部，否則模糊查詢廠牌
            val c = if (brand().isEmpty())
                dbrw.rawQuery("SELECT * FROM $TABLE", null)
            else
                dbrw.rawQuery("SELECT * FROM $TABLE WHERE brand LIKE ?", arrayOf("%${brand()}%"))
            items.clear()
            c.use {
                while (it.moveToNext()) {
                    items.add("廠牌:${it.getString(0)}  車型:${it.getString(1)}  年份:${it.getInt(2)}  價格:${it.getInt(3)}")
                }
            }
            adapter.notifyDataSetChanged()
            showToast("共有${items.size}筆資料")
        }
    }

    // 四欄皆必填，年份與價格須為整數；不合格回傳 null
    private fun values(): ContentValues? {
        val model = edModel.text.toString().trim()
        val year = edYear.text.toString().toIntOrNull()
        val price = edPrice.text.toString().toIntOrNull()
        if (brand().isEmpty() || model.isEmpty() || year == null || price == null) {
            showToast("欄位請勿留空，年份與價格須為數字")
            return null
        }
        return ContentValues().apply {
            put("brand", brand()); put("model", model)
            put("year", year); put("price", price)
        }
    }

    private fun showToast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

    private fun cleanEditText() {
        edBrand.setText(""); edModel.setText(""); edYear.setText(""); edPrice.setText("")
    }
}
