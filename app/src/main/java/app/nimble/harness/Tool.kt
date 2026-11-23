package app.nimble.harness

import android.content.Context
import android.content.Intent
import app.nimble.tools.NotesStore
import org.json.JSONArray
import org.json.JSONObject

enum class Risk {
    /** Runs when Needle is confident enough. */
    SAFE,
    /** Always asks the user first. */
    CONFIRM,
}

/** What a tool reports: [say] is shown in chat, [data] is fed back to Needle as the tool result. */
data class ToolResult(val say: String, val data: JSONObject = JSONObject().put("ok", true)) {
    companion object {
        fun error(say: String) = ToolResult(say, JSONObject().put("ok", false).put("error", say))
    }
}

/** Everything a tool may touch on the device. */
interface ToolContext {
    val context: Context
    val notes: NotesStore
    /** Asks for a runtime permission if needed; true when granted. */
    suspend fun requestPermission(permission: String): Boolean
    /** Starts an activity from the app (adds NEW_TASK); false if no app can handle it. */
    fun launch(intent: Intent): Boolean
}

/** One JSON-schema argument. Keep tools to a few flat, well-described arguments. */
data class Param(
    val name: String,
    val type: String,
    val description: String,
    val required: Boolean = true,
    val enum: List<String>? = null,
    val minimum: Int? = null,
    val maximum: Int? = null,
    val maxLength: Int? = null,
)

/**
 * A device action Needle can call. The schema is what the model sees; [run] is plain Kotlin.
 * Needle reads every tool of a domain in its prompt, so names stay short and distinct.
 */
abstract class Tool(
    val name: String,
    val description: String,
    val params: List<Param> = emptyList(),
    val risk: Risk = Risk.SAFE,
) {
    abstract suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult

    /** One line shown when asking the user to confirm this call. */
    open fun preview(args: JSONObject): String {
        val shown = params.mapNotNull { p -> args.opt(p.name)?.let { "${p.name}: $it" } }
        return if (shown.isEmpty()) name.replace('_', ' ') else "${name.replace('_', ' ')} (${shown.joinToString(", ")})"
    }

    fun schema(): JSONObject {
        val properties = JSONObject()
        for (p in params) {
            val prop = JSONObject().put("type", p.type).put("description", p.description)
            p.enum?.let { prop.put("enum", JSONArray(it)) }
            p.minimum?.let { prop.put("minimum", it) }
            p.maximum?.let { prop.put("maximum", it) }
            p.maxLength?.let { prop.put("maxLength", it) }
            properties.put(p.name, prop)
        }
        val parameters = JSONObject().put("type", "object").put("properties", properties)
        val required = params.filter { it.required }.map { it.name }
        if (required.isNotEmpty()) parameters.put("required", JSONArray(required))
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("parameters", parameters)
    }
}

/**
 * A small pack of related tools (Needle works best with five or fewer per session).
 * The router picks a domain first, then a Needle session with only that domain's tools runs.
 */
class Domain(
    val name: String,
    val routerDescription: String,
    /** Cheap keyword pre-routing: when exactly one domain matches, Needle routing is skipped. */
    val keywords: Regex,
    val tools: List<Tool>,
) {
    init {
        require(tools.size <= 5) { "Domain $name has ${tools.size} tools; keep it to 5 or fewer" }
    }

    val toolsJson: String by lazy { JSONArray(tools.map { it.schema() }).toString() }

    fun find(name: String): Tool? = tools.firstOrNull { it.name == name }

    /** The router sees each domain as one argument-free tool. */
    fun routerSchema(): JSONObject = JSONObject()
        .put("name", name)
        .put("description", routerDescription)
        .put("parameters", JSONObject().put("type", "object").put("properties", JSONObject()))
}
