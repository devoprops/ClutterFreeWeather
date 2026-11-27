package com.clutterfree.weather.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.clutterfree.weather.data.NwsWeatherRepository
import com.clutterfree.weather.data.WeatherStore
import com.clutterfree.weather.weather.AppConfig
import com.clutterfree.weather.weather.RefreshResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

class ForecastWidgetWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (inputData.getBoolean(WidgetRefresh.SEEK, false)) {
            val attempt = inputData.getInt(WidgetRefresh.ATTEMPT, 1)
            val stored = withContext(Dispatchers.IO) {
                val store = WeatherStore(applicationContext.filesDir)
                store.readPlace() to store.readSnapshot()
            }
            if (stored.first == null) {
                updateWeatherWidgets(applicationContext)
                WidgetRefresh.enqueueSeek(applicationContext, attempt + 1)
                return Result.success()
            }
            WidgetRefresh.cancelSeek(applicationContext, attempt + 1)
            if (stored.second != null) {
                updateWeatherWidgets(applicationContext)
                return Result.success()
            }
        }
        val result = withContext(Dispatchers.IO) {
            NwsWeatherRepository(applicationContext.filesDir).refresh()
        }
        updateWeatherWidgets(applicationContext)
        return when (result) {
            is RefreshResult.Failed -> Result.retry()
            else -> Result.success()
        }
    }
}

object WidgetRefresh {
    const val SEEK = "seek"
    const val ATTEMPT = "attempt"

    private const val PERIODIC = "saved-place-forecast"
    private const val IMMEDIATE = "saved-place-forecast-now"
    private const val SEEK_WORK = "saved-place-seek"

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<ForecastWidgetWorker>(
            AppConfig.WIDGET_REFRESH_MINUTES,
            TimeUnit.MINUTES,
        ).setConstraints(connected()).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    fun runNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<ForecastWidgetWorker>()
            .setConstraints(connected())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            IMMEDIATE,
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun startSeek(context: Context) {
        enqueueSeek(context, attempt = 1)
    }

    fun enqueueSeek(context: Context, attempt: Int) {
        if (attempt > AppConfig.WIDGET_SEEK_ATTEMPTS) return
        val request = OneTimeWorkRequestBuilder<ForecastWidgetWorker>()
            .setConstraints(connected())
            .setInputData(workDataOf(SEEK to true, ATTEMPT to attempt))
            .apply {
                if (attempt > 1) setInitialDelay(AppConfig.WIDGET_SEEK_SECONDS, TimeUnit.SECONDS)
            }
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "$SEEK_WORK-$attempt",
            ExistingWorkPolicy.KEEP,
            request,
        )
    }

    fun cancelSeek(context: Context, fromAttempt: Int = 1) {
        val manager = WorkManager.getInstance(context)
        for (attempt in fromAttempt..AppConfig.WIDGET_SEEK_ATTEMPTS) {
            manager.cancelUniqueWork("$SEEK_WORK-$attempt")
        }
    }

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC)
        manager.cancelUniqueWork(IMMEDIATE)
        cancelSeek(context)
    }

    private fun connected() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
