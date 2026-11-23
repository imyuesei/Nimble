package app.nimble.tools

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.provider.Settings
import app.nimble.harness.Domain
import app.nimble.harness.Param
import app.nimble.harness.Tool
import app.nimble.harness.ToolContext
import app.nimble.harness.ToolResult
import org.json.JSONObject
import kotlin.math.roundToInt

object DeviceTools {
    val domain = Domain(
        name = "phone_controls",
        routerDescription = "Flashlight, volume, battery level, and opening system settings like wifi or bluetooth.",
        keywords = Regex(
            "\\b(flashlight|torch|volume|louder|quieter|mute|battery|charge|wifi|wi-fi|bluetooth|settings|brightness)\\b",
            RegexOption.IGNORE_CASE,
        ),
        tools = listOf(SetFlashlight, SetVolume, CheckBattery, OpenSettings),
    )

    object SetFlashlight : Tool(
        name = "set_flashlight",
        description = "Turn the flashlight on or off.",
        params = listOf(Param("on", "boolean", "true to turn the flashlight on, false to turn it off.")),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val on = args.optBoolean("on", true)
            val cameras = ctx.context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val id = cameras.cameraIdList.firstOrNull {
                cameras.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: return ToolResult.error("This phone has no flashlight.")
            cameras.setTorchMode(id, on)
            return ToolResult(if (on) "Flashlight on." else "Flashlight off.")
        }
    }

    object SetVolume : Tool(
        name = "set_volume",
        description = "Set the media volume as a percentage.",
        params = listOf(Param("level", "integer", "Volume from 0 (mute) to 100 (max).", minimum = 0, maximum = 100)),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val level = args.optInt("level", 50).coerceIn(0, 100)
            val audio = ctx.context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, (level / 100f * max).roundToInt(), AudioManager.FLAG_SHOW_UI)
            return ToolResult("Volume set to $level%.")
        }
    }

    object CheckBattery : Tool(
        name = "check_battery",
        description = "Report the battery level and whether the phone is charging.",
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val battery = ctx.context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val charging = battery.isCharging
            return ToolResult(
                "Battery is at $level%" + if (charging) ", charging." else ".",
                JSONObject().put("level", level).put("charging", charging),
            )
        }
    }

    private val pages = linkedMapOf(
        "wifi" to Settings.ACTION_WIFI_SETTINGS,
        "bluetooth" to Settings.ACTION_BLUETOOTH_SETTINGS,
        "display" to Settings.ACTION_DISPLAY_SETTINGS,
        "sound" to Settings.ACTION_SOUND_SETTINGS,
        "battery" to Intent.ACTION_POWER_USAGE_SUMMARY,
        "location" to Settings.ACTION_LOCATION_SOURCE_SETTINGS,
        "apps" to Settings.ACTION_APPLICATION_SETTINGS,
        "general" to Settings.ACTION_SETTINGS,
    )

    object OpenSettings : Tool(
        name = "open_settings",
        description = "Open a page of the phone's system settings.",
        params = listOf(Param("page", "string", "Which settings page to open.", enum = pages.keys.toList())),
    ) {
        override suspend fun run(ctx: ToolContext, args: JSONObject): ToolResult {
            val page = args.optString("page", "general")
            val action = pages[page] ?: Settings.ACTION_SETTINGS
            if (!ctx.launch(Intent(action)) && !ctx.launch(Intent(Settings.ACTION_SETTINGS))) {
                return ToolResult.error("Settings can't be opened.")
            }
            return ToolResult("Opening $page settings.")
        }
    }
}
