package com.focusreels.app

import android.app.Application
import com.focusreels.app.data.db.AppDatabase

class FocusReelsApplication : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
}
