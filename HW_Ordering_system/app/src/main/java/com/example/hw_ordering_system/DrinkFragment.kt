package com.example.hw_ordering_system

import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import androidx.core.view.children
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hw_ordering_system.databinding.FragmentDrinkBinding

class DrinkFragment : Fragment(R.layout.fragment_drink) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentDrinkBinding.bind(view)
        val vm = ViewModelProvider(requireActivity())[OrderViewModel::class.java]

        // 還原先前選的飲料；用點擊而非勾選事件，還原時才不會誤寫回 ViewModel
        binding.rgDrink.children.filterIsInstance<RadioButton>().forEach { option ->
            option.isChecked = option.label == vm.order.value?.drink
            option.setOnClickListener { vm.update { copy(drink = option.label) } }
        }

        binding.btnDone.setOnClickListener { vm.show(PAGE_NONE) }
    }
}
