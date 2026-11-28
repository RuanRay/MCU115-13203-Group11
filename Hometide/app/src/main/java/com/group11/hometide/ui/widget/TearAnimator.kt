package com.group11.hometide.ui.widget

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.Keyframe
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.core.view.drawToBitmap

/**
 * AN-05 撕票（約 750 ms，規格 5.4、animations.html）：把車票截圖沿鋸齒撕線切成本體與存根，蓋在畫面最上層播放。
 * 存根以左下角為軸分三段掀開（每段停頓），本體被拉偏；撕離瞬間輕震並飛出、掉出 3 片紙屑。
 * 播放期間原本的車票隱藏；結束後呼叫 [onTorn]，由呼叫端開啟結果頁（AN-03）。不改動 TicketView 本身。
 */
object TearAnimator {

    private const val DURATION_MS = 760L
    private const val FIBER_DELAY_MS = 540L
    private const val FIBER_MS = 450L
    private const val TOOTH_COUNT = 9

    /** [stubWidth]：存根寬度（px），撕線在右緣往左這個距離 */
    fun play(ticket: View, stubWidth: Int, onTorn: () -> Unit) {
        val content = ticket.rootView.findViewById<ViewGroup>(android.R.id.content)
        if (!ValueAnimator.areAnimatorsEnabled() || content == null || ticket.width == 0) {
            onTorn()
            return
        }
        val full = ticket.drawToBitmap()
        val w = full.width
        val h = full.height
        val seam = (w - stubWidth).toFloat()
        val tooth = h / (TOOTH_COUNT * 2f)
        val depth = resolveDp(ticket, 4f)

        // 本體：右緣為鋸齒；存根：左緣為對應的鋸齒
        val mainPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(seam, 0f)
            for (i in 0 until TOOTH_COUNT * 2) lineTo(if (i % 2 == 0) seam - depth else seam, tooth * (i + 1))
            lineTo(0f, h.toFloat())
            close()
        }
        val stubPath = Path().apply {
            moveTo(seam, 0f)
            lineTo(w.toFloat(), 0f)
            lineTo(w.toFloat(), h.toFloat())
            lineTo(seam, h.toFloat())
            for (i in TOOTH_COUNT * 2 - 1 downTo 0) lineTo(if (i % 2 == 0) seam - depth else seam, tooth * i)
            close()
        }
        val fiberColor = full.getPixel((seam - depth * 2).toInt().coerceIn(0, w - 1), h / 2)

        val loc = IntArray(2).also(ticket::getLocationInWindow)
        val base = IntArray(2).also(content::getLocationInWindow)
        val layer = FrameLayout(ticket.context)
        content.addView(layer, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        // 位置用邊距（x/y 其實是 translation，會被下面的位移動畫蓋掉）
        val left = loc[0] - base[0]
        val top = loc[1] - base[1]
        fun at(width: Int, height: Int, dx: Int, dy: Int) =
            FrameLayout.LayoutParams(width, height).apply { leftMargin = left + dx; topMargin = top + dy }
        fun piece(path: Path) = ImageView(ticket.context).apply {
            setImageBitmap(clip(full, path))
            layoutParams = at(w, h, 0, 0)
            layer.addView(this)
        }
        val main = piece(mainPath)
        val stub = piece(stubPath).apply {
            pivotX = seam + stubWidth * 0.1f
            pivotY = h.toFloat()
        }
        val fibers = listOf(Triple(0f, 18f to -26f, 80f), Triple(0.35f, 26f to -10f, 140f), Triple(0.7f, 14f to 18f, -90f)).map { (yf, d, r) ->
            View(ticket.context).apply {
                setBackgroundColor(fiberColor)
                layoutParams = at(resolveDp(ticket, 5f).toInt(), resolveDp(ticket, 3f).toInt(), seam.toInt(), (h * yf + resolveDp(ticket, 10f)).toInt())
                alpha = 0f
                layer.addView(this)
            } to Triple(resolveDp(ticket, d.first), resolveDp(ticket, d.second), r)
        }
        ticket.alpha = 0f

        val dp = { v: Float -> resolveDp(ticket, v) }
        // animations.html @keyframes peel：掀開三段（每段停頓）→ 撕離飛出
        val peel = ObjectAnimator.ofPropertyValuesHolder(
            stub,
            frames(View.ROTATION, 0f to 0f, .12f to 4f, .20f to 3f, .36f to 9f, .44f to 8f, .58f to 15f, .66f to 17f, .72f to 24f, 1f to 60f),
            frames(View.TRANSLATION_X, 0f to 0f, .36f to dp(1f), .58f to dp(3f), .66f to dp(4f), .72f to dp(14f), 1f to dp(90f)),
            frames(View.TRANSLATION_Y, 0f to 0f, .58f to -dp(1f), .72f to -dp(8f), 1f to dp(120f)),
            frames(View.ALPHA, 0f to 1f, .72f to 1f, 1f to 0f),
        ).setDuration(DURATION_MS).apply { interpolator = LinearInterpolator() }
        // @keyframes resist：本體被拉偏後回正
        val resist = ObjectAnimator.ofPropertyValuesHolder(
            main,
            frames(View.TRANSLATION_X, 0f to 0f, .12f to -dp(1f), .36f to -dp(2f), .66f to -dp(3f), .72f to dp(1f), .80f to 0f, 1f to 0f),
            frames(View.ROTATION, 0f to 0f, .36f to -.6f, .66f to -.9f, .72f to 0f, 1f to 0f),
        ).setDuration(DURATION_MS).apply { interpolator = LinearInterpolator() }
        val fly = fibers.map { (v, d) ->
            ObjectAnimator.ofPropertyValuesHolder(
                v,
                frames(View.ALPHA, 0f to 0f, .05f to 1f, 1f to 0f),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f, d.first),
                PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f, d.second),
                PropertyValuesHolder.ofFloat(View.ROTATION, 0f, d.third),
            ).apply {
                startDelay = FIBER_DELAY_MS
                duration = FIBER_MS
                interpolator = DecelerateInterpolator()
            }
        }
        // 撕離瞬間（72%）輕震；遵守系統觸覺設定、不需權限
        ticket.postDelayed({
            ticket.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY,
            )
        }, (DURATION_MS * 0.72f).toLong())

        // 撕離（約 760 ms）就交給呼叫端開結果頁；紙屑繼續飄完再移除覆蓋層
        peel.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                ticket.alpha = 1f
                main.visibility = View.INVISIBLE
                onTorn()
            }
        })
        AnimatorSet().apply {
            playTogether(listOf(peel, resist) + fly)
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) = content.removeView(layer)
            })
            start()
        }
    }

    private fun frames(prop: android.util.Property<View, Float>, vararg kf: Pair<Float, Float>): PropertyValuesHolder =
        PropertyValuesHolder.ofKeyframe(prop, *kf.map { (t, v) -> Keyframe.ofFloat(t, v) }.toTypedArray())

    private fun clip(src: Bitmap, path: Path): Bitmap = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888).also { out ->
        Canvas(out).apply {
            clipPath(path)
            drawBitmap(src, 0f, 0f, null)
        }
    }

    private fun resolveDp(v: View, dp: Float) = dp * v.resources.displayMetrics.density
}
