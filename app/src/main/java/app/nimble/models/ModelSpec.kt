package app.nimble.models

/**
 * Weights Nimble can download. Revisions and SHA-256 sums are pinned to the same releases
 * that cactus-compute 4.2.6 pins (engine 4.1.4), so weights always match the linked engine.
 */
enum class ModelSpec(
    val title: String,
    val purpose: String,
    val repo: String,
    val revision: String,
    val fileName: String,
    val sha256: String,
) {
    NEEDLE(
        title = "Needle 3",
        purpose = "Understands requests and picks the action",
        repo = "Cactus-Compute/needle3",
        revision = "d6c7d6a2e9655505e0f8e23339356dd1705900eb",
        fileName = "needle3.cact",
        sha256 = "c9d915eca282ed42d1a09b143b592adb4cc6744ffe2d294adf5cfc5548170c38",
    ),
    WHISTLE(
        title = "Whistle",
        purpose = "Speech to text for the mic button",
        repo = "Cactus-Compute/whistle",
        revision = "88cb00eeb8928ddbcd410b491584f938ba030b07",
        fileName = "whistle.cact",
        sha256 = "b6e02f048568ac5d01a2042556c658061e699acbc0aa2a1439f52f3d461dffeb",
    );

    val url: String get() = "https://huggingface.co/$repo/resolve/$revision/$fileName"
}
