package ge.usltd.btu_classroom

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import androidx.work.Configuration
import ge.usltd.btu_classroom.core.preferences.UserPreferencesStore
import ge.usltd.btu_classroom.core.sync.BtuWorkerFactory
import ge.usltd.btu_classroom.core.sync.SyncScheduler
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@HiltAndroidApp
class BTUClassroomApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: BtuWorkerFactory
    @Inject lateinit var preferencesStore: UserPreferencesStore
    @Inject lateinit var syncScheduler: SyncScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            preferencesStore.preferencesFlow.collectLatest(syncScheduler::update)
        }
    }
}
