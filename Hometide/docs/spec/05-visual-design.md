## 5. 視覺設計規範

視覺主題為「硬式車票」：以早期硬式火車票為意象，用藏青、芥末黃與車票紙米色構成，路線、班次、提醒都做成車票，狀態用印章表示。本章內容已於 2026-11-07 討論確認。

### 5.1 色票（colors.xml 語意名稱）

| 名稱 | 用途 | 淺色 | 深色 |
|---|---|---|---|
| `color_background` | 頁面底色（車票紙） | #F5EEDC | #12161F |
| `color_surface` | 卡片、輸入框、空白票 | #FFFBF1 | #1B2231 |
| `color_surface_variant` | 次要底色、選中的導覽項目 | #EFE3C6 | #232C3E |
| `color_ticket` | 紙票底色 | #F2E3BF | #2B3448 |
| `color_outline` | 邊框、分隔線、撕線 | #D9C9A3 | #36415A |
| `color_on_background` | 主要文字（墨黑） | #2A2A2A | #EFE6D2 |
| `color_on_surface_muted` | 次要文字 | #7A6E58 | #A9A08C |
| `color_primary` | 主色：主票、主要按鈕（藏青） | #23395B | #E0A526 |
| `color_on_primary` | 主色上的文字 | #F5EEDC | #1B1B1B |
| `color_accent` | 強調：評分星、分頁底線、站名已驗證章（芥末黃） | #E0A526 | #EDBE57 |
| `color_stamp` | 印章、已開出、誤點、錯誤（印章紅） | #B23A2E | #E27C6E |
| `color_success` | 準點、即將發車、一致章 | #3E7A4A | #8CC79A |
| `color_facility` | 設施標籤（對號、自行車） | #2F6E6A | #7FC1B8 |

- 深色模式下主色改為芥末黃（主票變成芥末黃底、深色字），讓主票在深色背景上仍是最醒目的元素。
- 文字與底色對比至少 4.5:1；印章紅只用於印章與狀態，不用於大面積底色。

### 5.2 字體、字級與間距

| 樣式 | 字體 | 大小 | 字重 | 用途 |
|---|---|---|---|---|
| Display | 思源宋體（Noto Serif TC） | 22sp | Black | 頁面標題、下一班倒數標題 |
| TicketRoute | 思源宋體 | 18–22sp | Black | 車票上的起訖站 |
| TicketTime | 思源宋體 | 15–17sp | Black | 班次時間、票價 |
| Countdown | 思源宋體 | 40sp | Black | 下一班存根的倒數數字 |
| Body | 思源黑體（Noto Sans TC） | 14sp | Regular | 一般文字 |
| Caption | 思源黑體 | 10–12sp | Medium | 車票小字、標籤、說明 |
| Button | 思源宋體 | 14sp | Bold，字距 2 | 主要按鈕（例：「登　入」「送　出」） |

- 兩套字體皆為 SIL Open Font License，放在 `res/font`，並以 Downloadable Fonts 或子集化控制 APK 大小。
- 間距以 4dp 為單位：頁面左右邊距 14dp、車票間距 8dp、車票內距 10dp。
- 圓角：車票 6dp、按鈕與輸入框 4–6dp、Bottom Sheet 上緣 16dp、浮動輸入列 24dp。
- 撕線：2dp 虛線；缺口半徑 8dp。印章旋轉 −12°。
- 可點擊元件最小 48 × 48dp。

### 5.3 插圖、圖示與用語

- **插圖（VectorDrawable，淺／深色各一版）：** 登入主視覺「疊放車票＋日期戳章」`ill_tickets_stamp`、空狀態「問號車票」`ill_ticket_question`、無班次「空白車票」`ill_ticket_blank`、離線「斷線售票機」`ill_machine_offline`。
- **圖示：** Material Symbols（Rounded），24dp。
- **用語：** 介面主要動作用一般 App 用語（建立帳號、刪除、意見回饋、送出）；主題用語只用於區塊名稱與狀態章，包括「乘車申請單」「我的車票夾」「空白車票」「乘車會員證」「AI 開票」，以及「已開出／已預約／已使用／已收件／會員限定」等印章。

### 5.4 動畫規格

共同原則：

- 時長：微互動 150–250 ms、轉場 250–350 ms、強調動畫 400–900 ms。
- 同一畫面同時只播放一個強調動畫。
- 收藏、撕票、蓋章時觸發觸覺回饋（`HapticFeedbackConstants`）；撕票撕離瞬間使用較強的回饋。
- 系統開啟「移除動畫」（`Settings.Global.ANIMATOR_DURATION_SCALE = 0`）時，全部改為直接切換，功能不受影響。
- 互動式展示頁（動畫提案頁）保留作為實作時的參考。

