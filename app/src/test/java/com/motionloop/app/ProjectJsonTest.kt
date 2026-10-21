package com.motionloop.app

import com.motionloop.app.domain.audio.AmbientSound
import com.motionloop.app.domain.audio.AudioSettings
import com.motionloop.app.domain.audio.AudioSource
import com.motionloop.app.domain.model.Adjustments
import com.motionloop.app.domain.model.Anchor
import com.motionloop.app.domain.model.Atmosphere
import com.motionloop.app.domain.model.BackgroundMode
import com.motionloop.app.domain.model.BackgroundSettings
import com.motionloop.app.domain.model.BlendMode
import com.motionloop.app.domain.model.CameraPath
import com.motionloop.app.domain.model.DispersionSettings
import com.motionloop.app.domain.model.EffectsSettings
import com.motionloop.app.domain.model.LightLeak
import com.motionloop.app.domain.model.LoopMode
import com.motionloop.app.domain.model.LoopSettings
import com.motionloop.app.domain.model.MaskMode
import com.motionloop.app.domain.model.MaskStroke
import com.motionloop.app.domain.model.MotionArrow
import com.motionloop.app.domain.model.NPoint
import com.motionloop.app.domain.model.OverlaySettings
import com.motionloop.app.domain.model.ParallaxSettings
import com.motionloop.app.domain.model.ParticleType
import com.motionloop.app.domain.model.Project
import com.motionloop.app.domain.model.RippleSettings
import com.motionloop.app.domain.model.SelectionMode
import com.motionloop.app.domain.model.SelectionRegion
import com.motionloop.app.domain.model.SkySettings
import com.motionloop.app.domain.model.SkyStyle
import com.motionloop.app.domain.model.Sticker
import com.motionloop.app.domain.model.StickerKind
import com.motionloop.app.domain.model.SubjectSettings
import com.motionloop.app.domain.model.TextAnimation
import com.motionloop.app.domain.model.TextFont
import com.motionloop.app.domain.model.TextLayer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProjectJsonTest {

    // The repository's configuration.
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun fullProjectRoundTripsThroughJson() {
        val stroke = MaskStroke(listOf(NPoint(0.1f, 0.2f), NPoint(0.3f, 0.4f)), radius = 0.05f, hardness = 0.7f, mode = MaskMode.FREEZE)
        val erase = stroke.copy(mode = MaskMode.UNFREEZE)
        val project = Project(
            id = "p1",
            createdAt = 1L,
            updatedAt = 2L,
            imageWidth = 4000,
            imageHeight = 3000,
            arrows = listOf(
                MotionArrow("a", NPoint(0.1f, 0.1f), NPoint(0.5f, 0.5f)),
                MotionArrow("b", NPoint(0.2f, 0.2f), NPoint(0.6f, 0.4f), listOf(NPoint(0.2f, 0.2f), NPoint(0.4f, 0.1f), NPoint(0.6f, 0.4f))),
            ),
            anchors = listOf(Anchor("c", NPoint(0.5f, 0.9f), radius = 0.05f)),
            strokes = listOf(stroke, erase),
            loop = LoopSettings(mode = LoopMode.BOOMERANG, speed = 1.5f),
            adjustments = Adjustments(brightness = 0.1f, contrast = -0.2f, saturation = 0.3f, warmth = 0.4f, gradeId = "teal", gradeIntensity = 0.5f),
            overlay = OverlaySettings(
                particles = ParticleType.HEARTS, density = 0.3f, size = 0.7f, speed = 2, lightLeak = LightLeak.ROSE,
                leakIntensity = 0.4f, atmosphere = Atmosphere.FOG, atmosphereAmount = 0.6f, blend = BlendMode.ADD,
                opacity = 0.9f, areaStrokes = listOf(stroke),
            ),
            sky = SkySettings(enabled = true, style = SkyStyle.PHOTO, maskRev = 3, strokes = listOf(stroke), drift = 0.2f, feather = 0.1f, harmonize = 0.8f, photoRev = 2),
            parallax = ParallaxSettings(enabled = true, path = CameraPath.FLOAT, amount = 0.7f, focus = 0.3f, depthRev = 4),
            subject = SubjectSettings(maskRev = 5, strokes = listOf(erase), feather = 0.4f),
            background = BackgroundSettings(mode = BackgroundMode.PHOTO, color1 = 1, color2 = 2, blur = 0.5f, photoRev = 6),
            selections = listOf(SelectionRegion(7, SelectionMode.FREEZE), SelectionRegion(8, SelectionMode.RIPPLE)),
            texts = listOf(
                TextLayer("t1", "Hello", TextFont.SERIF_ITALIC, 0xFF123456.toInt(), 0.2f, 15f, 0.1f, true, true, 0xFF654321.toInt(), 0.3f, 0.7f, TextAnimation.TYPEWRITER, false),
                TextLayer("t2"),
            ),
            audio = AudioSettings(source = AudioSource.FILE, ambient = AmbientSound.FIRE, fileRev = 9, volume = 0.6f, beatSync = true, beatAmount = 0.4f),
            effects = EffectsSettings(
                stickers = listOf(Sticker("s1", StickerKind.BIRDS, 0.2f, 0.3f, 0.25f, 30f)),
                dispersion = DispersionSettings(enabled = true, strokes = listOf(stroke), angle = 45f, distance = 0.8f, amount = 0.9f),
                ripple = RippleSettings(strength = 0.7f),
            ),
        )
        val text = json.encodeToString(Project.serializer(), project)
        assertEquals(project, json.decodeFromString(Project.serializer(), text))
    }
}
