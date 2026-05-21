package se.birdy.pdf

expect class JournalPdfRenderer() {
    suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult
}

sealed interface JournalPdfRenderResult {
    data class Success(val pageCount: Int, val sizeBytes: Long) : JournalPdfRenderResult
    data object Empty : JournalPdfRenderResult
    data class Failed(val message: String, val cause: Throwable? = null) : JournalPdfRenderResult
}
