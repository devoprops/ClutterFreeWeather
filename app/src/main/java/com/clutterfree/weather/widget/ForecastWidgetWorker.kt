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
import com.clutterfree.weather.data.NwsWeatherRepository
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
    private const val PERIODIC = "saved-place-forecast"
    private const val IMMEDIATE = "saved-place-forecast-now"

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

    fun cancel(context: Context) {
        val manager = WorkManager.getInstance(context)
        manager.cancelUniqueWork(PERIODIC)
        manager.cancelUniqueWork(IMMEDIATE)
    }

    private fun connected() = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
}
