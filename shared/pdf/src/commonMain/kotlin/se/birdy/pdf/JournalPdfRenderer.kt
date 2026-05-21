package se.birdy.pdf

/**
 * Renders a Field Journal-style PDF from observations and species metadata.
 *
 * The Android actual uses `android.graphics.pdf.PdfDocument` to produce a paginated export.
 * JVM and iOS actuals return [JournalPdfRenderResult.Failed] — PDF export is Android-only in v1.
 */
expect class JournalPdfRenderer() {
    /**
     * Renders the journal to a PDF file at [outputPath].
     *
     * @param outputPath Absolute filesystem path to write the PDF; the Android actual creates or
     *   overwrites the file via `FileOutputStream`. `content://` URIs are NOT supported.
     *   The parent directory must exist; callers should pass an app-private path obtained from
     *   `Context.filesDir` or `Context.cacheDir`.
     * @return [JournalPdfRenderResult.Success] on success, [JournalPdfRenderResult.Empty] when
     *   [JournalPdfInput.observations] is empty (no file written), or
     *   [JournalPdfRenderResult.Failed] on render errors.
     */
    suspend fun render(input: JournalPdfInput, outputPath: String): JournalPdfRenderResult
}

sealed interface JournalPdfRenderResult {
    data class Success(val pageCount: Int, val sizeBytes: Long) : JournalPdfRenderResult

    /** No observations to render — no file is written. */
    data object Empty : JournalPdfRenderResult

    data class Failed(val message: String, val cause: Throwable? = null) : JournalPdfRenderResult
}
