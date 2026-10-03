package com.personal.calisthenicsguide

import android.app.Application
import com.personal.calisthenicsguide.data.AppDatabase
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.session.SessionRunner

class CalisthenicsApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var sessionRunner: SessionRunner
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
        sessionRunner = SessionRunner(this, repository)
    }
}
