package se.birdy.app.ui.recap

import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import se.birdy.app.badges.BadgeCatalogLoader
import se.birdy.app.i18n.AppStrings
import se.birdy.app.i18n.LocaleResolver
import se.birdy.app.testing.FakeBadgeRepository
import se.birdy.app.testing.FakeObservationRepository
import se.birdy.app.ui.badges.BadgeStringMap
import se.birdy.app.ui.badges.resolveBadgeString
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.SpeciesSummary
import se.birdy.domain.badge.BadgeUnlock
import se.birdy.domain.observation.Observation

/**
 * The week in the approved "Uppslag 1" mockup (release 1.3.0 Task 7j), on a fixed clock: ISO week
 * 41 of 2026 (Monday 5 to Sunday 11 October) seen on Sunday evening in Stockholm. Seven finds on
 * five days, three new species (Domherre № 29, Stjärtmes № 30, Sidensvans № 31), four weeks in a
 * row, four finds the week before (+3) and the stamp "Månads-rytm" earned on Monday.
 *
 * Every find's photo is `/fake/<id>.jpg`; the screenshot test maps each to the species' plate
 * photo, standing in for the user's own photos.
 */
internal object RecapFixtures {
    val stockholm: TimeZone = TimeZone.of("Europe/Stockholm")

    /** Sunday 11 October 2026, 18:00 in Stockholm. */
    val sundayEvening: Instant = Instant.parse("2026-10-11T16:00:00Z")

    const val TALGOXE = "Q25485"
    const val RODHAKE = "Q25334"
    const val HACKSPETT = "Q26209"
    const val DOMHERRE = "Q25382"
    const val STJARTMES = "Q170831"
    const val SIDENSVANS = "Q26135"

    private val namesSv =
        mapOf(
            TALGOXE to "Talgoxe",
            RODHAKE to "Rödhake",
            HACKSPETT to "Större hackspett",
            DOMHERRE to "Domherre",
            STJARTMES to "Stjärtmes",
            SIDENSVANS to "Sidensvans",
        )

    private val namesEn =
        mapOf(
            TALGOXE to "Great Tit",
            RODHAKE to "European Robin",
            HACKSPETT to "Great Spotted Woodpecker",
            DOMHERRE to "Eurasian Bullfinch",
            STJARTMES to "Long-tailed Tit",
            SIDENSVANS to "Bohemian Waxwing",
        )

    /** The week's finds, oldest first, as (id, species, instant). */
    val weekFinds: List<Triple<String, String, String>> =
        listOf(
            Triple("mon-talgoxe", TALGOXE, "2026-10-05T06:30:00Z"),
            Triple("tue-domherre", DOMHERRE, "2026-10-06T07:10:00Z"),
            Triple("thu-stjartmes", STJARTMES, "2026-10-08T12:40:00Z"),
            Triple("sat-hackspett", HACKSPETT, "2026-10-10T07:00:00Z"),
            Triple("sat-sidensvans", SIDENSVANS, "2026-10-10T11:00:00Z"),
            Triple("sun-talgoxe", TALGOXE, "2026-10-11T07:00:00Z"),
            Triple("sun-rodhake", RODHAKE, "2026-10-11T10:00:00Z"),
        )

    /** Species name per QID, in Swedish or English; [rename] swaps names for the large-text tests. */
    fun species(
        english: Boolean = false,
        rename: Map<String, String> = emptyMap(),
    ): Map<SpeciesId, SpeciesSummary> =
        (if (english) namesEn else namesSv).mapValues { (qid, name) -> rename[qid] ?: name }.entries.associate { (qid, name) ->
            SpeciesId(qid) to
                SpeciesSummary(
                    id = SpeciesId(qid),
                    name = name,
                    scientificName = "",
                    abundance = Abundance.ALLMÄN,
                    heroImagePath = "$qid/hero.webp",
                )
        }

    /** The whole mockup week, with the 28 species seen before it and the streak behind it. */
    fun mockupWeekRepo(): FakeObservationRepository =
        FakeObservationRepository().apply {
            seed(earlierFinds() + weekFinds.map { (id, qid, at) -> observation(id, qid, at) })
        }

