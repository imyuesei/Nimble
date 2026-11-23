package app.nimble.harness

import app.nimble.engine.CactusEngine
import app.nimble.settings.AppSettings
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A call Needle proposed that waits for the user's yes/no. */
data class PendingCall(val domain: Domain, val tool: Tool, val args: JSONObject, val reasoning: String?)

sealed interface Reply {
    data class Done(val text: String) : Reply
    data class NeedsConfirm(val prompt: String, val call: PendingCall) : Reply
}

/**
 * The Nimble loop, a Hermes-style agent loop reshaped for Needle 3:
 *
 *  1. Route: keyword match, else a Needle session whose tools are the domains.
 *  2. Act: a Needle session holding only the chosen domain's tools (5 or fewer).
 *  3. Gate: low confidence, a withheld call or a risky tool -> ask the user.
 *  4. Execute in Kotlin, feed the result back, repeat for up to [MAX_STEPS].
 *
 * Needle never writes prose, so every reply comes from the tools' own [ToolResult.say].
 */
class Harness(
    private val engine: CactusEngine,
    private val settings: AppSettings,
    private val domains: List<Domain>,
    private val toolContext: ToolContext,
) {
    private val routerJson = JSONArray(domains.map { it.routerSchema() }).toString()

    suspend fun handle(text: String): Reply {
        val domain = route(text) ?: return Reply.Done(REFUSAL)
        var response = engine.complete(system(), domain.toolsJson, text)
        val said = ArrayList<String>()
        val seen = HashSet<String>()

        for (step in 0 until MAX_STEPS) {
            val calls = response.calls("function_calls")
            if (response.optString("type") != "call" || calls.isEmpty()) {
                if (step == 0) {
                    // Nothing confident enough: offer a withheld call, else refuse.
                    val held = response.calls("suppressed_calls").firstOrNull()
                    val tool = held?.let { domain.find(it.name) }
                    if (held != null && tool != null) {
                        return confirm(domain, tool, held.args, response)
                    }
                    return Reply.Done(REFUSAL)
                }
                break
            }

            val confidence = response.optDouble("confidence", Double.NaN)
            val results = JSONArray()
            var progressed = false
            for (call in calls) {
                if (!seen.add(call.signature())) continue // the model repeated itself
                val tool = domain.find(call.name)
                if (tool == null) {
                    results.put(JSONObject().put("error", "unknown tool: ${call.name}"))
                    continue
                }
                val unsure = !confidence.isNaN() && confidence < settings.confidence
                if (tool.risk == Risk.CONFIRM || (step == 0 && unsure)) {
                    if (said.isEmpty()) return confirm(domain, tool, call.args, response)
                    said += "Skipped ${tool.preview(call.args)}: say it again on its own to run it."
                    continue
                }
                val result = execute(tool, call.args)
                said += result.say
                results.put(result.data)
                progressed = true
            }
            if (!progressed) break
            response = engine.continueWith(results.toString())
        }
        return Reply.Done(said.joinToString("\n").ifBlank { REFUSAL })
    }

    /** Runs a call the user approved. */
    suspend fun runConfirmed(call: PendingCall): String = execute(call.tool, call.args).say

    private suspend fun execute(tool: Tool, args: JSONObject): ToolResult = try {
        tool.run(toolContext, args)
    } catch (e: Exception) {
        ToolResult.error("${tool.name.replace('_', ' ')} failed: ${e.message ?: e}")
    }

    private fun confirm(domain: Domain, tool: Tool, args: JSONObject, response: JSONObject): Reply {
        val reasoning = response.optString("reasoning").ifBlank { null }
        return Reply.NeedsConfirm("Do this? ${tool.preview(args)}", PendingCall(domain, tool, args, reasoning))
    }

    private suspend fun route(text: String): Domain? {
        val hits = domains.filter { it.keywords.containsMatchIn(text) }
        if (hits.size == 1) return hits.first()
        val response = engine.complete(system(), routerJson, text, maxNewTokens = 64)
        val name = (response.calls("function_calls") + response.calls("suppressed_calls")).firstOrNull()?.name
        return domains.firstOrNull { it.name == name } ?: hits.firstOrNull()
    }

    /** Environment facts in the form Needle expects (facts only, never instructions). */
    private fun system(): String {
        val now = SimpleDateFormat("yyyy-MM-dd EEE HH:mm", Locale.US).format(Date())
        val locale = Locale.getDefault().toLanguageTag()
        return "date: $now; locale: $locale; device: phone"
    }

    private data class Call(val name: String, val args: JSONObject) {
        fun signature() = name + args.toString()
    }

    private fun JSONObject.calls(key: String): List<Call> {
        val array = optJSONArray(key) ?: return emptyList()
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val args = o.optJSONObject("arguments")
                ?: o.optString("arguments").takeIf { it.startsWith("{") }?.let { JSONObject(it) }
                ?: JSONObject()
            Call(o.optString("name"), args)
        }
    }

    companion object {
        const val MAX_STEPS = 3
        const val REFUSAL = "I can't do that yet. Try alarms, timers, calls, texts, notes or phone controls."
    }
}
