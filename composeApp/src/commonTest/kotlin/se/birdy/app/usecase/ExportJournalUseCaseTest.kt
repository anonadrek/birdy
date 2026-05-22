package se.birdy.app.usecase

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.app.testing.FakeUserPreferences
import se.birdy.content.Locale
import se.birdy.domain.badge.Badge
import se.birdy.domain.badge.BadgeCatalog
import se.birdy.domain.badge.BadgeCategory
import se.birdy.domain.badge.BadgeRule
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.pdf.JournalPdfInput
import se.birdy.pdf.JournalPdfRenderResult
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExportJournalUseCaseTest {
    @Test
    fun export_with_no_observations_returns_NothingToExport() =
        runTest {
            val uc = newUseCase()
            val result = uc.run()
            assertTrue(result is JournalExportResult.NothingToExport, "got $result")
        }

    @Test
    fun export_with_observations_returns_Success_with_path_and_renderer_metrics() =
        runTest {
            val obsRepo = FakeObservationRepository.withDefaults()
            val speciesRepo = FakeSpeciesRepository.withDefaults()
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = obsRepo,
                    speciesRepo = speciesRepo,
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(pageCount = 4, sizeBytes = 12_345L)
                    },
                    outputPathFactory = { ms -> "/tmp/journal_$ms.pdf" },
                    clock = fixedClock("2026-05-22T08:00:00Z"),
                )

            val result = uc.run()

            assertTrue(result is JournalExportResult.Success, "got $result")
            assertEquals(4, result.pageCount)
            assertEquals(12_345L, result.sizeBytes)
            assertTrue(result.pdfPath.endsWith(".pdf"))
            assertEquals(1, captured.size)
            assertEquals(5, captured.single().observations.size)
        }

    @Test
    fun renderer_failed_propagates_as_Failed() =
        runTest {
            val uc =
                newUseCase(
                    obsRepo = FakeObservationRepository.withDefaults(),
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    render = { _, _ -> JournalPdfRenderResult.Failed("boom") },
                )
            val result = uc.run()
            assertTrue(result is JournalExportResult.Failed, "got $result")
            assertEquals("boom", result.message)
        }

    @Test
    fun renderer_empty_propagates_as_NothingToExport() =
        runTest {
            val uc =
                newUseCase(
                    obsRepo = FakeObservationRepository.withDefaults(),
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    render = { _, _ -> JournalPdfRenderResult.Empty },
                )
            val result = uc.run()
            assertTrue(result is JournalExportResult.NothingToExport, "got $result")
        }

    @Test
    fun stats_count_species_and_observations_only_for_current_year() =
        runTest {
            val obsRepo = FakeObservationRepository()
            val zone = TimeZone.UTC
            obsRepo.seedObservation("Q1", capturedAt = Instant.parse("2026-03-10T10:00:00Z"))
            obsRepo.seedObservation("Q1", capturedAt = Instant.parse("2026-04-12T10:00:00Z"))
            obsRepo.seedObservation("Q2", capturedAt = Instant.parse("2025-12-01T10:00:00Z"))
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = obsRepo,
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(1, 0L)
                    },
                    clock = fixedClock("2026-06-01T10:00:00Z"),
                    timeZone = zone,
                )

            uc.run()

            val stats = captured.single().stats
            assertEquals(1, stats.speciesSeenThisYear) // only Q1 in 2026
            assertEquals(2, stats.totalObservationsThisYear) // 2 obs in 2026, 1 in 2025
        }

    @Test
    fun top_species_localizes_via_speciesByQid_and_caps_at_five() =
        runTest {
            val obsRepo = FakeObservationRepository.withDefaults() // 5 obs across 5 species
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = obsRepo,
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(1, 0L)
                    },
                )

            uc.run()

            val top = captured.single().stats.topSpecies
            assertTrue(top.size <= 5)
            assertTrue(top.all { (name, _) -> name.first().isUpperCase() }, "got $top")
        }

    @Test
    fun premium_badges_filtered_by_id_prefix_and_resolved_via_resolvers() =
        runTest {
            val badgeRepo = FakeBadgeRepository()
            val unlockedAt = Instant.parse("2026-05-20T12:00:00Z")
            badgeRepo.seedUnlocks(
                listOf(
                    BadgeUnlock("premium_field_member", unlockedAt),
                    BadgeUnlock("novice", unlockedAt), // non-premium → filtered out
                ),
            )
            val obsRepo = FakeObservationRepository.withDefaults()
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = obsRepo,
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    badgeRepo = badgeRepo,
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(1, 0L)
                    },
                    badgeNameResolver = { id -> "name_$id" },
                    badgeDescriptionResolver = { id -> "desc_$id" },
                )

            uc.run()

            val premium = captured.single().unlockedPremiumBadges
            assertEquals(1, premium.size)
            assertEquals("premium_field_member", premium.single().id)
            assertEquals("name_premium_field_member", premium.single().nameLocalized)
            assertEquals("desc_premium_field_member", premium.single().descriptionLocalized)
            assertEquals(unlockedAt, premium.single().unlockedAt)
        }

    @Test
    fun blank_user_name_falls_back_to_default() =
        runTest {
            val prefs = FakeUserPreferences().apply { userNameValue = "" }
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = FakeObservationRepository.withDefaults(),
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    prefs = prefs,
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(1, 0L)
                    },
                )

            uc.run()

            assertEquals("Birdy", captured.single().displayName)
        }

    @Test
    fun non_blank_user_name_is_used_as_display_name() =
        runTest {
            val prefs = FakeUserPreferences().apply { userNameValue = "Albin" }
            val captured = mutableListOf<JournalPdfInput>()
            val uc =
                newUseCase(
                    obsRepo = FakeObservationRepository.withDefaults(),
                    speciesRepo = FakeSpeciesRepository.withDefaults(),
                    prefs = prefs,
                    render = { input, _ ->
                        captured += input
                        JournalPdfRenderResult.Success(1, 0L)
                    },
                )

            uc.run()

            assertEquals("Albin", captured.single().displayName)
        }

    // -- helpers --

    private fun newUseCase(
        obsRepo: FakeObservationRepository = FakeObservationRepository(),
        speciesRepo: FakeSpeciesRepository = FakeSpeciesRepository(),
        badgeRepo: FakeBadgeRepository = FakeBadgeRepository(),
        prefs: FakeUserPreferences = FakeUserPreferences().apply { userNameValue = "Albin" },
        catalog: BadgeCatalog = emptyCatalog(),
        render: suspend (JournalPdfInput, String) -> JournalPdfRenderResult = { _, _ ->
            JournalPdfRenderResult.Empty
        },
        outputPathFactory: (Long) -> String = { ms -> "/tmp/x_$ms.pdf" },
        clock: Clock = fixedClock("2026-05-22T08:00:00Z"),
        timeZone: TimeZone = TimeZone.UTC,
        locale: Locale = Locale.SV,
        badgeNameResolver: suspend (String) -> String = { it },
        badgeDescriptionResolver: suspend (String) -> String = { "desc_$it" },
    ): ExportJournalUseCase =
        ExportJournalUseCase(
            observationRepo = obsRepo,
            speciesRepo = speciesRepo,
            badgeRepo = badgeRepo,
            catalog = catalog,
            render = render,
            userPreferences = prefs,
            outputPathFactory = outputPathFactory,
            clock = clock,
            timeZone = timeZone,
            locale = locale,
            badgeNameResolver = badgeNameResolver,
            badgeDescriptionResolver = badgeDescriptionResolver,
        )

    private fun emptyCatalog(): BadgeCatalog =
        BadgeCatalog(
            version = 1,
            badges =
                listOf(
                    Badge(
                        id = "premium_field_member",
                        category = BadgeCategory.PROGRESSION,
                        rule = BadgeRule.CountUniqueSpecies(target = 1),
                    ),
                ),
        )

    private fun fixedClock(iso: String): Clock {
        val instant = Instant.parse(iso)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }
}
