package app.nimble.tools

import app.nimble.harness.Domain

/** Every tool pack Nimble ships. Add a new domain here (5 tools or fewer each). */
object Domains {
    val all: List<Domain> = listOf(
        ClockTools.domain,
        PhoneTools.domain,
        NoteTools.domain,
        DeviceTools.domain,
    )
}
