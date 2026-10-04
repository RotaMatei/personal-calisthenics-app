package com.personal.calisthenicsguide

import android.app.Application
import com.personal.calisthenicsguide.data.AppDatabase
import com.personal.calisthenicsguide.data.Repository
import com.personal.calisthenicsguide.feedback.Feedback
import com.personal.calisthenicsguide.session.SessionController

class CalisthenicsApp : Application() {
    lateinit var repository: Repository
        private set
    lateinit var sessionController: SessionController
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
        sessionController = SessionController(this, repository, Feedback(this))
    }
}
