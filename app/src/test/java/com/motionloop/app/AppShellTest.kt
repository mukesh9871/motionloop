package com.motionloop.app

import com.motionloop.app.domain.model.DemoPhotos
import com.motionloop.app.domain.pro.ProFeature
import com.motionloop.app.domain.pro.ProPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppShellTest {

    @Test
    fun nothingIsLockedWhileGatingIsOff() {
        // Testers must get every feature until billing exists.
        assertFalse(ProPolicy.GATING_ENABLED)
        for (feature in ProFeature.entries) {
            assertFalse(feature.name, ProPolicy.isLocked(feature, isPro = false))
            assertFalse(feature.name, ProPolicy.isLocked(feature, isPro = true))
        }
    }

    @Test
    fun policyLocksTheRightFeaturesOnceGatingIsOn() {
        // What the Pro page promises and the sheet gates, independent of the master switch.
        assertTrue(ProFeature.LONG_EXPORT in ProPolicy.proOnly)
        assertTrue(ProFeature.EXPORT_4K in ProPolicy.proOnly)
        assertFalse(ProFeature.LIVE_WALLPAPER in ProPolicy.proOnly)
    }

    @Test
    fun proOnlyFeaturesAreDescribed() {
        for (feature in ProPolicy.proOnly) {
            assertTrue(feature.title.isNotBlank())
            assertTrue(feature.description.isNotBlank())
        }
    }

    @Test
    fun demoPhotosAreUniqueAndCredited() {
        val assets = DemoPhotos.all.map { it.asset }
        assertEquals(assets.size, assets.toSet().size)
        for (photo in DemoPhotos.all) {
            assertTrue(photo.assetPath.startsWith("demos/"))
            assertTrue(photo.author.isNotBlank())
            assertTrue(photo.sourceUrl.startsWith("https://commons.wikimedia.org/wiki/File:"))
        }
    }
}
