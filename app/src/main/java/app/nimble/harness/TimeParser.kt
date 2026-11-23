package app.nimble.harness

/**
 * Needle copies time phrases word for word (as in the Cactus productivity environment);
 * the host resolves them. This handles the common spoken and typed forms.
 */
object TimeParser {
    private val units = mapOf(
        "zero" to 0, "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6,
        "seven" to 7, "eight" to 8, "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
        "thirteen" to 13, "fourteen" to 14, "fifteen" to 15, "sixteen" to 16, "seventeen" to 17,
        "eighteen" to 18, "nineteen" to 19,
    )
    private val tens = mapOf("twenty" to 20, "thirty" to 30, "forty" to 40, "fifty" to 50, "sixty" to 60)

    /** Lowercases and turns number words into digits ("twenty five" -> "25", "an hour" -> "1 hour"). */
    fun normalize(input: String): String {
        val text = input.lowercase()
            .replace("half an hour", "30 minutes")
            .replace("half hour", "30 minutes")
            .replace("quarter of an hour", "15 minutes")
            .replace("quarter hour", "15 minutes")
            .replace('-', ' ')
        val tokens = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
        val out = ArrayList<String>()
        var i = 0
        while (i < tokens.size) {
            val t = tokens[i]
            val ten = tens[t]
            when {
                ten != null -> {
                    val next = tokens.getOrNull(i + 1)?.let { units[it] }
                    if (next != null && next in 1..9) { out += (ten + next).toString(); i++ } else out += ten.toString()
                }
                units[t] != null -> out += units.getValue(t).toString()
                (t == "a" || t == "an") && tokens.getOrNull(i + 1)?.let { unitOf(it) != null } == true -> out += "1"
                else -> out += t
            }
            i++
        }
        return out.joinToString(" ")
    }

    private fun unitOf(word: String): Int? = when (word.trimEnd('.', ',')) {
        "h", "hr", "hrs", "hour", "hours" -> 3600
        "m", "min", "mins", "minute", "minutes" -> 60
        "s", "sec", "secs", "second", "seconds" -> 1
        else -> null
    }

    /** "10 minutes", "1 hour 30 min", "an hour and a half", "90 sec", "5" (minutes) -> seconds. */
    fun durationSeconds(input: String): Int? {
        val text = normalize(input)
        var total = 0.0
        var matched = false
        Regex("(\\d+(?:\\.\\d+)?)\\s*([a-z]+)").findAll(text).forEach { m ->
            val unit = unitOf(m.groupValues[2]) ?: return@forEach
            total += m.groupValues[1].toDouble() * unit
            matched = true
        }
        if (matched && Regex("\\band (a )?half\\b").containsMatchIn(text)) {
            // "an hour and a half" -> add half of the largest unit mentioned
            val largest = Regex("[a-z]+").findAll(text).mapNotNull { unitOf(it.value) }.maxOrNull() ?: 0
            total += largest / 2.0
        }
        if (!matched) {
            val bare = Regex("^\\s*(\\d+(?:\\.\\d+)?)\\s*$").find(text) ?: return null
            total = bare.groupValues[1].toDouble() * 60
        }
        val seconds = total.toInt()
        return if (seconds > 0) seconds else null
    }

    /** "7", "7am", "7:30 pm", "19:00", "seven thirty", "half past 6", "noon" -> (hour, minute). */
    fun clock(input: String): Pair<Int, Int>? {
        val text = normalize(input)
            .replace(Regex("\\b([ap])\\.\\s?m\\.?"), "$1m") // a.m. / p.m.
            .replace(".", ":") // 7.30 -> 7:30
            .replace(Regex("(\\d)([a-z])"), "$1 $2") // 7am -> 7 am
        if (Regex("\\b(noon|midday)\\b").containsMatchIn(text)) return 12 to 0
        if (Regex("\\bmidnight\\b").containsMatchIn(text)) return 0 to 0

        var hour: Int
        var minute: Int
        val past = Regex("\\b(half|quarter|\\d{1,2}) past (\\d{1,2})\\b").find(text)
        val to = Regex("\\b(quarter|\\d{1,2}) to (\\d{1,2})\\b").find(text)
        when {
            past != null -> {
                hour = past.groupValues[2].toInt()
                minute = when (val m = past.groupValues[1]) { "half" -> 30; "quarter" -> 15; else -> m.toInt() }
            }
            to != null -> {
                val before = when (val m = to.groupValues[1]) { "quarter" -> 15; else -> m.toInt() }
                hour = (to.groupValues[2].toInt() + 23) % 24
                minute = 60 - before
            }
            else -> {
                val hm = Regex("\\b(\\d{1,2})(?:\\s*[:h]\\s*|\\s+)(\\d{2})\\b").find(text)
                val compact = Regex("\\b(\\d{1,2})(\\d{2})\\s*(?:am|pm|hours)?\\b").find(text)
                val single = Regex("\\b(\\d{1,2})\\b").find(text)
                when {
                    hm != null -> { hour = hm.groupValues[1].toInt(); minute = hm.groupValues[2].toInt() }
                    compact != null -> { hour = compact.groupValues[1].toInt(); minute = compact.groupValues[2].toInt() }
                    single != null -> { hour = single.groupValues[1].toInt(); minute = 0 }
                    else -> return null
                }
            }
        }
        val pm = Regex("\\bpm\\b|\\b(evening|afternoon|tonight|night)\\b").containsMatchIn(text) || text.endsWith("pm")
        val am = Regex("\\bam\\b|\\bmorning\\b").containsMatchIn(text) || text.endsWith("am")
        if (pm && hour in 1..11) hour += 12
        if (am && hour == 12) hour = 0
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour to minute
    }

    fun formatDuration(seconds: Int): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return buildList {
            if (h > 0) add("$h h")
            if (m > 0) add("$m min")
            if (s > 0) add("$s s")
        }.joinToString(" ").ifEmpty { "0 s" }
    }

    fun formatClock(hour: Int, minute: Int) = "%02d:%02d".format(hour, minute)
}
