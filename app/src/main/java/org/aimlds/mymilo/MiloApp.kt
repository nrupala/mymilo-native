package org.aimlds.mymilo

import android.app.Application
import org.aimlds.mymilo.data.MiloDatabase
import org.aimlds.mymilo.network.MiloApiClient

/**
 * Application entry point. Holds the singletons:
 * local database (offline-first) and the API client (server escalation).
 */
class MiloApp : Application() {

    lateinit var db: MiloDatabase
        private set
    lateinit var api: MiloApiClient
        private set

    override fun onCreate() {
        super.onCreate()
        db = MiloDatabase.build(this)
        api = MiloApiClient(this)
    }
}
