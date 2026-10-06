package se.birdy.app.ui.stats

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.testing.FakeSpeciesRepository
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesImage

/**
 * Shared fixture for the Season Statistics Robolectric tests (the gated [SeasonStatsScreenTest] and
 * the opt-in StatsScreenshotTest), release 1.3.0 Task 7c. It reproduces the approved mockup's year:
 * 15 finds of five species on a clock of 6 October 2026, months
 * J1 F1 M2 A1 M4 J2 J1 A1 S0 O2 N0 D0 (May is the single best month, October is in progress),
 * seasons winter 2 / spring 7 / summer 4 / autumn 2, first finds Talgoxe 8 Jan, Blåmes 21 Feb,
 * Koltrast 14 Mar, Tornfalk 3 May, Knölsvan 11 May, and the top three Talgoxe 6, Blåmes 4,
 * Koltrast 2 (Koltrast beats Knölsvan's 2 on species id).
 */
internal object SeasonStatsFixtures {
    const val NOW = "2026-10-06T08:00:00Z"

    private val mockupYear =
        listOf(
            "Q25485" to "2026-01-08", // Talgoxe
            "Q25404" to "2026-02-21", // Blåmes
            "Q25234" to "2026-03-14", // Koltrast
            "Q25485" to "2026-03-20",
            "Q25404" to "2026-04-09",
            "Q26490" to "2026-05-03", // Tornfalk
            "Q25402" to "2026-05-11", // Knölsvan
            "Q25485" to "2026-05-15",
            "Q25404" to "2026-05-24",
            "Q25485" to "2026-06-06",
            "Q25234" to "2026-06-19",
            "Q25402" to "2026-07-02",
            "Q25485" to "2026-08-17",
            "Q25485" to "2026-10-02",
            "Q25404" to "2026-10-04",
        )

    fun mockupYearRepo(): FakeObservationRepository =
        FakeObservationRepository().apply {
            mockupYear.forEach { (qid, day) -> seedObservation(qid, Instant.parse("${day}T09:00:00Z")) }
        }

    /** One find only: the "few finds" case must still read as intended. */
    fun singleFindRepo(): FakeObservationRepository =
        FakeObservationRepository().apply { seedObservation("Q25485", Instant.parse("2026-03-14T09:00:00Z")) }

    /** Two finds in two months: a tie, so no month is named. */
    fun twoFindsRepo(): FakeObservationRepository =
        FakeObservationRepository().apply {
            seedObservation("Q25485", Instant.parse("2026-03-14T09:00:00Z"))
            seedObservation("Q25404", Instant.parse("2026-09-02T09:00:00Z"))
        }

    /** The five default species, each with its real plate photo path ("Q…/hero.webp"). */
    fun speciesWithPhotos(): FakeSpeciesRepository =
        FakeSpeciesRepository.withDefaults().apply {
            byId.value = byId.value.mapValues { (id, species) -> species?.copy(images = listOf(hero(id))) }
        }

    /** As [speciesWithPhotos], but Talgoxe renamed to a long one-word name to exercise the large-text fallbacks. */
    fun speciesWithLongName(): FakeSpeciesRepository =
        speciesWithPhotos().apply {
            byId.value =
                byId.value.mapValues { (id, species) ->
                    if (id.raw == "Q25485") species?.copy(name = "Svarthakedopping") else species
                }
        }

    fun viewModel(
        locale: Locale,
        observationRepo: FakeObservationRepository = mockupYearRepo(),
        speciesRepo: FakeSpeciesRepository = speciesWithPhotos(),
    ) = SeasonStatsViewModel(
        observationRepo = observationRepo,
        speciesRepo = speciesRepo,
        clock = fixedClock(NOW),
        zone = TimeZone.UTC,
        locale = locale,
    )

    private fun hero(id: SpeciesId) =
        SpeciesImage(
            role = "hero",
            path = "${id.raw}/hero.webp",
            width = 800,
            height = 600,
            license = "",
            author = "",
            sourceUrl = "",
        )

    private fun fixedClock(iso: String): Clock {
        val instant = Instant.parse(iso)
        return object : Clock {
            override fun now(): Instant = instant
        }
    }
}
