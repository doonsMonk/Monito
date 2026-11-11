package com.monito.expense

import android.app.Application
import com.monito.expense.work.Notifications
import com.monito.expense.work.RecurrenceRunner
import com.monito.expense.work.Scheduler
import kotlin.concurrent.thread

class MonitoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        runCatching { Notifications.createChannels(this) }
        runCatching { Scheduler.scheduleAll(this) }
        thread(name = "monito-startup") {
            runCatching { RecurrenceRunner.run(this) }
        }
    }
}
