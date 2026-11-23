package app.nimble.tools

import android.content.Intent
import android.provider.AlarmClock
import app.nimble.harness.Domain
import app.nimble.harness.Param
import app.nimble.harness.TimeParser
import app.nimble.harness.Tool
import app.nimble.harness.ToolContext
import app.nimble.harness.ToolResult
import org.json.JSONObject

object ClockTools {
    val domain = Domain(
        name = "alarms_timers",
        routerDescription = "Alarms that ring at a clock time, countdown timers, and showing alarms.",
        keywords = Regex("\\b(alarm|alarms|timer|timers|wake me|countdown)\\b", RegexOption.IGNORE_CASE),
        tools = listOf(SetAlarm, SetTimer, ShowAlarms),
    )

    object SetAlarm : Tool(
        name = "set_alarm",
        description = "Set an alarm that rings at a clock time.",
        params = listOf(
            Param("time_human", "string", "The alarm time copied word for word, e.g. '7am', '6:30 pm'.", maxLength = 40),
            Param("label", "string", "A short alarm label; include only when the user names one.", required = false, maxLength = 60),
        ),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val phrase = args.optString("time_human")
            val (hour, minute) = TimeParser.clock(phrase)
                ?: return ToolResult.error("I couldn't read the time \"$phrase\".")
            val label = args.optString("label").ifBlank { null }
            val intent = Intent(AlarmClock.ACTION_SET_ALARM)
                .putExtra(AlarmClock.EXTRA_HOUR, hour)
                .putExtra(AlarmClock.EXTRA_MINUTES, minute)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (!ctx.launch(intent)) return ToolResult.error("No clock app can set alarms.")
            val time = TimeParser.formatClock(hour, minute)
            return ToolResult(
                "Alarm set for $time" + (label?.let { " ($it)" } ?: "") + ".",
                JSONObject().put("ok", true).put("time", time),
            )
        }
    }

    object SetTimer : Tool(
        name = "set_timer",
        description = "Start a countdown timer for a duration.",
        params = listOf(
            Param("duration_human", "string", "The duration copied word for word, e.g. '10 minutes', '1 hour 30 minutes'.", maxLength = 40),
            Param("label", "string", "A short timer label; include only when the user names one.", required = false, maxLength = 60),
        ),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val phrase = args.optString("duration_human")
            val seconds = TimeParser.durationSeconds(phrase)?.coerceAtMost(86_400)
                ?: return ToolResult.error("I couldn't read the duration \"$phrase\".")
            val label = args.optString("label").ifBlank { null }
            val intent = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            label?.let { intent.putExtra(AlarmClock.EXTRA_MESSAGE, it) }
            if (!ctx.launch(intent)) return ToolResult.error("No clock app can set timers.")
            return ToolResult(
                "Timer set for ${TimeParser.formatDuration(seconds)}" + (label?.let { " ($it)" } ?: "") + ".",
                JSONObject().put("ok", true).put("seconds", seconds),
            )
        }
    }

    object ShowAlarms : Tool(
        name = "show_alarms",
        description = "Open the list of alarms.",
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult =
            if (ctx.launch(Intent(AlarmClock.ACTION_SHOW_ALARMS))) ToolResult("Opening your alarms.")
            else ToolResult.error("No clock app found.")
    }
}
