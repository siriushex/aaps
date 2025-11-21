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
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventExtendedBolusChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.extensions.iobCalc
import app.aaps.core.objects.extensions.isInProgress
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.TreatmentsExtendedbolusFragmentBinding
import app.aaps.ui.databinding.TreatmentsExtendedbolusItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsExtendedBolusesFragment : DaggerFragment(), MenuProvider {

    private val disposable = CompositeDisposable()

    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer

    private var _binding: TreatmentsExtendedbolusFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<EB>
    private var millsToThePast = T.days(30).msecs()
    private var showInvalidated = false
    private var adapter: ExtendedBolusListAdapter? = null

    class EBWithLabel(
        val eb: EB,
        var hasLabel: Boolean? = null
    )

    private fun EB.withLabel() = EBWithLabel(this, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsExtendedbolusFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { adapter?.let { adapter -> for (i in 0 until adapter.currentList.size) adapter.notifyItemChanged(i) } }
            actionHelper.setOnRemoveHandler { removeSelected(it) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = ExtendedBolusListAdapter()
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
        binding.recyclerview.adapter = adapter

        binding.recyclerview.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)

                // Load more data if scrolled to the bottom
                if (dy > 0 && !binding.recyclerview.isLoading && (binding.recyclerview.layoutManager as LinearLayoutManager?)?.findLastCompletelyVisibleItemPosition() == (adapter?.currentList?.size ?: -1000) - 1) {
                    millsToThePast += T.hours(24).msecs()
                    ToastUtils.infoToast(requireContext(), rh.gs(app.aaps.core.ui.R.string.loading_more_data))
                    load(withScroll = false)
                }
            }
        })
    }

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable += if (showInvalidated)
            persistenceLayer
                .getExtendedBolusStartingFromTimeIncludingInvalid(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list ->
                    adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                    binding.recyclerview.isLoading = false
                }
        else
            persistenceLayer
                .getExtendedBolusesStartingFromTime(now - millsToThePast, false)
                .observeOn(aapsSchedulers.main)
                .subscribe { list ->
                    adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                    binding.recyclerview.isLoading = false
                }
    }

    override fun onResume() {
        super.onResume()
        load(withScroll = false)
        disposable += rxBus
            .toObservable(EventExtendedBolusChange::class.java)
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

    inner class ExtendedBolusListAdapter : ListAdapter<EBWithLabel, ExtendedBolusListAdapter.ExtendedBolusesViewHolder>(ExtendedBolusDiffCallback()) {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ExtendedBolusesViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_extendedbolus_item, viewGroup, false)
            return ExtendedBolusesViewHolder(v)
        }

        override fun onBindViewHolder(holder: ExtendedBolusesViewHolder, position: Int) {
            val item = getItem(position)
            val extendedBolus = item.eb
            holder.binding.ns.visibility = (extendedBolus.ids.nightscoutId != null).toVisibility()
            holder.binding.ph.visibility = (extendedBolus.ids.pumpId != null).toVisibility()
            holder.binding.invalid.visibility = extendedBolus.isValid.not().toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(extendedBolus.timestamp, getItem(position - 1).eb.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(extendedBolus.timestamp, rh) else ""
            if (extendedBolus.isInProgress(dateUtil)) {
                holder.binding.time.text = dateUtil.timeString(extendedBolus.timestamp)
                holder.binding.time.setTextColor(rh.gac(context, app.aaps.core.ui.R.attr.activeColor))
            } else {
                holder.binding.time.text = dateUtil.timeRangeString(extendedBolus.timestamp, extendedBolus.end)
                holder.binding.time.setTextColor(holder.binding.insulin.currentTextColor)
            }
            val profile = profileFunction.getProfile(extendedBolus.timestamp) ?: return
            holder.binding.duration.text = rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(extendedBolus.duration).mins())
            holder.binding.insulin.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, extendedBolus.amount)
            val iob = extendedBolus.iobCalc(System.currentTimeMillis(), profile, activePlugin.activeInsulin)
            holder.binding.iob.text = rh.gs(app.aaps.core.ui.R.string.format_insulin_units, iob.iob)
            holder.binding.ratio.text = rh.gs(app.aaps.core.ui.R.string.pump_base_basal_rate, extendedBolus.rate)
            if (iob.iob != 0.0) holder.binding.iob.setTextColor(
                rh.gac(
                    context,
                    app.aaps.core.ui.R.attr.activeColor
                )
            ) else holder.binding.iob.setTextColor(holder.binding.insulin.currentTextColor)
            holder.binding.cbRemove.visibility = (extendedBolus.isValid && actionHelper.isRemoving).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, extendedBolus, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, extendedBolus, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
        }

        inner class ExtendedBolusesViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsExtendedbolusItemBinding.bind(itemView)
        }
    }

    private class ExtendedBolusDiffCallback : DiffUtil.ItemCallback<EBWithLabel>() {

        override fun areItemsTheSame(oldItem: EBWithLabel, newItem: EBWithLabel): Boolean =
            oldItem.eb.id == newItem.eb.id

        override fun areContentsTheSame(oldItem: EBWithLabel, newItem: EBWithLabel): Boolean =
            oldItem.eb.timestamp == newItem.eb.timestamp &&
                oldItem.eb.amount == newItem.eb.amount &&
                oldItem.eb.duration == newItem.eb.duration &&
                oldItem.eb.isValid == newItem.eb.isValid &&
                oldItem.hasLabel == newItem.hasLabel
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_extended_bolus, menu)
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

    private fun getConfirmationText(selectedItems: SparseArray<EB>): String {
        if (selectedItems.size == 1) {
            val bolus = selectedItems.valueAt(0)
            return rh.gs(app.aaps.core.ui.R.string.extended_bolus) + "\n" +
                "${rh.gs(app.aaps.core.ui.R.string.date)}: ${dateUtil.dateAndTimeString(bolus.timestamp)}"
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size)
    }

    private fun removeSelected(selectedItems: SparseArray<EB>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), {
                selectedItems.forEach { _, extendedBolus ->
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
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
