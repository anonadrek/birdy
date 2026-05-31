package se.birdy.app.ui.scaffold

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Listen : AppRoute

    @Serializable data object Scan : AppRoute

    @Serializable data object PhotoAnalyze : AppRoute

    /**
     * Plan 6b2 T6: sourceJson is a JSON-encoded [ScanSourceSerialization];
     * replaces the old predictionsCsv + frameJpegPath params.
     */
    @Serializable data class MatchResult(
        val sourceJson: String,
        val capturedAtMs: Long,
    ) : AppRoute

    @Serializable data object Archive : AppRoute

    @Serializable data object ArchiveList : AppRoute

    @Serializable data class SpeciesProfile(
        val speciesId: String,
    ) : AppRoute

    @Serializable data object Lifelist : AppRoute

    @Serializable data class ObservationDetail(
        val id: String,
    ) : AppRoute

    @Serializable data object Badges : AppRoute

    @Serializable data object Settings : AppRoute

    @Serializable data object About : AppRoute

    @Serializable data object OnboardingReplay : AppRoute

    @Serializable data object Premium : AppRoute

    @Serializable data object DebugBenchmark : AppRoute

    @Serializable data object DebugDiagnostics : AppRoute

    /**
     * Audio-ID scan screen — Plan 6b2 T5.
     * Navigation to this route wired in AppScaffold; full Match-flow connection
     * deferred to T6 which refactors AppRoute.MatchResult to accept ScanSource JSON.
     */
    @Serializable data object AudioScan : AppRoute

    /**
     * Plan 6b3 T12: Season Statistics page (premium-gated). Entered from the
     * Lifelist `LiveStatsPreview` block when premium is active.
     */
    @Serializable data object SeasonStats : AppRoute

    /**
     * v1.2 Phase B: Weekly Recap screen. Reachable from Lifelist (Task 12),
     * Settings, and via the `birdy://recap` deep-link emitted by WeeklyRecapWorker.
     */
    @Serializable data object WeeklyRecap : AppRoute
}
