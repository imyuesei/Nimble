package app.nimble

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import app.nimble.harness.ToolContext
import app.nimble.tools.NotesStore
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Lets non-UI code (tools, voice) ask for runtime permissions through the visible activity. */
class PermissionGate(private val context: Context) {
    /** Set by MainActivity while it is alive; launches the system permission dialog. */
    @Volatile var requester: ((String) -> Unit)? = null
    private var pending: CompletableDeferred<Boolean>? = null
    private val mutex = Mutex()

    fun isGranted(permission: String) =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    suspend fun request(permission: String): Boolean {
        if (isGranted(permission)) return true
        return mutex.withLock {
            if (isGranted(permission)) return@withLock true
            val launch = requester ?: return@withLock false
            val result = CompletableDeferred<Boolean>()
            pending = result
            withContext(Dispatchers.Main) { launch(permission) }
            result.await()
        }
    }

    fun onResult(granted: Boolean) {
        pending?.complete(granted)
        pending = null
    }
}

class AppToolContext(
    override val context: Context,
    override val notes: NotesStore,
    private val permissions: PermissionGate,
) : ToolContext {
    override suspend fun requestPermission(permission: String) = permissions.request(permission)

    override fun launch(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }
}
