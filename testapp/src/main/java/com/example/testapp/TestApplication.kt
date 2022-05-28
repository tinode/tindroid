package com.example.testapp

import android.app.Application
import android.os.Build
import android.util.Log
import com.example.testapp.client.ChatClient
import com.example.testapp.client.models.UserBasicCredentials
import com.example.testapp.common.images.ImageHeadersProvider
import com.example.testapp.components.common.ChatUIInitializer
import com.example.testapp.livedata.plugin.LiveDataPluginFactory
import com.example.testapp.logger.ChatLogLevel

public class TestApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        val liveDataPlugin = LiveDataPluginFactory(
            appContext = this
        )

        val ui = ChatUIInitializer.create(this)

        val client = ChatClient.Builder(this).apply {
            appName("Tindroid/Test-App")
            osName(Build.VERSION.RELEASE)
            withPlugin(liveDataPlugin)
            logLevel(ChatLogLevel.ALL)
        }.build()

        client.connectUser(UserBasicCredentials(username = "bob", password = "bob123")).enqueue {
            if (it.isSuccess) {
                Log.i("TestApplication", "${it.data().user.id} : ${it.data().user.name}")
            } else {
                Log.e("TestApplication", it.error().message, it.error().cause)
            }
        }

        ui.imageHeadersProvider = object : ImageHeadersProvider {
            override fun getImageRequestHeaders(): Map<String, String> {
                Log.d("getImageRequestHeaders", client.socket.requestHeaders.orEmpty().toString())
                return client.socket.requestHeaders.orEmpty()
            }
        }

    }
}