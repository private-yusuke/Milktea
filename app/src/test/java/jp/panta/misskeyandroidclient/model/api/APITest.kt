package jp.panta.misskeyandroidclient.model.api

import net.pantasystem.milktea.api.misskey.DefaultOkHttpClientProvider
import net.pantasystem.milktea.api.misskey.MisskeyAPIServiceBuilder
import net.pantasystem.milktea.api.misskey.v11.MisskeyAPIV11
import net.pantasystem.milktea.model.instance.Version
import org.junit.Assert
import org.junit.Test

class APITest {

    @Test
    fun testV11Following(){
        val api = MisskeyAPIServiceBuilder(DefaultOkHttpClientProvider()).build("https://misskey.io", Version("12"))
        val v12 = api as? MisskeyAPIV11
        Assert.assertNotEquals(v12, null)

    }
}