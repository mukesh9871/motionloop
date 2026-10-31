package com.motionloop.app

import com.motionloop.app.data.StorageSweeper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSweeperTest {

    @Test
    fun leftoversOfInterruptedWritesAreSwept() {
        assertTrue(StorageSweeper.isLeftover("project.json.tmp"))
        assertTrue(StorageSweeper.isLeftover("sky_3.mask.tmp"))
        assertTrue(StorageSweeper.isLeftover("audio_2.audio.tmp"))
        assertTrue(StorageSweeper.isLeftover("sky_photo_import_123456789.jpg"))
        assertTrue(StorageSweeper.isLeftover("background_import_42.jpg.tmp"))
    }

    @Test
    fun projectFilesAreKept() {
        listOf(
            "project.json", "project.json.bak", "source.jpg", "thumb.jpg", "sky_3.mask", "subject_1.mask",
            "depth_2.mask", "selection_7.mask", "sky_photo_2.jpg", "background_1.jpg", "audio_2.audio", "grade.cube",
        ).forEach { assertFalse(it, StorageSweeper.isLeftover(it)) }
    }
}
