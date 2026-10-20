package com.motionloop.app.render.passes

/** A GPU stage in the render chain. Owns its GL resources, created and released on the GL thread. */
interface RenderPass {
    fun release()
}
