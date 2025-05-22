package app.aaps.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.protection.ProtectionCheck
import app.aaps.core.interfaces.queue.CommandQueue
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventTherapyEventChange
import app.aaps.core.interfaces.ui.UiInteraction
import app.aaps.core.interfaces.utils.DecimalFormatter
import app.aaps.core.interfaces.utils.Translator
import app.aaps.core.interfaces.utils.fabric.FabricPrivacy
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.IntKey
import app.aaps.core.objects.extensions.directionToIcon
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.ui.elements.VectorHitTestImageView
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.utils.HtmlHelper
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogSiteRotationBinding
import app.aaps.ui.databinding.DialogSiteRotationChildBinding
import app.aaps.ui.databinding.DialogSiteRotationItemBinding
import app.aaps.ui.databinding.DialogSiteRotationManBinding
import app.aaps.ui.databinding.DialogSiteRotationWomanBinding
import app.aaps.ui.dialogs.SiteRotationDialog.RecyclerViewAdapter.SiteManagementViewHolder
import app.aaps.ui.dialogs.utils.SiteRotationViewAdapter
import com.google.android.material.tabs.TabLayout
import com.google.common.base.Joiner
import dagger.android.HasAndroidInjector
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import java.util.LinkedList
import javax.inject.Inject

class SiteRotationDialog : DialogFragmentWithDate() {

    @Inject lateinit var constraintChecker: ConstraintsChecker
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var commandQueue: CommandQueue
    @Inject lateinit var activePlugin: ActivePlugin
    @Inject lateinit var uel: UserEntryLogger
    @Inject lateinit var persistenceLayer: PersistenceLayer
    @Inject lateinit var protectionCheck: ProtectionCheck
    @Inject lateinit var uiInteraction: UiInteraction
    @Inject lateinit var decimalFormatter: DecimalFormatter
    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var translator: Translator
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var injector: HasAndroidInjector

