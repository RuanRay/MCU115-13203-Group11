package com.example.myapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.myapplication.ui.theme.MyApplicationTheme

// MainActivity 是應用程式的主要進入點（頁面）
class MainActivity : ComponentActivity() {

    // onCreate 在 Activity 建立時呼叫，用來初始化畫面與設定內容
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 啟用滿版/沉浸式顯示 (Edge-to-Edge)，讓 UI 能延伸至狀態列與導覽列下方
        enableEdgeToEdge()

        // 設定應用程式的內容 UI（進入 Jetpack Compose 的入口）
        setContent {
            // 套用應用程式的主題樣式（色彩、字型等配置）
            MyApplicationTheme {
                // Scaffold 提供 Material Design 的基本頁面結構，會自動處理邊距 (innerPadding)
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 呼叫自訂的 Greeting 元件，傳入名字與 Scaffold 產生的邊距
                    Greeting(
                        name = "Word",
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * 顯示問候語的 UI 元件 (@Composable)
 *
 * @param name 要顯示的名字
 * @param modifier 佈局修飾符，用於設定大小、外框或外距等，預設為空的 Modifier
 */
@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    // Box 為容器元件，將內部子元件疊放；這裡設定填滿最大畫面且內容置中
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Text 用於繪製文字
        Text(
            text = "Hello $name!", // 字串範本，會將變數 name 的值嵌入字串中
            fontSize = 60.sp,      // 設定文字大小為 60sp
            color = Color.Red      // 設定文字顏色為紅色
        )
    }
}

/**
 * UI 預覽元件
 * 加上 @Preview 標籤後，無需將 App 安裝至手機，即可在 Android Studio 右側 Preview 視窗中預覽畫面
 */
@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme {
        Greeting("Word")
    }
}