| 編號 | 名稱 | 觸發 | 效果 | 時長 | Android 實作 |
|---|---|---|---|---|---|
| AN-01 | 售票機出票 | 冷啟動 | 頂部售票機指示燈亮、機器輕震；車票從出票口內分 5 段被送出（出票口以裁切遮罩遮住未出來的部分），完全出來後微微下垂，接著淡入首頁或登入頁 | 約 900 ms | SplashScreen API 結束後的第一個畫面播放；ValueAnimator 分段 translationY＋clipBounds |
| AN-02 | 導覽列左右滑動 | 底部導覽列切換 | 新頁從點擊方向滑入、舊頁往反方向滑出並變淡 | 250 ms | Activity 轉場（overridePendingTransition）或 Fragment 轉場 |
| AN-03 | 車票展開成結果頁 | 由車票夾、下一班進入 P4 | 被點的車票放大展開成結果頁頂部標題 | 300 ms | MaterialContainerTransform（共享元素，transitionName 為路線 ID） |
| AN-04 | AI 開票 | 解析完成 | 確認卡滑上 → 頂端出票口分段印出預覽車票 → 蓋「站名已驗證」章 | 約 1 秒 | BottomSheetDialogFragment＋與 AN-01 共用的出票動畫＋StampView |
| AN-05 | 撕票 | 點車票夾存根 | 存根以左下角為軸，從上端沿鋸齒撕線分三段掀開（每段停頓），車票本體被拉偏；最後撕離飛出並掉落紙屑，本體留下鋸齒邊；接著進行 AN-03 | 約 750 ms | 本體與存根為兩個 View，接縫以 Path 鋸齒裁切；ObjectAnimator 關鍵影格（rotation、translation、alpha）；紙屑為 3 個小 View |
| AN-06 | 收藏愛心 | 點 ♥ | 愛心放大到 1.35 倍再回彈；底部提示列「已加入我的路線」附「復原」 | 250 ms＋提示列 | scaleX/Y＋OvershootInterpolator；Snackbar |
| AN-07 | 設定提醒 | 選好提前分鐘數 | 鈴鐺左右搖晃，存根蓋上「已預約」章 | 400 ms＋220 ms | rotation 關鍵影格；StampView 蓋章（scale 1.8→1、alpha、rotation） |
| AN-08 | 刪除路線 | 左滑放開 | 露出紅色「刪除」，車票滑出畫面，下方車票往上補位；提示列可復原 | 220 ms | ItemTouchHelper＋RecyclerView DefaultItemAnimator |
| AN-09 | 交換起訖站 | 點交換鈕 | 按鈕旋轉 180°，起站與迄站上下交錯換位 | 300 ms | rotation＋兩個 TextView translationY |
| AN-10 | 已收件 | 送出回饋 | 紅色「已收件」郵戳蓋下，AI 結果車票延遲 300 ms 由下往上淡入 | 260 ms＋250 ms | StampView＋translationY/alpha |
| AN-11 | 骨架車票 | 載入中 | 三張車票輪廓緩慢閃光 | 1.2 s 循環 | 漸層位移（ValueAnimator＋LinearGradient） |
| AN-12 | 小火車重新整理 | 結果頁下拉 | 指示器改為小火車沿軌道從左開到右 | 約 1.4 s 循環 | 自訂 SwipeRefreshLayout 指示器 |
| AN-13 | 倒數翻牌 | 下一班每 60 秒 | 分鐘數上下翻牌更新；剩 5 分鐘內變紅並輕微脈動 | 300 ms | 兩個 TextView rotationX 翻轉；scale 脈動循環 |
| AN-14 | 「現在」線推進 | 結果頁每 60 秒 | 紅線往下移，被越過的班次變淡並蓋「已開出」章 | 600 ms＋220 ms | ItemDecoration 位置動畫＋item alpha＋StampView |
| AN-15 | 三點跳動 | AI 解析中、登入處理中 | 三個圓點依序上下跳動 | 900 ms 循環 | 三個 View 錯開 translationY |
| AN-16 | 錯誤與離線 | 驗證失敗、斷線 | 輸入框左右抖動＋紅框＋紅字；離線時頂部滑下芥末黃橫幅 | 300 ms／200 ms | translationX 關鍵影格；橫幅 translationY |
