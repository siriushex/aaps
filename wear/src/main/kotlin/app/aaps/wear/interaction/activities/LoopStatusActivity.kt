package app.aaps.wear.interaction.activities

import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventWearToMobile
import app.aaps.core.interfaces.rx.weardata.EventData
import app.aaps.core.interfaces.rx.weardata.LoopStatusData
import app.aaps.core.interfaces.rx.weardata.TempTargetInfo
import app.aaps.core.interfaces.rx.weardata.TargetRange
import app.aaps.core.interfaces.rx.weardata.OapsResultInfo
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.wear.R
import dagger.android.AndroidInjection
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class LoopStatusActivity : AppCompatActivity() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var dateUtil: DateUtil

    private val disposable = CompositeDisposable()

    // Main views
    private lateinit var loadingView: ProgressBar
    private lateinit var contentView: android.widget.ScrollView
    private lateinit var errorView: TextView

    // Header section
    private lateinit var loopModeText: TextView
    private lateinit var apsNameText: TextView

    // Loop Result section (merged loop info + oaps)
    private lateinit var loopResultCard: View
    private lateinit var lastRunEnactCombinedRow: View
    private lateinit var lastRunEnactCombinedValue: TextView
    private lateinit var lastRunRow: View
    private lateinit var lastRunValue: TextView
    private lateinit var lastEnactRow: View
    private lateinit var lastEnactValue: TextView

    // OpenAPS result section
    private lateinit var oapsSmbRow: View
    private lateinit var oapsSmbValue: TextView
    private lateinit var oapsStatusText: TextView
    private lateinit var oapsRateRow: View
    private lateinit var oapsRateValue: TextView
    private lateinit var oapsDurationRow: View
    private lateinit var oapsDurationValue: TextView
    private lateinit var oapsReasonContainer: View
    private lateinit var oapsReasonLabel: TextView
    private lateinit var oapsReasonToggle: TextView
    private lateinit var oapsReasonText: TextView
    private var isReasonExpanded = false

    // Targets section
    private lateinit var targetsCard: View
    private lateinit var tempTargetContainer: View
    private lateinit var tempTargetValue: TextView
    private lateinit var tempTargetDuration: TextView
    private lateinit var defaultRangeRow: View
    private lateinit var defaultRangeValue: TextView
    private lateinit var defaultTargetValue: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        AndroidInjection.inject(this)
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_loop_status)

        initViews()

        // Request detailed status from phone
        aapsLogger.debug(LTag.WEAR, "Requesting detailed loop status")
        rxBus.send(EventWearToMobile(EventData.ActionLoopStatusDetailed(System.currentTimeMillis())))

        // Listen for response
        disposable += rxBus
            .toObservable(EventData.LoopStatusResponse::class.java)
            .subscribe({ event ->
                           aapsLogger.debug(LTag.WEAR, "Received loop status response")
                           runOnUiThread {
                               displayStatus(event.data)
                           }
                       }, { error ->
                           aapsLogger.error(LTag.WEAR, "Error receiving loop status", error)
                           runOnUiThread {
                               showError("Failed to load status")
                           }
                       })
    }

    override fun onDestroy() {
        super.onDestroy()
        disposable.clear()
    }

    private fun initViews() {
        loadingView = findViewById(R.id.loading_progress)
        contentView = findViewById(R.id.content_container)
        errorView = findViewById(R.id.error_text)

        // Header
        loopModeText = findViewById(R.id.loop_mode_text)
        apsNameText = findViewById(R.id.aps_name_text)

        // Loop Result card
        loopResultCard = findViewById(R.id.loop_result_card)
        lastRunEnactCombinedRow = findViewById(R.id.last_run_enact_combined_row)
        lastRunEnactCombinedValue = findViewById(R.id.last_run_enact_combined_value)
        lastRunRow = findViewById(R.id.last_run_row)
        lastRunValue = findViewById(R.id.last_run_value)
        lastEnactRow = findViewById(R.id.last_enact_row)
        lastEnactValue = findViewById(R.id.last_enact_value)

        // OpenAPS result
        oapsSmbRow = findViewById(R.id.oaps_smb_row)
        oapsSmbValue = findViewById(R.id.oaps_smb_value)
        oapsStatusText = findViewById(R.id.oaps_status_text)
        oapsRateRow = findViewById(R.id.oaps_rate_row)
        oapsRateValue = findViewById(R.id.oaps_rate_value)
        oapsDurationRow = findViewById(R.id.oaps_duration_row)
        oapsDurationValue = findViewById(R.id.oaps_duration_value)
        oapsReasonContainer = findViewById(R.id.oaps_reason_container)
        oapsReasonLabel = findViewById(R.id.oaps_reason_label)
        oapsReasonToggle = findViewById(R.id.oaps_reason_toggle)
        oapsReasonText = findViewById(R.id.oaps_reason_text)

        // Setup click listener for expandable reason
        oapsReasonContainer.setOnClickListener {
            isReasonExpanded = !isReasonExpanded
            oapsReasonText.visibility = if (isReasonExpanded) View.VISIBLE else View.GONE
            oapsReasonToggle.text = if (isReasonExpanded) "▲" else "▼"
        }

        // Targets
        targetsCard = findViewById(R.id.targets_card)
        tempTargetContainer = findViewById(R.id.temp_target_container)
        tempTargetValue = findViewById(R.id.temp_target_value)
        tempTargetDuration = findViewById(R.id.temp_target_duration)
        defaultRangeRow = findViewById(R.id.default_range_row)
        defaultRangeValue = findViewById(R.id.default_range_value)
        defaultTargetValue = findViewById(R.id.default_target_value)
    }

    private fun displayStatus(data: LoopStatusData) {
        // Hide loading, show content
        loadingView.visibility = View.GONE
        errorView.visibility = View.GONE
        contentView.visibility = View.VISIBLE

        // Display all sections
        displayLoopMode(data.loopMode, data.apsName)
        displayLoopInfo(data.lastRun, data.lastEnact)
        data.oapsResult?.let { displayOapsResult(it) }
        displayTargets(data.tempTarget, data.defaultRange)
    }

    private fun showError(message: String) {
        loadingView.visibility = View.GONE
        contentView.visibility = View.GONE
        errorView.visibility = View.VISIBLE
        errorView.text = message
    }

    private fun displayLoopMode(mode: LoopStatusData.LoopMode, apsName: String?) {
        val (text, colorRes) = when (mode) {
            LoopStatusData.LoopMode.CLOSED -> "CLOSED LOOP" to R.color.loopClosed
            LoopStatusData.LoopMode.OPEN -> "OPEN LOOP" to R.color.loopOpen
            LoopStatusData.LoopMode.LGS -> "LGS MODE" to R.color.loopLGS
            LoopStatusData.LoopMode.DISABLED -> "LOOP DISABLED" to R.color.loopDisabled
            LoopStatusData.LoopMode.SUSPENDED -> "LOOP SUSPENDED" to R.color.loopSuspended
            LoopStatusData.LoopMode.DISCONNECTED -> "PUMP DISCONNECTED" to R.color.loopDisconnected
            LoopStatusData.LoopMode.UNKNOWN -> "UNKNOWN" to R.color.loopUnknown
        }

        loopModeText.text = text
        loopModeText.setTextColor(ContextCompat.getColor(this, colorRes))

        if (apsName != null) {
            apsNameText.text = apsName
            apsNameText.visibility = View.VISIBLE
        } else {
            apsNameText.visibility = View.GONE
        }
    }

    private fun displayLoopInfo(lastRun: Long?, lastEnact: Long?) {
        if (lastRun != null) {
            val runTimeString = dateUtil.timeString(lastRun)
            val runAgeMs = System.currentTimeMillis() - lastRun
            val runColor = ContextCompat.getColor(this, getAgeColorRes(runAgeMs))

            if (lastEnact != null) {
                val enactTimeString = dateUtil.timeString(lastEnact)
                val enactAgeMs = System.currentTimeMillis() - lastEnact

                // Compare only HH:mm part of times
                if (runTimeString == enactTimeString) {
                    // Show combined row
                    lastRunEnactCombinedRow.visibility = View.VISIBLE
                    lastRunEnactCombinedValue.text = runTimeString
                    lastRunEnactCombinedValue.setTextColor(runColor)

                    // Hide separate rows
                    lastRunRow.visibility = View.GONE
                    lastEnactRow.visibility = View.GONE
                } else {
                    // Show separate rows
                    lastRunEnactCombinedRow.visibility = View.GONE

                    lastRunRow.visibility = View.VISIBLE
                    lastRunValue.text = runTimeString
                    lastRunValue.setTextColor(runColor)

                    lastEnactRow.visibility = View.VISIBLE
                    lastEnactValue.text = enactTimeString
                    lastEnactValue.setTextColor(ContextCompat.getColor(this, getAgeColorRes(enactAgeMs)))
                }
            } else {
                // Only Last Run exists
                lastRunEnactCombinedRow.visibility = View.GONE
                lastEnactRow.visibility = View.GONE

                lastRunRow.visibility = View.VISIBLE
                lastRunValue.text = runTimeString
                lastRunValue.setTextColor(runColor)
            }
        } else {
            // No Last Run
            lastRunEnactCombinedRow.visibility = View.GONE
            lastEnactRow.visibility = View.GONE

            lastRunRow.visibility = View.VISIBLE
            lastRunValue.text = "N/A"
            lastRunValue.setTextColor(ContextCompat.getColor(this, R.color.tempTargetDisabled))
        }
    }

    private fun displayOapsResult(result: OapsResultInfo) {
        loopResultCard.visibility = View.VISIBLE

        // Show SMB if present
        result.smbAmount?.let { smb ->
            if (smb > 0) {
                oapsSmbRow.visibility = View.VISIBLE
                oapsSmbValue.text = String.format("%.2f U", smb)
            } else {
                oapsSmbRow.visibility = View.GONE
            }
        } ?: run {
            oapsSmbRow.visibility = View.GONE
        }

        when {
            // Case 1: Let current temp basal run
            result.isLetTempRun -> {
                oapsStatusText.text = "Let current temp basal run"
                oapsStatusText.setTextColor(ContextCompat.getColor(this, R.color.loopClosed))
                oapsStatusText.visibility = View.VISIBLE

                // Show current TBR details
                result.rate?.let { rate ->
                    oapsRateRow.visibility = View.VISIBLE
                    oapsRateValue.text = String.format("%.2f U/h (%d%%)", rate, result.ratePercent ?: 0)
                } ?: run {
                    oapsRateRow.visibility = View.GONE
                }

                result.duration?.let { duration ->
                    oapsDurationRow.visibility = View.VISIBLE
                    oapsDurationValue.text = "$duration min remaining"
                } ?: run {
                    oapsDurationRow.visibility = View.GONE
                }
            }

            // Case 2: No change requested
            !result.changeRequested -> {
                oapsStatusText.text = "No change requested"
                oapsStatusText.setTextColor(ContextCompat.getColor(this, R.color.loopClosed))
                oapsStatusText.visibility = View.VISIBLE
                oapsRateRow.visibility = View.GONE
                oapsDurationRow.visibility = View.GONE
            }

            // Case 3: Cancel temp basal
            result.isCancelTemp -> {
                oapsStatusText.text = "Cancel temp basal"
                oapsStatusText.setTextColor(ContextCompat.getColor(this, R.color.tempBasal))
                oapsStatusText.visibility = View.VISIBLE
                oapsRateRow.visibility = View.GONE
                oapsDurationRow.visibility = View.GONE
            }

            // Case 4: New temp basal requested
            else -> {
                oapsStatusText.visibility = View.GONE

                result.rate?.let { rate ->
                    oapsRateRow.visibility = View.VISIBLE
                    oapsRateValue.text = String.format("%.2f U/h (%d%%)", rate, result.ratePercent ?: 0)
                } ?: run {
                    oapsRateRow.visibility = View.GONE
                }

                result.duration?.let { duration ->
                    oapsDurationRow.visibility = View.VISIBLE
                    oapsDurationValue.text = "$duration min"
                } ?: run {
                    oapsDurationRow.visibility = View.GONE
                }
            }
        }

        // Show reason if available (collapsed by default)
        if (result.reason.isNotEmpty()) {
            oapsReasonContainer.visibility = View.VISIBLE
            oapsReasonText.text = result.reason
            // Reset to collapsed state
            isReasonExpanded = false
            oapsReasonText.visibility = View.GONE
            oapsReasonToggle.text = "▼"
        } else {
            oapsReasonContainer.visibility = View.GONE
        }
    }

    private fun displayTargets(tempTarget: TempTargetInfo?, defaultRange: TargetRange) {
        if (tempTarget != null) {
            tempTargetContainer.visibility = View.VISIBLE
            tempTargetValue.text = "${tempTarget.targetDisplay} ${tempTarget.units}"
            tempTargetDuration.text = "${tempTarget.durationMinutes} min (${dateUtil.timeString(tempTarget.endTime)})"
        } else {
            tempTargetContainer.visibility = View.GONE
        }

        // Hide Range row if low == high
        if (defaultRange.lowDisplay != defaultRange.highDisplay) {
            defaultRangeRow.visibility = View.VISIBLE
            defaultRangeValue.text = "${defaultRange.lowDisplay} - ${defaultRange.highDisplay} ${defaultRange.units}"
        } else {
            defaultRangeRow.visibility = View.GONE
        }

        defaultTargetValue.text = "${defaultRange.targetDisplay} ${defaultRange.units}"
    }

    private fun getAgeColorRes(ageMs: Long): Int {
        val ageMinutes = ageMs / 60000
        return when {
            ageMinutes < 5 -> R.color.loopClosed  // Green < 5 min
            ageMinutes < 10 -> R.color.tempBasal  // Orange < 10 min
            else -> R.color.loopDisabled          // Red >= 10 min
        }
    }
}