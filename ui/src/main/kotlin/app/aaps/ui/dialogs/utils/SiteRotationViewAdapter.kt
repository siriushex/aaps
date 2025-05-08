package app.aaps.ui.dialogs.utils

import androidx.viewbinding.ViewBinding
import app.aaps.ui.databinding.DialogSiteRotationManBinding
import app.aaps.ui.databinding.DialogSiteRotationWomanBinding

class SiteRotationViewAdapter(
    man: DialogSiteRotationManBinding? = null,
    woman: DialogSiteRotationWomanBinding? = null
) {

    init {
        if (man == null && woman == null) {
            throw IllegalArgumentException("Require at least on Binding parameter")
        }
    }
    private val errorMessage = "Missing require View Binding parameter"

    // Required attributes
    val root = man?.root ?: woman?.root ?: throw IllegalArgumentException(errorMessage)
    val front = man?.front ?: woman?.front ?: throw IllegalArgumentException(errorMessage)
    val back = man?.back ?: woman?.back ?: throw IllegalArgumentException(errorMessage)

    // Optional attributes
    // FrontView
    val frontLuChest = man?.frontLuChest
    val fronRuChest = man?.frontRuChest
    val sideLAram = man?.sideLArm ?: woman?.sideLArm
    val sideRAram = man?.sideRArm ?: woman?.sideRArm


    // BackView
    val backLArm = man?.backLArm ?: woman?.backLArm

    companion object {

        fun getBinding(bindLayout: ViewBinding): SiteRotationViewAdapter {
            return when (bindLayout) {
                is DialogSiteRotationManBinding   -> SiteRotationViewAdapter(bindLayout)
                is DialogSiteRotationWomanBinding -> SiteRotationViewAdapter(null, bindLayout)
                else                              -> throw IllegalArgumentException("ViewBinding is not implement in WatchfaceViewAdapter")
            }
        }
    }

}