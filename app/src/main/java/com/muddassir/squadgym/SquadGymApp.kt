package com.muddassir.squadgym

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import okio.Path.Companion.toPath
import com.muddassir.squadgym.core.di.AppContainer
import com.muddassir.squadgym.work.BackgroundSync

class SquadGymApp : Application(), SingletonImageLoader.Factory {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        BackgroundSync.schedule(this)
    }

    /**
     * Coil shares the authenticated OkHttp client, so private profile images load
     * with the session token and are cached on disk (spec §9).
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { container.authorizedClient }))
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("squadgym_images").absolutePath.toPath())
                    .maxSizeBytes(120L * 1024 * 1024)
                    .build()
            }
            .crossfade(180)
            .build()
}
