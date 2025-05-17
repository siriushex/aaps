package app.aaps.core.objects.extensions

import app.aaps.core.data.model.TE.Arrow
import app.aaps.core.objects.R

fun Arrow.directionToIcon(): Int =
    when (this) {
        Arrow.UP         -> R.drawable.ic_singleup
        Arrow.UP_RIGHT   -> R.drawable.ic_fortyfiveup
        Arrow.RIGHT      -> R.drawable.ic_flat
        Arrow.DOWN_RIGHT -> R.drawable.ic_fortyfivedown
        Arrow.DOWN       -> R.drawable.ic_singledown
        Arrow.DOWN_LEFT  -> R.drawable.ic_fortyfivedown
        Arrow.LEFT       -> R.drawable.ic_flat
        Arrow.UP_LEFT    -> R.drawable.ic_fortyfiveup
        Arrow.CENTER     -> R.drawable.ic_center
        Arrow.NONE       -> R.drawable.ic_invalid
    }
