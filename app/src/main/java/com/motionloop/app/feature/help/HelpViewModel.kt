package com.motionloop.app.feature.help

import androidx.lifecycle.ViewModel
import com.motionloop.app.render.tiles.TileRenderer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/** Hands Help the app-wide renderer, so its pictures are the same renders the effect cards use. */
@HiltViewModel
class HelpViewModel @Inject constructor(val tiles: TileRenderer) : ViewModel()
