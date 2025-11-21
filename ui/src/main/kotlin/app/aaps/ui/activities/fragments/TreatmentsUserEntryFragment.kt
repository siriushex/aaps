package app.aaps.ui.activities.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.UE
import app.aaps.core.data.time.T
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.maintenance.ImportExportPrefs
import app.aaps.core.interfaces.profile.ProfileFunction
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventPreferenceChange
import app.aaps.core.interfaces.userEntry.UserEntryPresentationHelper
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.TreatmentsUserEntryFragmentBinding
import app.aaps.ui.databinding.TreatmentsUserEntryItemBinding
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class TreatmentsUserEntryFragment : DaggerFragment(), MenuProvider {

    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var profileFunction: ProfileFunction
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var importExportPrefs: ImportExportPrefs
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var userEntryPresentationHelper: UserEntryPresentationHelper

    private val disposable = CompositeDisposable()
    private var millsToThePastFiltered = T.days(30).msecs()
    private var millsToThePastUnFiltered = T.days(3).msecs()
    private var menu: Menu? = null
    private var showLoop = false
    private var _binding: TreatmentsUserEntryFragmentBinding? = null
    private var adapter: UserEntryListAdapter? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!

    class UEWithLabel(
        val ue: UE,
        var hasLabel: Boolean? = null
    )

    private fun UE.withLabel() = UEWithLabel(this, null)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        TreatmentsUserEntryFragmentBinding.inflate(inflater, container, false).also {
            _binding = it
            requireActivity().addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        }.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = UserEntryListAdapter()
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
                    if (showLoop) millsToThePastUnFiltered += T.hours(24).msecs()
                    else millsToThePastFiltered += T.hours(24).msecs()
                    ToastUtils.infoToast(requireContext(), rh.gs(app.aaps.core.ui.R.string.loading_more_data))
                    load(withScroll = false)
                }
            }
        })
    }

    private fun exportUserEntries() {
        activity?.let { activity ->
            OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.ue_export_to_csv) + "?") {
                uel.log(Action.EXPORT_CSV, Sources.Treatments)
                importExportPrefs.exportUserEntriesCsv(activity)
            }
        }
    }

    private fun load(withScroll: Boolean) {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable +=
            if (showLoop)
                persistenceLayer
                    .getUserEntryDataFromTime(now - millsToThePastUnFiltered)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list ->
                        adapter?.submitList(list.map { it.withLabel() }) { if (withScroll) binding.recyclerview.scrollToPosition(0) }
                        binding.recyclerview.isLoading = false
                    }
            else
                persistenceLayer
                    .getUserEntryFilteredDataFromTime(now - millsToThePastFiltered)
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
            .toObservable(EventPreferenceChange::class.java)
            .observeOn(aapsSchedulers.main)
            .debounce(1L, TimeUnit.SECONDS)
            .subscribe({ load(withScroll = true) }, fabricPrivacy::logException)
    }

    override fun onPause() {
        super.onPause()
        disposable.clear()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter = null
        adapter = null
        _binding = null
    }

    inner class UserEntryListAdapter : ListAdapter<UEWithLabel, UserEntryListAdapter.UserEntryViewHolder>(UserEntryDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserEntryViewHolder {
            val view: View = LayoutInflater.from(parent.context).inflate(R.layout.treatments_user_entry_item, parent, false)
            return UserEntryViewHolder(view)
        }

        override fun onBindViewHolder(holder: UserEntryViewHolder, position: Int) {
            val item = getItem(position)
            val current = item.ue
            val newDay = position == 0 || !dateUtil.isSameDayGroup(current.timestamp, getItem(position - 1).ue.timestamp)
            item.hasLabel = newDay
            holder.binding.date.visibility = newDay.toVisibility()
            holder.binding.date.text = if (newDay) dateUtil.dateStringRelative(current.timestamp, rh) else ""
            holder.binding.time.text = dateUtil.timeStringWithSeconds(current.timestamp)
            holder.binding.action.text = userEntryPresentationHelper.actionToColoredString(current.action)
            holder.binding.notes.text = current.note
            holder.binding.notes.visibility = (current.note.isNotEmpty()).toVisibility()
            holder.binding.iconSource.setImageResource(userEntryPresentationHelper.iconId(current.source))
            holder.binding.values.text = userEntryPresentationHelper.listToPresentationString(current.values)
            holder.binding.values.visibility = (holder.binding.values.text != "").toVisibility()
        }

        inner class UserEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

            val binding = TreatmentsUserEntryItemBinding.bind(itemView)
        }
    }

    private class UserEntryDiffCallback : DiffUtil.ItemCallback<UEWithLabel>() {

        override fun areItemsTheSame(oldItem: UEWithLabel, newItem: UEWithLabel): Boolean =
            oldItem.ue.id == newItem.ue.id

        override fun areContentsTheSame(oldItem: UEWithLabel, newItem: UEWithLabel): Boolean =
            oldItem.ue.timestamp == newItem.ue.timestamp &&
                oldItem.ue.action == newItem.ue.action &&
                oldItem.ue.source == newItem.ue.source &&
                oldItem.ue.note == newItem.ue.note &&
                oldItem.hasLabel == newItem.hasLabel
    }

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_treatments_user_entry, menu)
        updateMenuVisibility()
    }

    private fun updateMenuVisibility() {
        menu?.findItem(R.id.nav_hide_loop)?.isVisible = showLoop
        menu?.findItem(R.id.nav_show_loop)?.isVisible = !showLoop
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean =
        when (item.itemId) {
            R.id.nav_show_loop -> {
                showLoop = true
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_loop_records)
                load(withScroll = false)
                true
            }

            R.id.nav_hide_loop -> {
                showLoop = false
                updateMenuVisibility()
                ToastUtils.infoToast(context, R.string.show_hide_records)
                load(withScroll = false)
                true
            }

            R.id.nav_export    -> {
                exportUserEntries()
                true
            }

            else               -> false
        }
}
