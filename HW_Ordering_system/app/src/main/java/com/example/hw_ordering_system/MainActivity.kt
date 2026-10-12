package com.example.hw_ordering_system

import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import com.example.hw_ordering_system.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val vm = ViewModelProvider(this)[OrderViewModel::class.java]

        // 四個副畫面是 pager 裡的 Fragment；只能由主畫面的按鈕進入，不開放左右滑
        binding.pager.adapter = WizardPagerAdapter(this)
        binding.pager.isUserInputEnabled = false

        // 副畫面開著時，返回鍵回主畫面而不是離開 App
        val back = onBackPressedDispatcher.addCallback(this, false) { vm.show(PAGE_NONE) }

        vm.page.observe(this) { page ->
            val open = page != PAGE_NONE
            binding.mainScreen.isVisible = !open
            binding.pager.isVisible = open
            if (open) binding.pager.setCurrentItem(page, false)
            back.isEnabled = open
        }
        vm.order.observe(this) { binding.tvSummary.text = orderSummary(it) }
        vm.submitted.observe(this) {
            binding.tvSummaryTitle.setText(
                if (it) R.string.submitted_order else R.string.current_selection
            )
        }

        binding.btnMainMeal.setOnClickListener { vm.show(PAGE_MAIN_MEAL) }
        binding.btnSideDishes.setOnClickListener { vm.show(PAGE_SIDE_DISHES) }
        binding.btnDrink.setOnClickListener { vm.show(PAGE_DRINK) }
        binding.btnOrder.setOnClickListener { vm.show(PAGE_CONFIRM) }
    }
}
