package app.nimble.tools

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import app.nimble.harness.Domain
import app.nimble.harness.Param
import app.nimble.harness.Tool
import app.nimble.harness.ToolContext
import app.nimble.harness.ToolResult
import org.json.JSONObject

object PhoneTools {
    val domain = Domain(
        name = "calls_messages",
        routerDescription = "Calling someone, texting someone, or opening an app.",
        keywords = Regex("\\b(call|ring|dial|phone|text|sms|message|tell|open|launch)\\b", RegexOption.IGNORE_CASE),
        tools = listOf(CallContact, SendText, OpenApp),
    )

    private data class Contact(val name: String, val number: String)

    /** Looks a contact up by name; a name made of digits is treated as a number. */
    private suspend fun resolve(ctx: ToolContext, name: String): Contact? {
        val digits = name.filter { it.isDigit() || it == '+' }
        if (digits.length >= 3 && digits.length >= name.count { !it.isWhitespace() } - 2) return Contact(name, digits)
        if (!ctx.requestPermission(Manifest.permission.READ_CONTACTS)) return null
        val resolver = ctx.context.contentResolver
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        resolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?", arrayOf("%$name%"), null,
        )?.use { c ->
            var best: Contact? = null
            while (c.moveToNext()) {
                val contact = Contact(c.getString(0) ?: continue, c.getString(1) ?: continue)
                if (contact.name.equals(name, ignoreCase = true)) return contact
                if (best == null || contact.name.length < best.name.length) best = contact
            }
            return best
        }
        return null
    }

    object CallContact : Tool(
        name = "call_contact",
        description = "Phone a contact or a number. Opens the dialer ready to call.",
        params = listOf(Param("name", "string", "The contact name or phone number copied word for word.", maxLength = 60)),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val name = args.optString("name")
            val contact = resolve(ctx, name)
                ?: return ToolResult.error("I couldn't find \"$name\" in your contacts.")
            if (!ctx.launch(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(contact.number))))) {
                return ToolResult.error("No phone app found.")
            }
            return ToolResult("Calling ${contact.name}: tap call to connect.")
        }
    }

    object SendText : Tool(
        name = "send_text",
        description = "Write a text message to a contact. Opens the messaging app with the message filled in.",
        params = listOf(
            Param("name", "string", "The contact name or phone number copied word for word.", maxLength = 60),
            Param("message", "string", "The message copied word for word.", maxLength = 300),
        ),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val name = args.optString("name")
            val message = args.optString("message")
            val contact = resolve(ctx, name)
                ?: return ToolResult.error("I couldn't find \"$name\" in your contacts.")
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(contact.number)))
                .putExtra("sms_body", message)
            if (!ctx.launch(intent)) return ToolResult.error("No messaging app found.")
            return ToolResult("Message to ${contact.name} is ready: tap send.")
        }
    }

    object OpenApp : Tool(
        name = "open_app",
        description = "Open an installed app by name.",
        params = listOf(Param("app_name", "string", "The app name copied word for word.", maxLength = 40)),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val wanted = args.optString("app_name").trim()
            val pm = ctx.context.packageManager
            val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val apps = pm.queryIntentActivities(launcher, PackageManager.MATCH_ALL)
                .map { it.loadLabel(pm).toString() to it.activityInfo.packageName }
            val match = apps.firstOrNull { it.first.equals(wanted, ignoreCase = true) }
                ?: apps.filter { it.first.contains(wanted, ignoreCase = true) }.minByOrNull { it.first.length }
                ?: return ToolResult.error("I couldn't find an app called \"$wanted\".")
            val intent = pm.getLaunchIntentForPackage(match.second)
                ?: return ToolResult.error("${match.first} can't be opened.")
            if (!ctx.launch(intent)) return ToolResult.error("${match.first} can't be opened.")
            return ToolResult("Opening ${match.first}.")
        }
    }
}
