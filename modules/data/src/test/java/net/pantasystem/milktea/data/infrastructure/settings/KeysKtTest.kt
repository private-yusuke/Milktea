package net.pantasystem.milktea.data.infrastructure.settings

import net.pantasystem.milktea.model.setting.Keys
import net.pantasystem.milktea.model.setting.allKeys
import net.pantasystem.milktea.model.setting.str
import org.junit.Assert
import org.junit.Test

class KeysKtTest {

    @Test
    fun str() {
        Keys.allKeys.forEach { key ->
            when (key) {
                Keys.BackgroundImage -> Assert.assertEquals("BackgroundImage", key.str())
                Keys.ClassicUI -> Assert.assertEquals(
                    "HIDE_BOTTOM_NAVIGATION",
                    key.str()
                )
                Keys.IsPostButtonToBottom -> Assert.assertEquals(
                    "IS_POST_BUTTON_TO_BOTTOM",
                    key.str()
                )
                Keys.IsSimpleEditorEnabled -> Assert.assertEquals(
                    "IS_SIMPLE_EDITOR_ENABLED",
                    key.str()
                )
                Keys.IsUserNameDefault -> Assert.assertEquals(
                    "IS_USER_NAME_DEFAULT",
                    key.str()
                )
                Keys.NoteLimitHeight -> Assert.assertEquals(
                    "HEIGHT",
                    key.str()
                )
                Keys.ReactionPickerType -> Assert.assertEquals("ReactionPickerType", key.str())
                Keys.ThemeType -> Assert.assertEquals("THEME", key.str())

                Keys.IsIncludeLocalRenotes -> Assert.assertEquals(
                    "INCLUDE_LOCAL_RENOTES",
                    key.str()
                )
                Keys.IsIncludeMyRenotes -> Assert.assertEquals("INCLUDE_MY_RENOTES", key.str())
                Keys.IsIncludeRenotedMyNotes -> Assert.assertEquals(
                    "INCLUDE_RENOTED_MY_NOTES",
                    key.str()
                )
                Keys.SurfaceColorOpacity -> Assert.assertEquals(
                    "jp.panta.misskeyandroidclient.model.settings.SURFACE_COLOR_OPAQUE_KEY",
                    key.str()
                )
                Keys.IsEnableTimelineScrollAnimation -> Assert.assertEquals("IS_ENABLE_TIMELINE_SCROLL_ANIMATION", key.str())
                Keys.IsCrashlyticsCollectionEnabled -> Assert.assertEquals("IsCrashlyticsCollectionEnabled", key.str())
                Keys.IsConfirmedCrashlyticsCollection -> Assert.assertEquals("IsConfirmedCrashlyticsCollection", key.str())
                Keys.IsAnalyticsCollectionEnabled -> Assert.assertEquals("IsAnalyticsCollectionEnabled", key.str())
                Keys.IsConfirmedAnalyticsCollection -> Assert.assertEquals("IsConfirmedAnalyticsCollection", key.str())
                Keys.IsConfirmedPostNotification -> Assert.assertEquals("IsConfirmedPostNotification", key.str())
                Keys.IsEnableInstanceTicker -> Assert.assertEquals("IsEnableInstanceTicker", key.str())
                Keys.IsDriveUsingGridView -> Assert.assertEquals("IsDriveUsingGridView", key.str())
                Keys.IsEnableNotificationSound -> Assert.assertEquals("IsEnableNotificationSound", key.str())
            }
        }
    }


    @Test
    fun checkAllKeysCount() {
        Assert.assertEquals(21, Keys.allKeys.size)
        Assert.assertEquals(21, Keys.allKeys.map { it.str() }.toSet().size)
    }


}