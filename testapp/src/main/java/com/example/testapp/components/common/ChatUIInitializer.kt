package com.example.testapp.components.common

import android.content.Context
import android.os.Build
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.decode.SvgDecoder
import coil.util.CoilUtils
import com.example.testapp.common.coil.TinuiCoil
import com.example.testapp.components.ChatUI
import com.example.testapp.common.coil.TinuiImageLoaderFactory
import com.example.testapp.components.common.internal.AvatarFetcher
import okhttp3.Dispatcher
import okhttp3.Interceptor
import okhttp3.OkHttpClient

/**
 * Jetpack Startup Initializer for Stream's Chat UI Components.
 */
public class ChatUIInitializer {
    public companion object {
        @JvmStatic
        public fun create(context: Context): ChatUI {
            ChatUI.appContext = context

            val imageLoaderFactory = TinuiImageLoaderFactory(context) {
                okHttpClient {
                    val cacheControlInterceptor = Interceptor { chain ->
                        chain.proceed(chain.request())
                            .newBuilder()
                            .header("Cache-Control", "max-age=3600,public")
                            .build()
                    }
                    // Don't limit concurrent network requests by host.
                    val dispatcher = Dispatcher().apply { maxRequestsPerHost = maxRequests }

                    OkHttpClient.Builder()
                        .cache(CoilUtils.createDefaultCache(context))
                        .dispatcher(dispatcher)
                        .addNetworkInterceptor(cacheControlInterceptor)
                        .build()
                }
                componentRegistry {
                    // duplicated as we can not extend component
                    // registry of existing image loader builder
                    add(SvgDecoder(context))
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        add(ImageDecoderDecoder(context))
                    } else {
                        add(GifDecoder())
                    }
                    add(AvatarFetcher())
                }
            }
            TinuiCoil.setImageLoader(imageLoaderFactory)

            return ChatUI
        }
    }
}
