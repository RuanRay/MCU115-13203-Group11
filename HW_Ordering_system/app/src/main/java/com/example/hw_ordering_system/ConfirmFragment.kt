package com.example.hw_ordering_system

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.hw_ordering_system.databinding.FragmentConfirmBinding

class ConfirmFragment : Fragment(R.layout.fragment_confirm) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val binding = FragmentConfirmBinding.bind(view)
        val vm = ViewModelProvider(requireActivity())[OrderViewModel::class.java]
        val context = requireContext()

        vm.order.observe(viewLifecycleOwner) { binding.tvSummary.text = context.orderSummary(it) }

        binding.btnBack.setOnClickListener { vm.show(PAGE_NONE) }

        binding.btnConfirm.setOnClickListener {
            val order = vm.order.value
            // 任何一項沒選：只跳 Toast，不開對話框
            if (order?.isComplete() != true) {
                Toast.makeText(context, R.string.order_incomplete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            // 都有選：列出所選項目，提交後回主畫面顯示
            AlertDialog.Builder(context)
                .setTitle(R.string.submit_order)
                .setMessage(context.orderSummary(order))
                .setPositiveButton(R.string.submit) { _, _ -> vm.submit() }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }
}
