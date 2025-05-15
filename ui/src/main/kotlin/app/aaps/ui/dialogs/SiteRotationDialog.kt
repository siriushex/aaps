package app.aaps.ui.dialogs

import android.content.res.ColorStateList
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.VectorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.vectordrawable.graphics.drawable.VectorDrawableCompat
import app.aaps.core.data.model.TE
import app.aaps.core.data.time.T
import app.aaps.core.interfaces.constraints.ConstraintsChecker
import app.aaps.core.interfaces.db.PersistenceLayer
import app.aaps.core.interfaces.logging.LTag
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
import app.aaps.core.objects.extensions.formatColor
import app.aaps.core.ui.extensions.toVisibility
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.ui.R
import app.aaps.ui.databinding.DialogSiteRotationBinding
import app.aaps.ui.databinding.DialogSiteRotationChildBinding
import app.aaps.ui.databinding.DialogSiteRotationItemBinding
import app.aaps.ui.databinding.DialogSiteRotationManBinding
import app.aaps.ui.databinding.DialogSiteRotationWomanBinding
import app.aaps.ui.dialogs.SiteRotationDialog.RecyclerViewAdapter.SiteManagementViewHolder
import app.aaps.ui.dialogs.utils.SiteRotationViewAdapter
import com.google.android.material.tabs.TabLayout
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

    private var queryingProtection = false
    private val disposable = CompositeDisposable()
    private var _binding: DialogSiteRotationBinding? = null
    private var _siteBinding: SiteRotationViewAdapter? = null
    private var siteMode = UiInteraction.SiteMode.VIEW
    private var siteType = UiInteraction.SiteType.PUMP
    private var location = TE.Location.NONE
    private var rotation = 0
    private var time: Long = 0
    private val millsToThePast = T.days(45).msecs()

    // This property is only valid between onCreateView and onDestroyView.
    private val binding get() = _binding!!
    private val siteBinding get() = _siteBinding!!

    override fun onSaveInstanceState(savedInstanceState: Bundle) {
        super.onSaveInstanceState(savedInstanceState)
        savedInstanceState.putInt("siteMode", siteMode.ordinal)
        if (siteMode == UiInteraction.SiteMode.EDIT) {
            savedInstanceState.putInt("siteType", siteType.ordinal)
            savedInstanceState.putInt("location", location.ordinal)
            savedInstanceState.putInt("rotation", rotation)
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
                siteType = UiInteraction.SiteType.entries.toTypedArray()[bundle.getInt("siteType", UiInteraction.SiteType.PUMP.ordinal)]
                location = TE.Location.entries.toTypedArray()[bundle.getInt("location", TE.Location.NONE.ordinal)]
                rotation = bundle.getInt("orientation", 0)
                time = bundle.getLong("time", 0)
            }
        }
        onCreateViewGeneral()
        _binding = DialogSiteRotationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDynamicContent(preferences.get(IntKey.SiteRotationUserProfile))
        if (siteMode == UiInteraction.SiteMode.EDIT) {
            binding.headerIcon.setImageResource(
                when (siteType) {
                    UiInteraction.SiteType.PUMP -> app.aaps.core.objects.R.drawable.ic_cp_pump_cannula
                    UiInteraction.SiteType.CGM -> app.aaps.core.objects.R.drawable.ic_cp_cgm_insert
                }
            )
        }

        binding.layoutSelectorGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.man_layout_option -> loadDynamicContent(0)
                R.id.woman_layout_option -> loadDynamicContent(1)
                R.id.child_layout_option -> loadDynamicContent(2)
            }
            processVisibility(3)
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
        val actions: LinkedList<String?> = LinkedList()

        val siteChange = binding.pumpSiteManagement.isChecked
        if (siteChange)
            actions.add(rh.gs(R.string.record_pump_site_change).formatColor(context, rh, app.aaps.core.ui.R.attr.actionsConfirmColor))
        val insulinChange = binding.pumpSiteManagement.isChecked
        if (insulinChange)
            actions.add(rh.gs(R.string.record_insulin_cartridge_change).formatColor(context, rh, app.aaps.core.ui.R.attr.actionsConfirmColor))
        eventTime -= eventTime % 1000

        if (eventTimeChanged)
            actions.add(rh.gs(app.aaps.core.ui.R.string.time) + ": " + dateUtil.dateAndTimeString(eventTime))
        /*
        if (insulinAfterConstraints > 0 || binding.cgmSiteManagement.isChecked || binding.cgmSiteManagement.isChecked) {
            activity?.let { activity ->
                OKDialog.showConfirmation(activity, rh.gs(app.aaps.core.ui.R.string.prime_fill), HtmlHelper.fromHtml(Joiner.on("<br/>").join(actions)), {

                    if (insulinAfterConstraints > 0) {
                        uel.log(
                            action = Action.PRIME_BOLUS, source = Sources.FillDialog,
                            note = notes,
                            value = ValueWithUnit.Insulin(insulinAfterConstraints)
                        )
                        requestPrimeBolus(insulinAfterConstraints, notes)
                    }
                    if (siteChange)
                        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE(
                                timestamp = eventTime,
                                type = TE.Type.CANNULA_CHANGE,
                                note = notes,
                                glucoseUnit = GlucoseUnit.MGDL
                            ),
                            action = Action.SITE_CHANGE, source = Sources.FillDialog,
                            note = notes,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                                ValueWithUnit.TEType(TE.Type.CANNULA_CHANGE)
                            ).filterNotNull()
                        ).subscribe()
                    if (insulinChange)
                    // add a second for case of both checked
                        disposable += persistenceLayer.insertPumpTherapyEventIfNewByTimestamp(
                            therapyEvent = TE(
                                timestamp = eventTime + 1000,
                                type = TE.Type.INSULIN_CHANGE,
                                note = notes,
                                glucoseUnit = GlucoseUnit.MGDL
                            ),
                            action = Action.RESERVOIR_CHANGE, source = Sources.FillDialog,
                            note = notes,
                            listValues = listOf(
                                ValueWithUnit.Timestamp(eventTime).takeIf { eventTimeChanged },
                                ValueWithUnit.TEType(TE.Type.INSULIN_CHANGE)
                            ).filterNotNull()

                        ).subscribe()
                }, null)
            }
        } else {
            activity?.let { activity ->
                OKDialog.show(activity, rh.gs(app.aaps.core.ui.R.string.prime_fill), rh.gs(app.aaps.core.ui.R.string.no_action_selected))
            }
        }
        */
        dismiss()
        return true
    }
    override fun onResume() {
        super.onResume()
        if (!queryingProtection) {
            queryingProtection = true
            activity?.let { activity ->
                val cancelFail = {
                    queryingProtection = false
                    aapsLogger.debug(LTag.APS, "Dialog canceled on resume protection: ${this.javaClass.simpleName}")
                    ToastUtils.warnToast(activity, R.string.dialog_canceled)
                    dismiss()
                }
                protectionCheck.queryProtection(activity, ProtectionCheck.Protection.BOLUS, { queryingProtection = false }, cancelFail, cancelFail)
            }
        }
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
                    .subscribe { list -> binding.recyclerview.swapAdapter(RecyclerViewAdapter(list.filter { te -> te.type == TE.Type.CANNULA_CHANGE || te.type == TE.Type.SENSOR_CHANGE }), true) }
    }

    private fun loadDynamicContent(selectedLayout: Int) {
        preferences.put(IntKey.SiteRotationUserProfile, selectedLayout)
        binding.siteLayout.removeAllViews()
        val bindLayout = when (selectedLayout) {
            0 -> DialogSiteRotationManBinding.inflate(layoutInflater)
            1 -> DialogSiteRotationWomanBinding.inflate(layoutInflater)
            2 -> DialogSiteRotationChildBinding.inflate(layoutInflater)
            else -> DialogSiteRotationManBinding.inflate(layoutInflater)
        }
        _siteBinding = SiteRotationViewAdapter.getBinding(bindLayout)
        binding.siteLayout.addView(siteBinding.root)
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
        //processEnabledIcons()
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
                holder.binding.iconSource.setImageResource(app.aaps.core.objects.R.drawable.ic_cp_cgm_insert) //app.aaps.core.objects.R.drawable.ic_cp_pump_cannula
            /*
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
             */
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
                            args.putInt("siteType", if (therapyEvent.type == TE.Type.SENSOR_CHANGE) UiInteraction.SiteType.CGM.ordinal else UiInteraction.SiteType.PUMP.ordinal)
                            args.putInt("location", therapyEvent.location?.ordinal ?: TE.Location.NONE.ordinal)
                            args.putInt("rotation", therapyEvent.rotation?.ordinal ?: TE.Rotation.NONE.ordinal)
                        }
                        srd.show(childFragmentManager, "SiteRotationViewDialog")
                    }
                }
                if (siteMode == UiInteraction.SiteMode.EDIT)
                    binding.update.visibility = View.GONE
            }
        }
    }

}