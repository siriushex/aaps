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
import app.aaps.core.data.model.RM
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventRunningModeChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.TreatmentsRunningModeFragmentBinding
import app.aaps.ui.databinding.TreatmentsRunningModeItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsRunningModeFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var profileUtil: ProfileUtil
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var decimalFormatter: DecimalFormatter

    private var _binding: TreatmentsRunningModeFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private lateinit var actionHelper: ActionModeHelper<RM>
    private val disposable = CompositeDisposable()
    private var millsToThePast = T.days(30).msecs()
    private var showInvalidated = false
    private var adapter: RunningModeListAdapter? = null

    class RMWithLabel(
        val rm: RM,
        var hasLabel: Boolean? = null
    )

    private fun RM.withLabel() = RMWithLabel(this, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsRunningModeFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { adapter?.let { adapter -> for (i in 0 until adapter.currentList.size) adapter.notifyItemChanged(i) } }
            actionHelper.setOnRemoveHandler { removeSelected(it) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = RunningModeListAdapter()
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

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                persistenceLayer
                    .getRunningModesIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                        binding.recyclerview.isLoading = false
                    }
            else
                persistenceLayer
                    .getRunningModesFromTime(now - millsToThePast, false)
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
            .toObservable(EventRunningModeChange::class.java)
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

    inner class RunningModeListAdapter : ListAdapter<RMWithLabel, RunningModeListAdapter.RunningModeViewHolder>(RunningModeDiffCallback()) {

        private val currentlyActiveMode = persistenceLayer.getRunningModeActiveAt(dateUtil.now())

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): RunningModeViewHolder =
            RunningModeViewHolder(LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_running_mode_item, viewGroup, false))

        override fun onBindViewHolder(holder: RunningModeViewHolder, position: Int) {
            val item = getItem(position)
            val runningMode = item.rm
            holder.binding.ns.visibility = (runningMode.ids.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = runningMode.isValid.not().toVisibility()
            holder.binding.cbRemove.visibility = (runningMode.isValid && actionHelper.isRemoving).toVisibility()
            if (actionHelper.isRemoving) {
                holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                    actionHelper.updateSelection(position, runningMode, value)
                }
                holder.binding.root.setOnClickListener {
                    holder.binding.cbRemove.toggle()
                    actionHelper.updateSelection(position, runningMode, holder.binding.cbRemove.isChecked)
                }
                holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
            }
            val newDay = position == 0 || !dateUtil.isSameDayGroup(runningMode.timestamp, getItem(position - 1).rm.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(runningMode.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeString(runningMode.timestamp)
            holder.binding.duration.text =
                if (runningMode.duration > T.months(12).msecs()) rh.gs(R.string.until_changed)
                else if (runningMode.isTemporary()) rh.gs(app.aaps.core.ui.R.string.format_mins, T.msecs(runningMode.duration).mins())
                else ""
            holder.binding.mode.text = translator.translate(runningMode.mode)
            holder.binding.time.setTextColor(
                when {
                    runningMode.id == currentlyActiveMode.id -> rh.gac(context, app.aaps.core.ui.R.attr.activeColor)
                    runningMode.timestamp > dateUtil.now()    -> rh.gac(context, app.aaps.core.ui.R.attr.scheduledColor)
                    else -> holder.binding.duration.currentTextColor
                }
            )
        }

        inner class RunningModeViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsRunningModeItemBinding.bind(view)
        }
    }

    private class RunningModeDiffCallback : DiffUtil.ItemCallback<RMWithLabel>() {

        override fun areItemsTheSame(oldItem: RMWithLabel, newItem: RMWithLabel): Boolean =
            oldItem.rm.id == newItem.rm.id

        override fun areContentsTheSame(oldItem: RMWithLabel, newItem: RMWithLabel): Boolean =
            oldItem.rm.timestamp == newItem.rm.timestamp &&
                oldItem.rm.duration == newItem.rm.duration &&
                oldItem.rm.mode == newItem.rm.mode &&
                oldItem.rm.isValid == newItem.rm.isValid &&
                oldItem.hasLabel == newItem.hasLabel
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_running_mode, menu)
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

    private fun getConfirmationText(selectedItems: SparseArray<RM>): String {
        if (selectedItems.size == 1) {
            val runningMode = selectedItems.valueAt(0)
            return "${rh.gs(app.aaps.core.ui.R.string.running_mode)}: ${runningMode.mode.name}\n" +
                dateUtil.dateAndTimeString(runningMode.timestamp)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size)
    }

    private fun removeSelected(selectedItems: SparseArray<RM>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), {
                selectedItems.forEach { _, runningMode ->
                    disposable += persistenceLayer.invalidateRunningMode(
                        id = runningMode.id,
                        action = Action.LOOP_REMOVED, source = Sources.Treatments, note = null,
                        listValues = listOfNotNull(
                            ValueWithUnit.Timestamp(runningMode.timestamp),
                            ValueWithUnit.RMMode(runningMode.mode),
                            ValueWithUnit.Minute(TimeUnit.MILLISECONDS.toMinutes(runningMode.duration).toInt())
                        )
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
