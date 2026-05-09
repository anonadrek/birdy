package se.birdy.app.ui.components

sealed interface HeadlineSegment {
    val text: String

    data class Plain(
        override val text: String,
    ) : HeadlineSegment

    data class Accent(
        override val text: String,
    ) : HeadlineSegment
}

/**
 * Parses headline syntax: `*word*` becomes an Accent segment, the rest Plain.
 *
 * Rules:
 * - `\*` is a literal asterisk in a Plain segment.
 * - Inside `*…*` the next `*` is the closer (no escape supported).
 * - An unmatched lone `*` is kept as a literal.
 * - An empty pair `**` is dropped.
 */
fun parseJournalHeadline(input: String): List<HeadlineSegment> {
    val out = mutableListOf<HeadlineSegment>()
    val plainBuf = StringBuilder()
    var i = 0
    while (i < input.length) {
        val c = input[i]
        when {
            c == '\\' && i + 1 < input.length && input[i + 1] == '*' -> {
                plainBuf.append('*')
                i += 2
            }
            c == '*' -> {
                val end = input.indexOf('*', startIndex = i + 1)
                when {
                    end < 0 -> {
                        plainBuf.append('*')
                        i += 1
                    }
                    end == i + 1 -> {
                        i += 2
                    }
                    else -> {
                        if (plainBuf.isNotEmpty()) {
                            out.add(HeadlineSegment.Plain(plainBuf.toString()))
                            plainBuf.clear()
                        }
                        out.add(HeadlineSegment.Accent(input.substring(i + 1, end)))
                        i = end + 1
                    }
                }
            }
            else -> {
                plainBuf.append(c)
                i += 1
            }
        }
    }
    if (plainBuf.isNotEmpty()) {
        out.add(HeadlineSegment.Plain(plainBuf.toString()))
    }
    return out
}
