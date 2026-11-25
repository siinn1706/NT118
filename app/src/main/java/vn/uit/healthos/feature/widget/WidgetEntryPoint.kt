package vn.uit.healthos.feature.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import vn.uit.healthos.feature.hydration.HydrationRepository
import vn.uit.healthos.feature.meds.DoseCacheDao
import vn.uit.healthos.feature.meds.MedsRepository
import vn.uit.healthos.feature.safety.SafetyPreferences
import vn.uit.healthos.feature.steps.StepCounterRepository

/** Glance widgets and their action callbacks are not Hilt-injectable; they reach the graph through this. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {
    fun todaySnapshotRepository(): TodaySnapshotRepository
    fun hydrationRepository(): HydrationRepository
    fun stepCounterRepository(): StepCounterRepository
    fun medsRepository(): MedsRepository
    fun doseCacheDao(): DoseCacheDao
    fun safetyPreferences(): SafetyPreferences
    fun widgetUpdater(): WidgetUpdater
}

fun widgetEntryPoint(context: Context): WidgetEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
