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
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.configuration.Config
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.profile.ProfileUtil
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.objects.ui.ActionModeHelper
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.TreatmentsCareportalFragmentBinding
import app.aaps.ui.databinding.TreatmentsCareportalItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsCareportalFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var translator: Translator
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var config: Config
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var profileUtil: ProfileUtil

    private var _binding: TreatmentsCareportalFragmentBinding? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private var menu: Menu? = null
    private val disposable = CompositeDisposable()
    private var millsToThePast = T.days(30).msecs()
    private lateinit var actionHelper: ActionModeHelper<TE>
    private var showInvalidated = false
    private var adapter: TherapyEventListAdapter? = null

    class TEWithLabel(
        /** Original TE value */
        val te: TE,
        /** true if displayed with date label */
        var hasLabel: Boolean? = null
    )

    private fun TE.withLabel() = TEWithLabel(this, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsCareportalFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            actionHelper = ActionModeHelper(rh, activity, this)
            actionHelper.setUpdateListHandler { adapter?.let { adapter -> for (i in 0 until adapter.currentList.size) adapter.notifyItemChanged(i) } }
            actionHelper.setOnRemoveHandler { handler -> removeSelected(handler) }
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = TherapyEventListAdapter()
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

    private fun removeStartedEvents() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.careportal), rh.gs(R.string.careportal_remove_started_events), {
                disposable += persistenceLayer.invalidateTherapyEventsWithNote(rh.gs(app.aaps.core.ui.R.string.androidaps_start), Action.RESTART_EVENTS_REMOVED, Sources.Treatments).subscribe()
            })
        }
    }

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showInvalidated)
                persistenceLayer
                    .getTherapyEventDataIncludingInvalidFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe {
                        list -> adapter?.submitList(list.map { it.withLabel() })  { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                        binding.recyclerview.isLoading = false
                    }
            else
                persistenceLayer
                    .getTherapyEventDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> adapter?.submitList(list.map { it.withLabel() })  {
                        if (withScroll) binding.recyclerview.scrollToPosition(0) }
                        binding.recyclerview.isLoading = false
                    }
    }

    override fun onResume() {
        super.onResume()
        load(withScroll = false)
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
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

    inner class TherapyEventListAdapter : ListAdapter<TEWithLabel, TherapyEventListAdapter.TherapyEventsViewHolder>(TherapyEventDiffCallback()) {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): TherapyEventsViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.treatments_careportal_item, viewGroup, false)
            return TherapyEventsViewHolder(v)
        }

        override fun onBindViewHolder(holder: TherapyEventsViewHolder, position: Int) {
            val item = getItem(position)
            val therapyEvent = item.te
            holder.binding.ns.visibility = (therapyEvent.ids.nightscoutId != null).toVisibility()
            holder.binding.invalid.visibility = therapyEvent.isValid.not().toVisibility()
            val newDay = position == 0 || !dateUtil.isSameDayGroup(therapyEvent.timestamp, getItem(position - 1).te.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(therapyEvent.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeString(therapyEvent.timestamp)
            holder.binding.duration.text = if (therapyEvent.duration == 0L) "" else dateUtil.niceTimeScalar(therapyEvent.duration, rh)
            holder.binding.note.text = therapyEvent.note
            if (therapyEvent.type == TE.Type.FINGER_STICK_BG_VALUE)
                therapyEvent.glucose?.let { holder.binding.bg.text = profileUtil.stringInCurrentUnitsDetect(it) }
            holder.binding.type.text = translator.translate(therapyEvent.type)
            holder.binding.cbRemove.visibility = (therapyEvent.isValid && actionHelper.isRemoving).toVisibility()
            holder.binding.cbRemove.setOnCheckedChangeListener { _, value ->
                actionHelper.updateSelection(position, therapyEvent, value)
            }
            holder.binding.root.setOnClickListener {
                holder.binding.cbRemove.toggle()
                actionHelper.updateSelection(position, therapyEvent, holder.binding.cbRemove.isChecked)
            }
            holder.binding.cbRemove.isChecked = actionHelper.isSelected(position)
        }

        inner class TherapyEventsViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = TreatmentsCareportalItemBinding.bind(view)
        }
    }

    private class TherapyEventDiffCallback : DiffUtil.ItemCallback<TEWithLabel>() {

        override fun areItemsTheSame(oldItem: TEWithLabel, newItem: TEWithLabel): Boolean = oldItem.te.id == newItem.te.id

        override fun areContentsTheSame(oldItem: TEWithLabel, newItem: TEWithLabel): Boolean =
            oldItem.te.timestamp == newItem.te.timestamp &&
                oldItem.te.isValid == newItem.te.isValid &&
                oldItem.te.type == newItem.te.type &&
                oldItem.te.note == newItem.te.note &&
                oldItem.te.duration == newItem.te.duration &&
                oldItem.te.glucose == newItem.te.glucose &&
                oldItem.hasLabel == newItem.hasLabel
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_careportal, menu)
        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_invalidated)?.isVisible = showInvalidated
        menu?.findItem(R.id.nav_show_invalidated)?.isVisible = !showInvalidated
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_remove_items          -> actionHelper.startRemove()

            R.id.nav_show_invalidated      -> {
                showInvalidated = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_invalidated_records)
                load(withScroll = false)
                true
            }

            R.id.nav_hide_invalidated      -> {
                showInvalidated = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.hide_invalidated_records)
                load(withScroll = false)
                true
            }

            R.id.nav_remove_started_events -> {
                removeStartedEvents()
                true
            }

            else                           -> false
        }

    private fun getConfirmationText(selectedItems: SparseArray<TE>): String {
        if (selectedItems.size == 1) {
            val therapyEvent = selectedItems.valueAt(0)
            return rh.gs(app.aaps.core.ui.R.string.event_type) + ": " + translator.translate(therapyEvent.type) + "\n" +
                rh.gs(app.aaps.core.ui.R.string.notes_label) + ": " + (therapyEvent.note ?: "") + "\n" +
                rh.gs(app.aaps.core.ui.R.string.date) + ": " + dateUtil.dateAndTimeString(therapyEvent.timestamp)
        }
        return rh.gs(app.aaps.core.ui.R.string.confirm_remove_multiple_items, selectedItems.size)
    }

    private fun removeSelected(selectedItems: SparseArray<TE>) {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.removerecord), getConfirmationText(selectedItems), {
                selectedItems.forEach { _, therapyEvent ->
                    disposable += persistenceLayer.invalidateTherapyEvent(
                        id = therapyEvent.id,
                        action = Action.CAREPORTAL_REMOVED, source = Sources.Treatments, note = therapyEvent.note,
                        listValues = listOf(
                            ValueWithUnit.Timestamp(therapyEvent.timestamp),
                            ValueWithUnit.TEType(therapyEvent.type)
                        )
                    ).subscribe()
                }
                actionHelper.finish()
            })
        }
    }
}
