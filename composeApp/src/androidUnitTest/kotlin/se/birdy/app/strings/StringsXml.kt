package se.birdy.app.strings

import java.io.File

/** Minimal reader for compose-resources strings.xml files, for JVM guard tests. */
internal object StringsXml {
    private val stringRegex =
        Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
    private val arrayRegex =
        Regex("""<string-array name="([^"]+)"[^>]*>(.*?)</string-array>""", RegexOption.DOT_MATCHES_ALL)
    private val itemRegex = Regex("""<item>(.*?)</item>""", RegexOption.DOT_MATCHES_ALL)
    val placeholderRegex = Regex("""%\d+\$[sd]""")

    fun strings(file: File): Map<String, String> = stringRegex.findAll(file.readText()).associate { it.groupValues[1] to it.groupValues[2] }

    fun arrays(file: File): Map<String, List<String>> =
        arrayRegex.findAll(file.readText()).associate { m ->
            m.groupValues[1] to itemRegex.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
        }

    /** `values/` is Swedish (default), `values-en/` is English. */
    fun swedish(): File = File(resourcesDir(), "values/strings.xml")

    fun english(): File = File(resourcesDir(), "values-en/strings.xml")

    private fun resourcesDir(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "src/commonMain/composeResources")
            if (File(candidate, "values/strings.xml").isFile) return candidate
            val nested = File(dir, "composeApp/src/commonMain/composeResources")
            if (File(nested, "values/strings.xml").isFile) return nested
            dir = dir.parentFile
        }
        error("composeResources not found from ${System.getProperty("user.dir")}")
    }
}
