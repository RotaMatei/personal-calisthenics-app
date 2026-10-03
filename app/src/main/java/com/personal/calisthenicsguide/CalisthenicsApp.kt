package com.personal.calisthenicsguide

import android.app.Application
import com.personal.calisthenicsguide.data.AppDatabase
import com.personal.calisthenicsguide.data.Repository

class CalisthenicsApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(AppDatabase.create(this))
    }
}
