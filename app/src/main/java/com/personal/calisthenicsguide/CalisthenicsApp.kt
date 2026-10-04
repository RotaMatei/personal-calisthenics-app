package com.personal.calisthenicsguide

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.personal.calisthenicsguide.data.AppDatabase
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.session.SessionRunner
import com.personal.calisthenicsguide.ui.guide.MeshAssets

class CalisthenicsApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var sessionRunner: SessionRunner
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
        sessionRunner = SessionRunner(this, repository)
        // A session row with no sets and no end time can only come from a killed app; drop it.
        CoroutineScope(Dispatchers.IO).launch { runCatching { repository.cleanUpAbandoned() } }
        // Open the 3D body mesh in the background so the first clip does not wait for it.
        CoroutineScope(Dispatchers.Default).launch { MeshAssets.load(this@CalisthenicsApp) }
    }
}
