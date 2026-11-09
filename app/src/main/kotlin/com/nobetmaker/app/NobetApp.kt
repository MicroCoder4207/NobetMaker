package com.nobetmaker.app

import android.app.Application
import com.nobetmaker.app.data.Repository

class NobetApp : Application() {
    lateinit var repository: Repository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = Repository(this)
    }
}
