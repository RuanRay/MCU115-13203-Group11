package com.group11.hometide.ui.feedback

import android.animation.ValueAnimator
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.annotation.StringRes
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.group11.hometide.HometideApp
import com.group11.hometide.R
import com.group11.hometide.data.local.entity.Feedback
import com.group11.hometide.data.local.entity.FeedbackCategory
import com.group11.hometide.data.local.entity.Sentiment
import com.group11.hometide.databinding.ActivityFeedbackBinding
import com.group11.hometide.service.SyncService
import com.group11.hometide.ui.base.BaseActivity
import com.group11.hometide.util.DateUtils
import kotlinx.coroutines.launch

/** P7 意見回饋（規格 4.9；FR-FB-01～03；feedback.png、feedback_done.png） */
class FeedbackActivity : BaseActivity() {

    private lateinit var binding: ActivityFeedbackBinding

    private val vm: FeedbackViewModel by viewModels {
        viewModelFactory { initializer { FeedbackViewModel((application as HometideApp).container.feedbackRepository) } }
    }

    private var stampPlayed = false
    private var cardShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)
        applySystemBarInsets(binding.root)
        stampPlayed = savedInstanceState != null
        cardShown = savedInstanceState != null

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.btnBack.setOnClickListener { finish() }
        binding.spCategory.adapter = ArrayAdapter(this, R.layout.item_feedback_category, FeedbackCategory.entries.map { getString(it.labelRes) })
            .apply { setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.ratingBar.setOnRatingBarChangeListener { _, _, _ -> updateSubmit() }
        binding.etContentInput.doAfterTextChanged { updateSubmit() }
        binding.btnSubmit.setOnClickListener {
            vm.submit(
                binding.ratingBar.rating.toInt(),
                FeedbackCategory.entries[binding.spCategory.selectedItemPosition],
                binding.etContentInput.text.toString(),
                binding.cbAttachUsage.isChecked,
            )
        }
        updateSubmit()

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { vm.state.collect(::render) }
                launch { vm.errorFlow.collect { showSnackbar(R.string.error_unknown) } }
            }
        }
    }

    /** FR-FB-01：評分 1–5、內容 1–500 字才能送出；計數器「27 / 500」 */
    private fun updateSubmit() {
        val content = binding.etContentInput.text.toString()
        binding.tvContentCount.text = getString(R.string.feedback_counter, content.length, FeedbackRules.MAX_LENGTH)
        binding.btnSubmit.isEnabled = FeedbackRules.canSubmit(binding.ratingBar.rating.toInt(), content) && !vm.state.value.submitting
    }

    private fun render(s: FeedbackViewModel.UiState) {
        binding.btnSubmit.isEnabled = !s.submitting && FeedbackRules.canSubmit(binding.ratingBar.rating.toInt(), binding.etContentInput.text.toString())
        val date = s.receivedOn ?: return
        if (binding.flipperFeedback.displayedChild == 0) {
            binding.flipperFeedback.displayedChild = 1
            SyncService.start(this) // FR-SYNC-01 (1)（重複啟動時 Service 只跑一輪）
            ViewCompat.getWindowInsetsController(binding.root)?.hide(WindowInsetsCompat.Type.ime())
            binding.root.scrollTo(0, 0)
            // 實作者確認：成功頁按返回鍵直接回 P6
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = finish()
            })
        }
        binding.vReceivedStamp.text = getString(R.string.feedback_received_stamp, DateUtils.rocDate(date))
        if (!stampPlayed) {
            stampPlayed = true
            playStamp()
        }
        renderAi(s.ai)
    }

    /** FR-FB-03 */
    private fun renderAi(card: FeedbackViewModel.AiCard) {
        val tk = binding.tkAiResult
        when (card) {
            FeedbackViewModel.AiCard.Waiting -> return
            FeedbackViewModel.AiCard.Hidden -> {
                tk.visibility = View.GONE
                return
            }
            FeedbackViewModel.AiCard.Pending -> {
                binding.tvAiPending.isVisible = true
                binding.gridAiResult.isVisible = false
                binding.vMatchStamp.isVisible = false
                binding.tvAiMaybe.isVisible = false
            }
            is FeedbackViewModel.AiCard.Done -> fillResult(card.feedback)
        }
        if (tk.visibility != View.VISIBLE) fadeIn(tk)
    }

    private fun fillResult(fb: Feedback) {
        binding.tvAiPending.isVisible = false
        binding.gridAiResult.isVisible = true
        binding.tvAiCategory.text = fb.aiCategory?.let { getString(it.labelRes) }
        binding.tvAiSentiment.text = fb.aiSentiment?.let { getString(it.labelRes) }
        binding.tvAiSummary.text = fb.aiSummary
        val match = FeedbackRules.matches(fb)
        binding.vMatchStamp.isVisible = match
        binding.tvAiMaybe.isVisible = !match
        if (!match) binding.tvAiMaybe.text = getString(R.string.feedback_ai_maybe, fb.aiCategory?.let { getString(it.labelRes) }.orEmpty())
    }

    /** AN-10：郵戳蓋下（scale 1.8→1、alpha） */
    private fun playStamp() {
        if (!animationsEnabled) return
        binding.vReceivedStamp.apply {
            scaleX = STAMP_FROM_SCALE
            scaleY = STAMP_FROM_SCALE
            alpha = 0f
            animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(STAMP_MS).start()
        }
    }

    /** AN-10：AI 結果車票延遲 300 ms 後由下往上淡入（250 ms） */
    private fun fadeIn(v: View) {
        v.visibility = View.VISIBLE
        if (!ValueAnimator.areAnimatorsEnabled() || cardShown) return
        cardShown = true
        v.alpha = 0f
        v.translationY = resources.getDimension(R.dimen.feedback_card_rise)
        v.animate().alpha(1f).translationY(0f).setStartDelay(CARD_DELAY_MS).setDuration(CARD_FADE_MS).start()
    }

    private companion object {
        const val STAMP_FROM_SCALE = 1.8f
        const val STAMP_MS = 220L
        const val CARD_DELAY_MS = 300L
        const val CARD_FADE_MS = 250L
    }
}

@get:StringRes
val FeedbackCategory.labelRes: Int
    get() = when (this) {
        FeedbackCategory.SUGGESTION -> R.string.feedback_category_suggestion
        FeedbackCategory.BUG -> R.string.feedback_category_bug
        FeedbackCategory.DATA_ERROR -> R.string.feedback_category_data_error
        FeedbackCategory.OTHER -> R.string.feedback_category_other
    }

@get:StringRes
val Sentiment.labelRes: Int
    get() = when (this) {
        Sentiment.POSITIVE -> R.string.sentiment_positive
        Sentiment.NEUTRAL -> R.string.sentiment_neutral
        Sentiment.NEGATIVE -> R.string.sentiment_negative
    }
