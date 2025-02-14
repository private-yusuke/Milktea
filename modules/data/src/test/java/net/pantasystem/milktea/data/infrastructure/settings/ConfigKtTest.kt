package net.pantasystem.milktea.data.infrastructure.settings

import net.pantasystem.milktea.model.setting.*
import org.junit.Assert
import org.junit.Test

class ConfigKtTest {


    @Test
    fun allKeys() {
        Assert.assertEquals(
            Keys::class.nestedClasses.map { it.simpleName }.filterNot { it == "Companion" }.toSet(),
            Keys.allKeys.map { it::class }.map { it.simpleName }.toSet()
        )
    }

    @Test
    fun prefs() {
        val config = DefaultConfig.config.copy(
            theme = Theme.Bread,
            surfaceColorOpacity = 250,
            isClassicUI = true,
            isPostButtonAtTheBottom = false,
            isUserNameDefault = false,
            isSimpleEditorEnabled = true,
            isIncludeMyRenotes = false,
            isIncludeRenotedMyNotes = false,
            isIncludeLocalRenotes = false,
        )
        Assert.assertNotNull(config.prefs()[Keys.ThemeType])
        config.prefs().forEach { (k, u) ->
            when (k) {
                Keys.BackgroundImage -> Assert.assertEquals(
                    config.backgroundImagePath,
                    (u as PrefType.StrPref).value
                )
                Keys.ClassicUI -> Assert.assertEquals(
                    config.isClassicUI,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsPostButtonToBottom -> Assert.assertEquals(
                    config.isPostButtonAtTheBottom,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsSimpleEditorEnabled -> Assert.assertEquals(
                    config.isSimpleEditorEnabled,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsUserNameDefault -> Assert.assertEquals(
                    config.isUserNameDefault,
                    (u as PrefType.BoolPref).value
                )
                Keys.NoteLimitHeight -> Assert.assertEquals(
                    config.noteExpandedHeightSize,
                    (u as PrefType.IntPref).value
                )
                Keys.ReactionPickerType -> Assert.assertEquals(
                    config.reactionPickerType.ordinal,
                    (u as PrefType.IntPref).value
                )
                Keys.ThemeType -> Assert.assertEquals(
                    (config.theme.toInt()), (u as PrefType.IntPref).value
                )
                Keys.IsIncludeLocalRenotes -> Assert.assertEquals(
                    config.isIncludeLocalRenotes, (u as PrefType.BoolPref).value
                )
                Keys.IsIncludeMyRenotes -> Assert.assertEquals(
                    config.isIncludeMyRenotes, (u as PrefType.BoolPref).value
                )
                Keys.IsIncludeRenotedMyNotes -> Assert.assertEquals(
                    config.isIncludeRenotedMyNotes, (u as PrefType.BoolPref).value
                )
                Keys.SurfaceColorOpacity -> Assert.assertEquals(
                    config.surfaceColorOpacity, (u as PrefType.IntPref).value
                )
                Keys.IsEnableTimelineScrollAnimation -> Assert.assertEquals(
                    config.isEnableTimelineScrollAnimation,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsCrashlyticsCollectionEnabled -> Assert.assertEquals(
                    config.isCrashlyticsCollectionEnabled.isEnable,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsConfirmedCrashlyticsCollection -> Assert.assertEquals(
                    config.isCrashlyticsCollectionEnabled.isConfirmed,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsAnalyticsCollectionEnabled -> Assert.assertEquals(
                    config.isAnalyticsCollectionEnabled.isEnabled,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsConfirmedAnalyticsCollection -> Assert.assertEquals(
                    config.isCrashlyticsCollectionEnabled.isConfirmed,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsConfirmedPostNotification -> Assert.assertEquals(
                    config.isConfirmedPostNotification,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsEnableInstanceTicker -> Assert.assertEquals(
                    config.isEnableInstanceTicker,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsDriveUsingGridView -> Assert.assertEquals(
                    config.isDriveUsingGridView,
                    (u as PrefType.BoolPref).value
                )
                Keys.IsEnableNotificationSound -> Assert.assertEquals(
                    config.isEnableNotificationSound,
                    (u as PrefType.BoolPref).value
                )
            }
        }
    }

    @Test
    fun from() {
        Assert.assertEquals(
            Theme.Bread, Config.from(
                mapOf(
                    Keys.ThemeType to PrefType.IntPref(Theme.Bread.toInt())
                )
            ).theme
        )

    }
}