package app.aaps.core.objects.extensions

import app.aaps.core.data.model.TE.Rotation
import app.aaps.core.objects.R

fun Rotation.directionToIcon(): Int =
    when (this) {
        Rotation.UP         -> R.drawable.ic_singleup
        Rotation.UP_RIGHT   -> R.drawable.ic_fortyfiveup
        Rotation.RIGHT      -> R.drawable.ic_flat
        Rotation.DOWN_RIGHT -> R.drawable.ic_fortyfivedown
        Rotation.DOWN       -> R.drawable.ic_singledown
        Rotation.DOWN_LEFT  -> R.drawable.ic_fortyfivedown
        Rotation.LEFT       -> R.drawable.ic_flat
        Rotation.UP_LEFT    -> R.drawable.ic_fortyfiveup
        Rotation.CENTER     -> R.drawable.ic_invalid
        Rotation.NONE       -> R.drawable.ic_invalid
    }
