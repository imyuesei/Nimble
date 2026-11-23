package app.nimble

import android.app.Application
import app.nimble.engine.CactusEngine
import app.nimble.harness.Harness
import app.nimble.models.ModelManager
import app.nimble.settings.AppSettings
import app.nimble.tools.Domains
import app.nimble.tools.NotesStore

/** Owns the app-wide singletons. */
class NimbleApp : Application() {
    lateinit var settings: AppSettings private set
    lateinit var models: ModelManager private set
    lateinit var engine: CactusEngine private set
    lateinit var permissions: PermissionGate private set
    lateinit var harness: Harness private set

    override fun onCreate() {
        super.onCreate()
        settings = AppSettings(this)
        models = ModelManager(this)
        engine = CactusEngine(models, settings)
        permissions = PermissionGate(this)
        val toolContext = AppToolContext(this, NotesStore(this), permissions)
        harness = Harness(engine, settings, Domains.all, toolContext)
    }
}
