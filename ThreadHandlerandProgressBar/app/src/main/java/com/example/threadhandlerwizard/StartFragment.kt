package com.example.threadhandlerwizard

import android.os.Bundle
import android.view.View
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.viewpager2.widget.ViewPager2

class StartFragment : Fragment(R.layout.frag_start) {
    private val vm: WorkViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val start = view.findViewById<Button>(R.id.btnStart)
        val cancel = view.findViewById<Button>(R.id.btnCancel)
        start.setOnClickListener {
            vm.start()
            requireActivity().findViewById<ViewPager2>(R.id.pager).currentItem = 1
        }
        cancel.setOnClickListener { vm.cancel() }
        vm.phase.observe(viewLifecycleOwner) {
            start.isEnabled = !vm.isActive
            cancel.isEnabled = vm.isActive
        }
    }
}
