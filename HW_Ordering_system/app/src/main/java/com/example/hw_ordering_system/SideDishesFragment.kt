package com.example.hw_ordering_system

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hw_ordering_system.databinding.FragmentSideDishesBinding

class SideDishesFragment : Fragment(R.layout.fragment_side_dishes) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentSideDishesBinding.bind(view)
        val vm = ViewModelProvider(requireActivity())[OrderViewModel::class.java]

        val options = binding.llSideDishes.children.filterIsInstance<CheckBox>()

        // 還原先前勾的副餐；全部取消也照實存成空清單，「至少一項」留給確認頁驗證
        val current = vm.order.value?.sideDishes.orEmpty()
        options.forEach { option ->
            option.isChecked = option.label in current
            option.setOnClickListener {
                val picked = options.filter { it.isChecked }.map { it.label }.toList()
                vm.update { copy(sideDishes = picked) }
            }
        }

        binding.btnDone.setOnClickListener { vm.show(PAGE_NONE) }
    }
}
