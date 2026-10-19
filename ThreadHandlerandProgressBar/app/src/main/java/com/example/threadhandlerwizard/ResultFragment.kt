package com.example.threadhandlerwizard

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ResultFragment : Fragment(R.layout.frag_result) {
    private val vm: WorkViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val result = view.findViewById<TextView>(R.id.txtResult)
        vm.phase.observe(viewLifecycleOwner) { phase ->
            val (text, color) = when (phase) {
                Phase.IDLE -> R.string.status_idle to R.color.muted
                Phase.PREPARING -> R.string.status_preparing to R.color.amber
                Phase.WORKING -> R.string.status_working to R.color.brand
                Phase.DONE -> R.string.status_done to R.color.green
                Phase.CANCELED -> R.string.status_canceled to R.color.red
            }
            result.setText(text)
            result.setTextColor(ContextCompat.getColor(requireContext(), color))
        }
    }
}
