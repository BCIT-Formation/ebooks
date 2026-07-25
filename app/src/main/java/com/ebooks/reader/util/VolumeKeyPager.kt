package com.ebooks.reader.util

/**
 * Routes hardware volume-key presses from [com.ebooks.reader.MainActivity]
 * to the currently open reader (E-ink extra: many e-readers have physical
 * volume buttons but poor touch response). A reader registers a [handler]
 * while its volume-key pagination setting is on; when no handler is set the
 * keys keep their normal volume behaviour.
 */
object VolumeKeyPager {

    /** Called with `forward = true` for volume-down (page forward). Returns true when consumed. */
    @Volatile
    var handler: ((forward: Boolean) -> Boolean)? = null

    val isActive: Boolean get() = handler != null

    fun dispatch(forward: Boolean): Boolean = handler?.invoke(forward) == true
}
