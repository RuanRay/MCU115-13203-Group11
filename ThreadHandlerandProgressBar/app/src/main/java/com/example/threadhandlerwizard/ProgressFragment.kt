package com.example.threadhandlerwizard

import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels

class ProgressFragment : Fragment(R.layout.frag_progress) {
    private val vm: WorkViewModel by activityViewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ring = view.findViewById<ProgressBar>(R.id.progressBar)
        val percent = view.findViewById<TextView>(R.id.txtPercent)
        val status = view.findViewById<TextView>(R.id.txtStatus)

        fun render() {
            val p = vm.progress.value ?: 0
            val phase = vm.phase.value ?: Phase.IDLE
            ring.progress = p
            percent.text = if (phase == Phase.PREPARING) "…" else getString(R.string.percent, p)
            status.setText(
                when (phase) {
                    Phase.IDLE -> R.string.status_idle
                    Phase.PREPARING -> R.string.status_preparing
                    Phase.WORKING -> R.string.status_working
                    Phase.DONE -> R.string.status_done
                    Phase.CANCELED -> R.string.status_canceled
                }
            )
        }
        vm.phase.observe(viewLifecycleOwner) { render() }
        vm.progress.observe(viewLifecycleOwner) { render() }
    }
}