    private val disposable = CompositeDisposable()
    private var _binding: DialogSiteRotationBinding? = null
    private var _siteBinding: SiteRotationViewAdapter? = null
    private var siteMode = UiInteraction.SiteMode.VIEW
    private var siteType = TE.Type.CANNULA_CHANGE
    private var time: Long = 0
    private val millsToThePast = T.days(45).msecs()
    private var listTE: List<TE> = ArrayList()
    private var therapyEdited: TE? = null
    private var selectedLocation = TE.Location.NONE
    private var selectedArrow = TE.Arrow.NONE
    private var selectedSiteView: ImageView? = null

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private val siteBinding get() = _siteBinding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("siteMode", siteMode.ordinal)
        if (siteMode == UiInteraction.SiteMode.EDIT) {
            savedInstanceState.putInt("siteType", siteType.ordinal)
            savedInstanceState.putLong("time", time)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // load data from bundle
        (savedInstanceState ?: arguments)?.let { bundle ->
            siteMode = UiInteraction.SiteMode.entries.toTypedArray()[bundle.getInt("siteMode", UiInteraction.SiteMode.VIEW.ordinal)]
            if (siteMode == UiInteraction.SiteMode.EDIT) {
                siteType = TE.Type.entries.toTypedArray()[bundle.getInt("siteType", TE.Type.CANNULA_CHANGE.ordinal)]
                time = bundle.getLong("time", 0)
            }
        }
        onCreateViewGeneral()
        _binding = DialogSiteRotationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.layoutSelectorGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.man_layout_option -> loadDynamicContent(0)
                R.id.woman_layout_option -> loadDynamicContent(1)
                R.id.child_layout_option -> loadDynamicContent(2)
            }
            processVisibility(3)
        }

        binding.layoutSelectorGroup.check(
            when (preferences.get(IntKey.SiteRotationUserProfile)) {
                0    -> R.id.man_layout_option
                1    -> R.id.woman_layout_option
                2    -> R.id.child_layout_option
                else -> R.id.man_layout_option
            }
        )
        loadDynamicContent(preferences.get(IntKey.SiteRotationUserProfile))

        if (siteMode == UiInteraction.SiteMode.EDIT) {
            binding.headerIcon.setImageResource(
                when (siteType) {
                    TE.Type.CANNULA_CHANGE -> app.aaps.core.objects.R.drawable.ic_cp_pump_cannula
                    TE.Type.SENSOR_CHANGE -> app.aaps.core.objects.R.drawable.ic_cp_cgm_insert
                    else-> app.aaps.core.objects.R.drawable.ic_cp_pump_cannula.also { siteType = TE.Type.CANNULA_CHANGE }
                }
            )
            binding.notesLayout.root.visibility = View.VISIBLE // independent to preferences
            binding.editSite.visibility = View.VISIBLE

            setupSiteSelectionListeners()
            //siteBinding.arrows.visibility = View.VISIBLE
        } else {
            binding.editSite.visibility = View.GONE
            //siteBinding.arrows.visibility = View.GONE
        }

        // checkboxes
        loadCheckedStates()
        binding.pumpSiteVisible.isChecked = binding.pumpSiteManagement.isChecked
        binding.cgmSiteVisible.isChecked = binding.cgmSiteManagement.isChecked
        binding.pumpSiteManagement.setOnCheckedChangeListener(::onCheckedChanged)
        binding.cgmSiteManagement.setOnCheckedChangeListener(::onCheckedChanged)
        binding.pumpSiteVisible.setOnCheckedChangeListener(::onCheckedChanged)
        binding.cgmSiteVisible.setOnCheckedChangeListener(::onCheckedChanged)

        binding.tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                processVisibility(tab.position)
            }
            override fun onTabUnselected(tab: TabLayout.Tab) {}
            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(siteMode.ordinal))
        processVisibility(siteMode.ordinal)
        binding.recyclerview.setHasFixedSize(true)
        binding.recyclerview.layoutManager = LinearLayoutManager(view.context)
        binding.recyclerview.emptyView = binding.noRecordsText
        binding.recyclerview.loadingView = binding.progressBar
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun submit(): Boolean {
        if (_binding == null) return false

        eventTime -= eventTime % 1000

        if (siteMode == UiInteraction.SiteMode.EDIT && therapyEdited != null) {
            therapyEdited?.let { te ->
                val actions: LinkedList<String?> = LinkedList()
                val note = binding.notesLayout.notes.text.toString()
                val siteChange = te.location != selectedLocation || te.arrow != selectedArrow || te.note != note
                if (siteChange) {
                    if (te.location != selectedLocation)
                        actions.add(rh.gs(R.string.record_site_location, translator.translate(te.location)))
                    if (te.arrow != selectedArrow)
                        actions.add(rh.gs(R.string.record_site_arrow, translator.translate(te.arrow)))
                    val note = binding.notesLayout.notes.text.toString()
                    if (note.isNotEmpty()) {
                        te.note = note
                        actions.add(rh.gs(R.string.record_site_note, te.note))
                    }
                    activity?.let { activity ->
                        OKDialog.showConfirmation(activity, rh.gs(R.string.record_site_change), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {
                            disposable += persistenceLayer.insertOrUpdateTherapyEvent(
                                therapyEvent = te
                            ).subscribe()
                        }, null)
                    }
                }
            }
        }
        dismiss()
        return true
    }
    override fun onResume() {
        super.onResume()
        swapAdapter()
        disposable += rxBus
            .toObservable(EventTherapyEventChange::class.java)
            .observeOn(aapsSchedulers.main)
            .subscribe({ swapAdapter() }, fabricPrivacy::logException)
    }

    fun swapAdapter() {
        val now = System.currentTimeMillis()
        binding.recyclerview.isLoading = true
        disposable += persistenceLayer
                    .getTherapyEventDataFromTime(now - millsToThePast, false)
                    .observeOn(aapsSchedulers.main)
                    .subscribe { list -> listTE = list.filter { te -> te.type == TE.Type.CANNULA_CHANGE || te.type == TE.Type.SENSOR_CHANGE }; filterViews(); editView() }

    }

    fun editView() {
        binding.time.text = dateUtil.dateStringShort(time)
        therapyEdited = listTE.firstOrNull { it.timestamp == time }?.also {
            binding.location.text = translator.translate(it.location)
            binding.iconArrow.setImageResource(it.arrow?.directionToIcon() ?: TE.Arrow.NONE.directionToIcon())
            selectedArrow = it.arrow ?: TE.Arrow.NONE
            selectedLocation = it.location ?: TE.Location.NONE
            binding.notesLayout.notes.editableText.insert(0, it.note ?:"")
        }
        // Add click listener for icon selection
        binding.iconArrow.setOnClickListener { view ->
            showIconSelectionPopup(requireContext(), view) { selectedArrow ->
                binding.iconArrow.setImageResource(selectedArrow.directionToIcon())
                therapyEdited?.arrow = selectedArrow
            }
        }
    }

    fun filterViews() {
        val showCannula = binding.pumpSiteVisible.isChecked
        val showCgm = binding.cgmSiteVisible.isChecked
        if (siteMode == UiInteraction.SiteMode.VIEW) {
            binding.recyclerview.swapAdapter(RecyclerViewAdapter(listTE.filter { te ->
                (te.type == TE.Type.CANNULA_CHANGE && showCannula) || (te.type == TE.Type.SENSOR_CHANGE && showCgm)
            }), true)
            siteBinding.listViews.forEach {
                it.visibility = ((it.tag as TE.Location).pump || showCgm).toVisibility()
            }
        } else {
            binding.recyclerview.swapAdapter(RecyclerViewAdapter(listTE.filter { te ->
                te.type == siteType || (te.type == TE.Type.CANNULA_CHANGE && showCannula) || (te.type == TE.Type.SENSOR_CHANGE && showCgm)
            }), true)
            siteBinding.listViews.forEach {
                it.visibility = ((it.tag as TE.Location).pump || siteType == TE.Type.SENSOR_CHANGE).toVisibility()
            }
            listTE.firstOrNull{ it.timestamp == time }?.let {

            }
        }
    }

    private fun loadDynamicContent(selectedLayout: Int) {
        val previousSelectedLocation = selectedLocation
        preferences.put(IntKey.SiteRotationUserProfile, selectedLayout)
        binding.siteLayout.removeAllViews()
        val bindLayout = when (selectedLayout) {
            0 -> DialogSiteRotationManBinding.inflate(layoutInflater)
            1 -> DialogSiteRotationWomanBinding.inflate(layoutInflater)
            2 -> DialogSiteRotationChildBinding.inflate(layoutInflater)
            else -> DialogSiteRotationManBinding.inflate(layoutInflater)
        }
        _siteBinding = SiteRotationViewAdapter.getBinding(bindLayout)
        val params = binding.siteLayout.layoutParams as LinearLayout.LayoutParams
        params.weight = when(selectedLayout) {
            2 -> 1.3f
            else -> 2.5f
        }
        binding.siteLayout.layoutParams = params
        binding.siteLayout.addView(siteBinding.root)
        if (siteMode == UiInteraction.SiteMode.EDIT) {
            setupSiteSelectionListeners()

            // Restore selection if it exists
            previousSelectedLocation?.let { location ->
                siteBinding.listViews.firstOrNull { view -> view.tag == location }?.let { view ->
                    highlightSelectedSite(view)
                }
            }
        }
    }

    private fun processVisibility(position: Int) {
        if (siteMode == UiInteraction.SiteMode.VIEW) {
            binding.tabLayout.getTabAt(0)?.view?.visibility = View.VISIBLE
            binding.tabLayout.getTabAt(1)?.view?.visibility = View.GONE
            binding.tabLayout.getTabAt(2)?.view?.visibility = View.GONE
        } else {
            binding.tabLayout.getTabAt(0)?.view?.visibility = View.GONE
            binding.tabLayout.getTabAt(1)?.view?.visibility = View.VISIBLE
            binding.tabLayout.getTabAt(2)?.view?.visibility = View.VISIBLE
        }
        siteBinding.front.visibility = (position == 0 || position == 1).toVisibility()
        siteBinding.back.visibility = (position == 0 || position == 2).toVisibility()
        binding.listLayout.visibility = (position != 3).toVisibility()
        binding.settings.visibility = (position == 3).toVisibility()
        val paramsFront = siteBinding.front.layoutParams as ConstraintLayout.LayoutParams
        val paramsBack = siteBinding.back.layoutParams as ConstraintLayout.LayoutParams
        when(position) {
            0 -> {
                paramsFront.matchConstraintPercentWidth = 0.45f
                paramsBack.matchConstraintPercentWidth = 0.45f
                siteBinding.front.layoutParams = paramsFront
                siteBinding.back.layoutParams = paramsBack
            }
            else -> {
                paramsFront.matchConstraintPercentWidth = 0.80f
                paramsBack.matchConstraintPercentWidth = 0.80f
                siteBinding.front.layoutParams = paramsFront
                siteBinding.back.layoutParams = paramsBack
            }
        }
        siteBinding.front.requestLayout()
        siteBinding.back.requestLayout()
    }

    private fun onCheckedChanged(buttonView: CompoundButton, @Suppress("unused") state: Boolean) {
        saveCheckedStates()
        if (buttonView.id == binding.pumpSiteManagement.id)
            binding.pumpSiteVisible.isChecked = binding.pumpSiteManagement.isChecked
        if (buttonView.id == binding.cgmSiteManagement.id)
            binding.cgmSiteVisible.isChecked = binding.cgmSiteManagement.isChecked
        filterViews()
    }

    private fun saveCheckedStates() {
        preferences.put(BooleanKey.SiteRotationManagePump, binding.pumpSiteManagement.isChecked)
        preferences.put(BooleanKey.SiteRotationManageCgm, binding.cgmSiteManagement.isChecked)
    }

    private fun loadCheckedStates() {
        binding.pumpSiteManagement.isChecked = preferences.get(BooleanKey.SiteRotationManagePump)
        binding.cgmSiteManagement.isChecked = preferences.get(BooleanKey.SiteRotationManageCgm)
        //binding.correctionPercent.isChecked = usePercentage
    }

    inner class RecyclerViewAdapter internal constructor(private var therapyList: List<TE>) : RecyclerView.Adapter<SiteManagementViewHolder>() {

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): SiteManagementViewHolder {
            val v = LayoutInflater.from(viewGroup.context).inflate(R.layout.dialog_site_rotation_item, viewGroup, false)
            return SiteManagementViewHolder(v)
        }

        override fun onBindViewHolder(holder: SiteManagementViewHolder, position: Int) {
            val therapyEvent = therapyList[position]
            /*
            if (therapyEvent.type == TE.Type.CANNULA_CHANGE)
                therapyEvent.glucose?.let { holder.binding.bg.text = profileUtil.stringInCurrentUnitsDetect(it) }
            if (therapyEvent.type == TE.Type.SENSOR_CHANGE)
                therapyEvent.glucose?.let { holder.binding.bg.text = profileUtil.stringInCurrentUnitsDetect(it) }

             */
            holder.binding.location.text = "not defined"
            holder.binding.update.text = "Edit"
            holder.binding.update.tag = therapyEvent
            holder.binding.time.text = dateUtil.dateStringShort(therapyEvent.timestamp)
            holder.binding.notes.text = therapyEvent.note
            holder.binding.notes.visibility = (therapyEvent.note != "").toVisibility()
            if (therapyEvent.type == TE.Type.SENSOR_CHANGE)
                holder.binding.iconSource.setImageResource(app.aaps.core.objects.R.drawable.ic_cp_cgm_insert)
            else
                holder.binding.iconSource.setImageResource(app.aaps.core.objects.R.drawable.ic_cp_pump_cannula)
            holder.binding.location.text = translator.translate(therapyEvent.location)
            holder.binding.iconArrow.setImageResource(therapyEvent.arrow?.directionToIcon() ?: TE.Arrow.NONE.directionToIcon())
        }

        override fun getItemCount() = therapyList.size

        inner class SiteManagementViewHolder(view: View) : RecyclerView.ViewHolder(view) {

            val binding = DialogSiteRotationItemBinding.bind(view)
            init {
                binding.update.setOnClickListener {
                    SiteRotationDialog().also { srd ->
                        srd.arguments = Bundle().also { args ->
                            val therapyEvent = it.tag as TE
                            args.putLong("time", therapyEvent.timestamp)
                            args.putInt("siteMode", UiInteraction.SiteMode.EDIT.ordinal)
                            args.putInt("siteType", therapyEvent.type.ordinal)
                        }
                        srd.show(childFragmentManager, "SiteRotationViewDialog")
                    }
                }
                if (siteMode == UiInteraction.SiteMode.EDIT)
                    binding.update.visibility = View.GONE
            }
        }
    }

    private fun showIconSelectionPopup(context: Context, anchorView: View, onArrowSelected: (TE.Arrow) -> Unit) {
        val popupView = LayoutInflater.from(context).inflate(R.layout.dialog_site_rotation_arrows, null)
        val popupWindow = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            isOutsideTouchable = true
        }

        // Liste de tous les IDs des ImageView dans le popup
        val arrowViewIds = listOf(
            R.id.ic_up_right, R.id.ic_up, R.id.ic_up_left, R.id.ic_right, R.id.ic_center,
            R.id.ic_left, R.id.ic_down_right, R.id.ic_down, R.id.ic_down_left, R.id.ic_none
        )

        arrowViewIds.forEach { viewId ->
            popupView.findViewById<ImageView>(viewId).setOnClickListener {
                onArrowSelected(viewId.viewIdToArrow())
                popupWindow.dismiss()
            }
        }

        popupWindow.showAsDropDown(anchorView)
    }

    fun Int.viewIdToArrow(): TE.Arrow = when (this) {
        R.id.ic_up -> TE.Arrow.UP
        R.id.ic_up_right -> TE.Arrow.UP_RIGHT
        R.id.ic_right -> TE.Arrow.RIGHT
        R.id.ic_down_right -> TE.Arrow.DOWN_RIGHT
        R.id.ic_down -> TE.Arrow.DOWN
        R.id.ic_down_left -> TE.Arrow.DOWN_LEFT
        R.id.ic_left -> TE.Arrow.LEFT
        R.id.ic_up_left -> TE.Arrow.UP_LEFT
        R.id.ic_center -> TE.Arrow.CENTER
        R.id.ic_none -> TE.Arrow.NONE
        else -> TE.Arrow.NONE
    }

    private fun setupSiteSelectionListeners() {
        siteBinding.listViews.forEach { imageView ->
            imageView.setOnClickListener { view ->
                val location = view.tag as TE.Location
                therapyEdited?.location = location
                binding.location.text = translator.translate(location)
                highlightSelectedSite(view as ImageView)
                selectedSiteView = view
            }
        }
    }
    private fun highlightSelectedSite(selectedView: ImageView) {
        selectedSiteView?.clearColorFilter()
        // Highlight the selected view
        selectedView.setColorFilter(Color.argb(150, 0, 255, 0)) // Green tint
    }

}