package se.birdy.pdf

actual class JournalPdfRenderer actual constructor() {
    actual suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult {
        if (input.observations.isEmpty()) return JournalPdfRenderResult.Empty
        return JournalPdfRenderResult.Failed("PDF rendering is Android-only in v1")
    }
}
