package app.aaps.wear.interaction.actions

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.wear.R
import dagger.android.support.AndroidSupportInjection
import dagger.android.support.DaggerFragment
import java.text.DecimalFormat
import javax.inject.Inject

class WizardConfirmFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus

    private val decimalFormat = DecimalFormat("0.00")
    private var timestamp: Long = 0
    private var confirmationSent = false  // Prevent double-clicking

    companion object {
        private const val ARG_TIMESTAMP = "timestamp"
        private const val ARG_TOTAL_INSULIN = "total_insulin"
        private const val ARG_CARBS = "carbs"

        fun newInstance(timestamp: Long, totalInsulin: Double, carbs: Int): WizardConfirmFragment {
            return WizardConfirmFragment().apply {
                arguments = Bundle().apply {
                    putLong(ARG_TIMESTAMP, timestamp)
                    putDouble(ARG_TOTAL_INSULIN, totalInsulin)
                    putInt(ARG_CARBS, carbs)
                }
            }
        }
    }

    override fun onAttach(context: Context) {
        AndroidSupportInjection.inject(this)
        super.onAttach(context)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_wizard_confirm, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = arguments ?: return

        timestamp = args.getLong(ARG_TIMESTAMP)
        val totalInsulin = args.getDouble(ARG_TOTAL_INSULIN)
        val carbs = args.getInt(ARG_CARBS)

        view.findViewById<TextView>(R.id.confirm_total_insulin).text =
            getString(R.string.wizard_insulin_format, decimalFormat.format(totalInsulin))
        view.findViewById<TextView>(R.id.confirm_carbs).text =
            getString(R.string.wizard_carbs_format, carbs)

        view.findViewById<ImageView>(R.id.confirm_button).setOnClickListener { button ->
            // Prevent double-clicking
            if (confirmationSent) return@setOnClickListener
            confirmationSent = true

            // Disable button to prevent further clicks
            button.isClickable = false

            // Send confirmation to phone
            rxBus.send(EventWearToMobile(EventData.ActionWizardConfirmed(timestamp)))

            // Visual feedback: scale up the checkmark
            button.animate()
                .scaleX(1.2f)
                .scaleY(1.2f)
                .setDuration(150)
                .withEndAction {
                    // Fade out the entire view
                    view.animate()
                        .alpha(0f)
                        .setDuration(250)
                        .withEndAction {
                            // Check if fragment is still attached before finishing activity
                            if (isAdded && !isDetached) {
                                requireActivity().finish()
                            }
                        }
                        .start()
                }
                .start()
        }
    }
}