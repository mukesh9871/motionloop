package com.motionloop.app.render.tiles

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** The renderer for effect cards and Help pictures. Null in previews and tests: tiles fall back to drawings. */
val LocalTileRenderer = staticCompositionLocalOf<TileRenderer?> { null }

/**
 * The rendered picture of [key], or null while it is not ready (or cannot be rendered).
 * With [animated], the loop frames are asked for too.
 */
@Composable
fun rememberTile(key: TileKey, shape: TileShape = TileShape.SQUARE, animated: Boolean = false): TileImage? {
    val renderer = LocalTileRenderer.current ?: return null
    val request = remember(key, shape) { TileRequest(key, shape) }
    val loop = animated && key.moves
    LaunchedEffect(request, loop) { renderer.request(request, loop) }
    // While this tile plays the loop, its frames are not evicted for other tiles.
    DisposableEffect(request, loop) {
        if (loop) renderer.retain(request)
        onDispose { if (loop) renderer.releaseLoop(request) }
    }
    // Only this tile's entry: other tiles finishing do not recompose this one.
    val flow = remember(request) { renderer.images.map { it[request] }.distinctUntilChanged() }
    val image by flow.collectAsState(renderer.images.value[request])
    return image
}
