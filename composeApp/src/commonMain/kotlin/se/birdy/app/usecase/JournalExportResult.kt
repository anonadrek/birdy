package se.birdy.app.usecase

sealed interface JournalExportResult {
    data class Success(
        val pdfPath: String,
        val pageCount: Int,
        val sizeBytes: Long,
    ) : JournalExportResult

    data object NothingToExport : JournalExportResult

    data class Failed(
        val message: String,
    ) : JournalExportResult
}
