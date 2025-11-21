package app.aaps.ui.activities.fragments

import android.os.Bundle
import android.util.SparseArray
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.util.forEach
import androidx.core.util.size
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.EB
import app.aaps.core.data.model.TB
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTempBasalChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.objects.extensions.toStringFull
import app.aaps.core.objects.extensions.toTemporaryBasal
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.TreatmentsTempbasalsFragmentBinding
import app.aaps.ui.databinding.TreatmentsTempbasalsItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlin.math.abs

class TreatmentsTemporaryBasalsFragment : DaggerFragment(), MenuProvider {

    private val disposable = CompositeDisposable()

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var _binding: TreatmentsTempbasalsFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<TB>
    private var millsToThePast = T.days(30).msecs()
    private var showInvalidated = false
    private var adapter: TempBasalListAdapter? = null

    class TBWithLabel(
        val tb: TB,
        var hasLabel: Boolean? = null
    )

    private fun TB.withLabel() = TBWithLabel(this, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsTempbasalsFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { adapter?.let { adapter -> for (i in 0 until adapter.currentList.size) adapter.notifyItemChanged(i) } }
            actionHelper.setOnRemoveHandler { removeSelected(it) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TempBasalListAdapter()
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        binding.recyclerview.adapter = adapter

        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Load more data if scrolled to the bottom
                if ((binding.recyclerview.layoutManager as LinearLayoutManager?)?.findLastCompletelyVisibleItemPosition() == (adapter?.currentList?.size ?: -1000) - 1) {
                    millsToThePast += T.hours(24).msecs()
                    ToastUtils.infoToast(requireContext(), rh.gs(app.aaps.core.ui.R.string.loading_more_data))
                    load(withScroll = false)
                }
            }
        })
    }

    private fun tempBasalsWithInvalid(now: Long) = persistenceLayer
        .getTemporaryBasalsStartingFromTimeIncludingInvalid(now - millsToThePast, false)

    private fun tempBasals(now: Long) = persistenceLayer
        .getTemporaryBasalsStartingFromTime(now - millsToThePast, false)

    private fun extendedBolusesWithInvalid(now: Long) = persistenceLayer
        .getExtendedBolusStartingFromTimeIncludingInvalid(now - millsToThePast, false)
        .map { eb -> eb.map { profileFunction.getProfile(it.timestamp)?.let { profile -> it.toTemporaryBasal(profile) } } }

    private fun extendedBoluses(now: Long) = persistenceLayer
        .getExtendedBolusesStartingFromTime(now - millsToThePast, false)
        .map { eb -> eb.map { profileFunction.getProfile(it.timestamp)?.let { profile -> it.toTemporaryBasal(profile) } } }

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (activePlugin.activePump.isFakingTempsByExtendedBoluses) {
                if (showInvalidated)
                    tempBasalsWithInvalid(now)
                        .zipWith(extendedBolusesWithInvalid(now)) { first, second -> first + second }
                        .map { list -> list.filterNotNull() }
                        .map { list -> list.sortedByDescending { it.timestamp } }
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list ->
                            adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                            binding.recyclerview.isLoading = false
                        }
                else
                    tempBasals(now)
                        .zipWith(extendedBoluses(now)) { first, second -> first + second }
                        .map { list -> list.filterNotNull() }
                        .map { list -> list.sortedByDescending { it.timestamp } }
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list ->
                            adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                            binding.recyclerview.isLoading = false
                        }
            } else {
                if (showInvalidated)
                    tempBasalsWithInvalid(now)
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list ->
                            adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                            binding.recyclerview.isLoading = false
                        }
                else
                    tempBasals(now)
                        .observeOn(aapsSchedulers.main)
                        .subscribe { list ->
                            adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                            binding.recyclerview.isLoading = false
                        }
            }
    }

    override fun onResume() {
        super.onResume()
        load(withScroll = false)
        disposable += rxBus
            .toObservable(EventTempBasalChange::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ load(withScroll = true) }, fabricPrivacy::logException)
    }

    override fun onPause() {
        super.onPause()
        actionHelper.finish()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null
        adapter = null
        _binding = null
    }

    inner class TempBasalListAdapter : ListAdapter<TBWithLabel, TempBasalListAdapter.TempBasalsViewHolder>(TempBasalDiffCallback()) {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TempBasalsViewHolder =
            TempBasalsViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_tempbasals_item, viewGroup, false))

        override fun onBindViewHolder(holder: TempBasalsViewHolder, position: Int) {
            val item = getItem(position)
            val tempBasal = item.tb
            holder.binding.ns.visibility = (tempBasal.ids.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = tempBasal.isValid.not().toVisibility()
            holder.binding.ph.visibility = (tempBasal.ids.pumpId != null).toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(tempBasal.timestamp, getItem(position - 1).tb.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(tempBasal.timestamp, rh) else ""
            if (tempBasal.isInProgress) {
                holder.binding.time.text = dateUtil.timeString(tempBasal.timestamp)
                holder.binding.time.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.activeColor))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(tempBasal.timestamp, tempBasal.end)
                holder.binding.time.setTextColor(holder.binding.duration.currentTextColor)
            }
            holder.binding.duration.text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(tempBasal.duration).mins())
            if (tempBasal.isAbsolute) holder.binding.rate.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, tempBasal.rate)
            else holder.binding.rate.text = rh.gs(app.aaps.core.ui.R.string.format_percent, tempBasal.rate.toInt())
            val now = dateUtil.now()
            var iob = IobTotal(now)
            val profile = profileFunction.getProfile(now)
            if (profile != null) iob = tempBasal.iobCalc(now, profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, iob.basaliob)
            holder.binding.extendedFlag.visibility = (tempBasal.type == TB.Type.FAKE_EXTENDED).toVisibility()
            holder.binding.suspendFlag.visibility = (tempBasal.type == TB.Type.PUMP_SUSPEND).toVisibility()
            holder.binding.emulatedSuspendFlag.visibility = (tempBasal.type == TB.Type.EMULATED_PUMP_SUSPEND).toVisibility()
            holder.binding.superBolusFlag.visibility = (tempBasal.type == TB.Type.SUPERBOLUS).toVisibility()
            if (abs(iob.basaliob) > 0.01) holder.binding.iob.setTextColor(
                rh.gac(
                    context,
                    app.aaps.core.ui.R.attr.activeColor
                )
            ) else holder.binding.iob.setTextColor(holder.binding.duration.currentTextColor)
            holder.binding.cbRemove.visibility = (tempBasal.isValid && actionHelper.isRemoving).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, tempBasal, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, tempBasal, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
        }

        inner class TempBasalsViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsTempbasalsItemBinding.bind(itemView)
        }
    }

    private class TempBasalDiffCallback : DiffUtil.ItemCallback<TBWithLabel>() {

        override fun areItemsTheSame(oldItem: TBWithLabel, newItem: TBWithLabel): Boolean =
            oldItem.tb.id == newItem.tb.id

        override fun areContentsTheSame(oldItem: TBWithLabel, newItem: TBWithLabel): Boolean =
            oldItem.tb.timestamp == newItem.tb.timestamp &&
                oldItem.tb.rate == newItem.tb.rate &&
                oldItem.tb.duration == newItem.tb.duration &&
                oldItem.tb.isValid == newItem.tb.isValid &&
                oldItem.tb.type == newItem.tb.type &&
                oldItem.hasLabel == newItem.hasLabel
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_temp_basal, menu)
        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items     -> actionHelper.startRemove()

            R.id.nav_show_invalidated -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                load(withScroll = false)
                true
            }

            R.id.nav_hide_invalidated -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                load(withScroll = false)
                true
            }

            else                      -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TB>): String {
        if (selectedItems.size == 1) {
            val tempBasal = selectedItems.valueAt(0)
            val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
            val profile = profileFunction.getProfile(dateUtil.now())
            if (profile != null)
                return "${if (isFakeExtended) rh.gs(app.aaps.core.ui.R.string.extended_bolus) else rh.gs(app.aaps.core.ui.R.string.tempbasal_label)}: ${
                    tempBasal.toStringFull(
                        profile,
                        dateUtil,
                        rh
                    )
                }\n" +
                    "${rh.gs(app.aaps.core.ui.R.string.date)}: ${dateUtil.dateAndTimeString(tempBasal.timestamp)}"
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size)
    }

    private fun removeSelected(selectedItems: SparseArray<TB>) {
        if (selectedItems.size > 0)
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), {
                    selectedItems.forEach { _, tempBasal ->
                        var extendedBolus: EB? = null
                        val isFakeExtended = tempBasal.type == TB.Type.FAKE_EXTENDED
                        if (isFakeExtended) {
                            extendedBolus = persistenceLayer.getExtendedBolusActiveAt(tempBasal.timestamp)
                        }
                        if (isFakeExtended && extendedBolus != null) {
                            disposable += persistenceLayer.invalidateExtendedBolus(
                                id = extendedBolus.id,
                                action = Action.EXTENDED_BOLUS_REMOVED,
                                source = Sources.Treatments,
                                listValues = listOf(
                                    ValueWithUnit.Timestamp(extendedBolus.timestamp),
                                    ValueWithUnit.Insulin(extendedBolus.amount),
                                    ValueWithUnit.UnitPerHour(extendedBolus.rate),
                                    ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(extendedBolus.duration).toInt())
                                )
                            )
                                .subscribe()
                        } else if (!isFakeExtended) {
                            disposable += persistenceLayer.invalidateTemporaryBasal(
                                id = tempBasal.id,
                                action = Action.TEMP_BASAL_REMOVED,
                                source = Sources.Treatments,
                                listValues = listOf(
                                    ValueWithUnit.Timestamp(tempBasal.timestamp),
                                    if (tempBasal.isAbsolute) ValueWithUnit.UnitPerHour(tempBasal.rate) else ValueWithUnit.Percent(tempBasal.rate.toInt()),
                                    ValueWithUnit.Minute(T.msecs(tempBasal.duration).mins().toInt())
                                )
                            )
                                .subscribe()
                        }
                    }
                    actionHelper.finish()
                })
            }
    }
}