    /** The weeks before week 41, without anything in week 41 itself: a quiet week, streak at risk. */
    fun quietWeekRepo(): FakeObservationRepository = FakeObservationRepository().apply { seed(earlierFinds()) }

    /** A single find, the user's very first: one of everything. */
    fun firstFindRepo(): FakeObservationRepository =
        FakeObservationRepository().apply { seed(listOf(observation("first", TALGOXE, "2026-10-07T08:00:00Z"))) }

    /**
     * Life list № 1 to 28 before week 41: two species in July, then weeks 38 to 40 (a streak of
     * four with week 41), with exactly four finds in week 40 so the week reads "+3".
     */
    private fun earlierFinds(): List<Observation> =
        buildList {
            add(observation("jul-1", "Q900024", "2026-07-20T08:00:00Z"))
            add(observation("jul-2", "Q900025", "2026-07-20T09:00:00Z"))
            (1..10).forEach { add(observation("w38-$it", "Q9000${it.toString().padStart(2, '0')}", "2026-09-15T08:${10 + it}:00Z")) }
            add(observation("w38-talgoxe", TALGOXE, "2026-09-16T08:00:00Z"))
            (11..20).forEach { add(observation("w39-$it", "Q9000$it", "2026-09-22T08:$it:00Z")) }
            add(observation("w39-rodhake", RODHAKE, "2026-09-23T08:00:00Z"))
            add(observation("w40-1", "Q900021", "2026-10-01T08:00:00Z"))
            add(observation("w40-2", "Q900022", "2026-10-02T08:00:00Z"))
            add(observation("w40-hackspett", HACKSPETT, "2026-10-03T08:00:00Z"))
            add(observation("w40-3", "Q900023", "2026-10-03T09:00:00Z"))
        }

    fun observation(
        id: String,
        speciesId: String?,
        at: String,
    ) = Observation(
        id = id,
        speciesId = speciesId,
        capturedAt = Instant.parse(at),
        savedAt = Instant.parse(at),
        photoPath = "/fake/$id.jpg",
        note = "",
        confidence = 0.9f,
        latitude = null,
        longitude = null,
        locationLabel = null,
    )

    /** "Månads-rytm" (four weeks in a row), earned on Monday morning. */
    fun weekStampRepo(): FakeBadgeRepository =
        FakeBadgeRepository().apply {
            seedUnlocks(listOf(BadgeUnlock("weekly_streak_4", Instant.parse("2026-10-05T06:31:00Z"))))
        }

    /**
     * The app's own wiring: the real badge catalog (so the stamp has its real number) and the real
     * badge strings. Call after attachComposeResourcesContext().
     */
    fun viewModel(
        observations: FakeObservationRepository = mockupWeekRepo(),
        badges: FakeBadgeRepository = weekStampRepo(),
        species: Map<SpeciesId, SpeciesSummary> = species(),
        now: Instant = sundayEvening,
        zone: TimeZone = stockholm,
        // The test's language (its Robolectric qualifiers) stands in for the app's.
        strings: AppStrings =
            AppStrings(
                LocaleResolver.resolve(
                    override = null,
                    systemTag =
                        java.util.Locale
                            .getDefault()
                            .toLanguageTag(),
                ),
            ),
    ): RecapViewModel {
        val catalog = runBlocking { BadgeCatalogLoader.loadFromResources() }
        return RecapViewModel(
            obsRepo = observations,
            badgeRepo = badges,
            speciesByQid = { species },
            stampFor =
                recapStampResolver(
                    catalog = catalog,
                    nameFor = { id -> resolveBadgeString(id, strings) { BadgeStringMap.nameFor(id) } },
                    descriptionFor = { id -> strings.get(BadgeStringMap.descriptionFor(id)) },
                ),
            zone = zone,
            now = { now },
        )
    }

    /** The catalog number of a badge, as Märken shows it. */
    fun stampNumberOf(badgeId: String): Int =
        runBlocking { BadgeCatalogLoader.loadFromResources() }.badges.indexOfFirst { it.id == badgeId } + 1
}
