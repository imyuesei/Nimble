package app.nimble.tools

import app.nimble.harness.Domain
import app.nimble.harness.Param
import app.nimble.harness.Risk
import app.nimble.harness.Tool
import app.nimble.harness.ToolContext
import app.nimble.harness.ToolResult
import org.json.JSONObject

object NoteTools {
    val domain = Domain(
        name = "notes",
        routerDescription = "Saving a note, reading notes back, or clearing notes.",
        keywords = Regex("\\b(note|notes|jot|write down|remember)\\b", RegexOption.IGNORE_CASE),
        tools = listOf(CreateNote, ListNotes, ClearNotes),
    )

    object CreateNote : Tool(
        name = "create_note",
        description = "Save a free-form note. Notes store information; they never ring.",
        params = listOf(Param("text", "string", "The note content copied word for word.", maxLength = 300)),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val text = args.optString("text").trim()
            if (text.isEmpty()) return ToolResult.error("The note was empty.")
            ctx.notes.add(text)
            return ToolResult("Noted: $text")
        }
    }

    object ListNotes : Tool(
        name = "list_notes",
        description = "Read back the saved notes.",
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val notes = ctx.notes.all()
            if (notes.isEmpty()) return ToolResult("You have no notes yet.", JSONObject().put("count", 0))
            val recent = notes.takeLast(10).reversed()
            val text = recent.joinToString("\n") { "• ${it.text}" }
            return ToolResult(
                "Your notes:\n$text" + if (notes.size > recent.size) "\n…and ${notes.size - recent.size} more." else "",
                JSONObject().put("count", notes.size),
            )
        }
    }

    object ClearNotes : Tool(
        name = "clear_notes",
        description = "Delete every saved note.",
        risk = Risk.CONFIRM,
    ) {
        override fun preview(args: JSONObject) = "delete all notes"

        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            ctx.notes.clear()
            return ToolResult("All notes deleted.")
        }
    }
}
