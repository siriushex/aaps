package app.aaps.ui.dialogs.utils

import android.view.View
import androidx.viewbinding.ViewBinding
import app.aaps.core.data.model.TE
import app.aaps.ui.databinding.DialogSiteRotationManBinding
import app.aaps.ui.databinding.DialogSiteRotationWomanBinding
import app.aaps.ui.databinding.DialogSiteRotationChildBinding

class SiteRotationViewAdapter(
    man: DialogSiteRotationManBinding? = null,
    woman: DialogSiteRotationWomanBinding? = null,
    child: DialogSiteRotationChildBinding? = null
) {

    init {
        if (man == null && woman == null && child == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }
    private val errorMessage = "Missing require View Binding parameter"
    val listViews: MutableList<View> = ArrayList()

    // Required attributes
    val root = man?.root ?: woman?.root ?: child?.root ?: throw IllegalArgumentException(errorMessage)
    val front = man?.front ?: woman?.front ?: child?.front ?: throw IllegalArgumentException(errorMessage)
    val back = man?.back ?: woman?.back ?: child?.back ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    // FrontView from top to down
    val frontLuChest = (man?.frontLuChest)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_CHEST; listViews.add(it)}
    val fronRuChest = (man?.frontRuChest)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_CHEST; listViews.add(it)}
    val sideLAram = (man?.sideLArm ?: woman?.sideLArm)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_ARM; listViews.add(it)}
    val sideRAram = (man?.sideRArm ?: woman?.sideRArm)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_ARM; listViews.add(it)}
    val sideLuAbdomen = (man?.sideLuAbdomen ?: woman?.sideLuAbdomen ?: child?.sideLuAbdomen)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_ABDOMEN; listViews.add(it)}
    val sideRuAbdomen = (man?.sideRuAbdomen ?: woman?.sideRuAbdomen ?: child?.sideRuAbdomen)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_ABDOMEN; listViews.add(it)}
    val frontLuAbdomen = (man?.frontLuAbdomen ?: woman?.frontLuAbdomen ?: child?.frontLuAbdomen)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_ABDOMEN; listViews.add(it)}
    val frontRuAbdomen = (man?.frontRuAbdomen ?: woman?.frontRuAbdomen ?: child?.frontRuAbdomen)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_ABDOMEN; listViews.add(it)}
    val sideLlAbdomen = (man?.sideLlAbdomen ?: woman?.sideLlAbdomen ?: child?.sideLlAbdomen)?.also { it.tag = TE.Location.SIDE_LEFT_LOWER_ABDOMEN; listViews.add(it)}
    val sideRlAbdomen = (man?.sideRlAbdomen ?: woman?.sideRlAbdomen ?: child?.sideRlAbdomen)?.also { it.tag = TE.Location.SIDE_RIGHT_LOWER_ABDOMEN; listViews.add(it)}
    val frontLlAbdomen = (man?.frontLlAbdomen ?: woman?.frontLlAbdomen ?: child?.frontLlAbdomen)?.also { it.tag = TE.Location.FRONT_LEFT_LOWER_ABDOMEN; listViews.add(it)}
    val frontRlAbdomen = (man?.frontRlAbdomen ?: woman?.frontRlAbdomen ?: child?.frontRlAbdomen)?.also { it.tag = TE.Location.FRONT_RIGHT_LOWER_ABDOMEN; listViews.add(it)}
    val frontLuThigh = (man?.frontLuThigh ?: woman?.frontLuThigh ?: child?.frontLuThigh)?.also { it.tag = TE.Location.FRONT_LEFT_UPPER_THIGH; listViews.add(it)}
    val frontRuThigh = (man?.frontRuThigh ?: woman?.frontRuThigh ?: child?.frontRuThigh)?.also { it.tag = TE.Location.FRONT_RIGHT_UPPER_THIGH; listViews.add(it)}
    val frontLlThigh = (man?.frontLlThigh ?: woman?.frontLlThigh ?: child?.frontLlThigh)?.also { it.tag = TE.Location.FRONT_LEFT_LOWER_THIGH; listViews.add(it)}
    val frontRlThigh = (man?.frontRlThigh ?: woman?.frontRlThigh ?: child?.frontRlThigh)?.also { it.tag = TE.Location.FRONT_LEFT_LOWER_THIGH; listViews.add(it)}

    // BackView from top to down
    val backLArm = (man?.backLArm ?: woman?.backLArm ?: child?.backLArm)?.also { it.tag = TE.Location.BACK_LEFT_UPPER_ARM; listViews.add(it) }
    val backRArm = (man?.backRArm ?: woman?.backRArm ?: child?.backRArm)?.also { it.tag = TE.Location.BACK_RIGHT_UPPER_ARM; listViews.add(it) }
    val backLButtock = (man?.backLButtock ?: woman?.backLButtock ?: child?.backLButtock)?.also { it.tag = TE.Location.BACK_LEFT_BUTTOCK; listViews.add(it) }
    val backRButtock = (man?.backRButtock ?: woman?.backRButtock ?: child?.backRButtock)?.also { it.tag = TE.Location.BACK_RIGHT_BUTTOCK; listViews.add(it) }
    val sideLuThigh = (man?.sideLuThigh ?: woman?.sideLuThigh)?.also { it.tag = TE.Location.SIDE_LEFT_UPPER_THIGH; listViews.add(it) }
    val sideRuThigh = (man?.sideRuThigh ?: woman?.sideRuThigh)?.also { it.tag = TE.Location.SIDE_RIGHT_UPPER_THIGH; listViews.add(it) }
    val sideLlThigh = (man?.sideLlThigh ?: woman?.sideLlThigh)?.also { it.tag = TE.Location.SIDE_LEFT_LOWER_THIGH; listViews.add(it) }
    val sideRlThigh = (man?.sideRlThigh ?: woman?.sideRlThigh)?.also { it.tag = TE.Location.SIDE_RIGHT_LOWER_THIGH; listViews.add(it) }


    companion object {

        fun getBinding(bindLayout: ViewBinding): SiteRotationViewAdapter {
            return when (bindLayout) {
                is DialogSiteRotationManBinding   -> SiteRotationViewAdapter(bindLayout)
                is DialogSiteRotationWomanBinding -> SiteRotationViewAdapter(null, bindLayout)
                is DialogSiteRotationChildBinding -> SiteRotationViewAdapter(null, null, bindLayout)
                else                              -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}