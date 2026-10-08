package se.birdy.domain.dailybird

import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import se.birdy.content.Abundance
import se.birdy.content.SpeciesId
import se.birdy.content.model.Species
import se.birdy.content.model.SpeciesTaxonomy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val MONTH_KEYS =
    listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

private fun monthKey(monthNumber: Int): String = MONTH_KEYS[monthNumber - 1]

class DailyBirdSelectorTest {
    private fun species(
        id: String,
        abundance: Abundance = Abundance.ALLMÄN,
        regions: List<String> = listOf("SE"),
        seasonByMonth: Map<String, String> = (1..12).associate { monthKey(it) to "present" },
        iucnStatus: String = "LC",
    ): Pair<SpeciesId, Species> =
        SpeciesId(id) to
            Species(
                id = SpeciesId(id),
                scientificName = "Test $id",
                taxonomy = SpeciesTaxonomy("Testidae", "Testfåglar", "Testus", "Test"),
                name = id,
                abundance = abundance,
                iucnStatus = iucnStatus,
                regions = regions,
                season = seasonByMonth,
                description = null,
                migration = null,
                images = emptyList(),
            )

    @Test
    fun `selectFor is deterministic per date`() =
        runTest {
            val pool = mapOf(species("Q1"), species("Q2"), species("Q3"))
            val selector = DailyBirdSelector { pool }
            val date = LocalDate(2026, 5, 25)
            val results = (1..100).map { selector.selectFor(date) }
            assertEquals(1, results.toSet().size, "Same date must produce same bird across 100 calls")
        }

    @Test
    fun `selectFor produces variance across dates`() =
        runTest {
            val pool = (1..50).associate { species("Q$it").first to species("Q$it").second }
            val selector = DailyBirdSelector { pool }
            val results = (0L until 30L).map { selector.selectFor(LocalDate(2026, 5, 1).plusDays(it)) }
            val distinct = results.mapNotNull { it?.speciesId }.toSet()
            assertTrue(distinct.size >= 20, "Expected at least 20 distinct birds across 30 dates, got ${distinct.size}")
        }

    @Test
    fun `selectFor filters out species outside Nordic regions`() =
        runTest {
            val pool =
                mapOf(
                    species("Q_UK", regions = listOf("UK")),
                    species("Q_SE", regions = listOf("SE")),
                )
            val selector = DailyBirdSelector { pool }
            val results = (0L until 30L).map { selector.selectFor(LocalDate(2026, 1, 1).plusDays(it))?.speciesId }
            assertTrue(results.all { it == "Q_SE" }, "Expected only Nordic species, got ${results.toSet()}")
        }

    @Test
    fun `selectFor filters out species absent in current month`() =
        runTest {
            val mayMap = (1..12).associate { monthKey(it) to if (it == 5) "absent" else "present" }
            val alwaysMap = (1..12).associate { monthKey(it) to "present" }
            val pool =
                mapOf(
                    species("Q_ABSENT_MAY", seasonByMonth = mayMap),
                    species("Q_ALWAYS", seasonByMonth = alwaysMap),
                )
            val selector = DailyBirdSelector { pool }
            val mayResult = selector.selectFor(LocalDate(2026, 5, 15))
            assertEquals("Q_ALWAYS", mayResult?.speciesId)
        }

    @Test
    fun `selectFor weights common bucket around 75 percent`() =
        runTest {
            val common = (1..10).map { species("QC$it", abundance = Abundance.ALLMÄN) }
            val rare = (1..10).map { species("QR$it", abundance = Abundance.SÄLLSYNT) }
            val pool = (common + rare).toMap()
            val selector = DailyBirdSelector { pool }
            val results =
                (0L until 1000L).map {
                    selector.selectFor(LocalDate(2026, 1, 1).plusDays(it))?.speciesId
                }
            val commonCount = results.count { it?.startsWith("QC") == true }
            assertTrue(commonCount in 700..800, "Expected 70-80% common, got $commonCount/1000")
        }

    @Test
    fun `selectFor returns null when no candidates`() =
        runTest {
            val noNordic = mapOf(species("Q_UK", regions = listOf("UK")))
            val selector = DailyBirdSelector { noNordic }
            assertNull(selector.selectFor(LocalDate(2026, 5, 25)))
        }

    @Test
    fun `selectFor maps season tags correctly`() =
        runTest {
            val breedingMap = (1..12).associate { monthKey(it) to if (it == 5) "breeding" else "absent" }
            val migratingMap = (1..12).associate { monthKey(it) to if (it == 5) "migrating" else "absent" }
            val pool =
                mapOf(
                    species("Q_BREED", seasonByMonth = breedingMap),
                    species("Q_MIG", seasonByMonth = migratingMap),
                )
            val selector = DailyBirdSelector { pool }
            val result = selector.selectFor(LocalDate(2026, 5, 15))
            assertNotNull(result)
            assertTrue(result.seasonTag == SeasonTag.BREEDING || result.seasonTag == SeasonTag.MIGRATING)
        }

    // Release 1.3.0: Garfågel, Kanariestrandskata and Smalnäbbad spov are extinct (IUCN EX). The
    // daily bird is one to go out and find, and catching it counts towards a badge.
    @Test
    fun `selectFor never picks an extinct species`() =
        runTest {
            val pool =
                mapOf(
                    species("Q_LIVE_COMMON"),
                    species("Q_LIVE_RARE", abundance = Abundance.OVANLIG),
                    species("Q_EX_COMMON", iucnStatus = "EX"),
                    species("Q_EX_RARE", abundance = Abundance.OVANLIG, iucnStatus = "EX"),
                    species("Q_EW_RARE", abundance = Abundance.SÄLLSYNT, iucnStatus = "EW"),
                )
            val selector = DailyBirdSelector { pool }
            val picks = (0L until 365L).map { selector.selectFor(LocalDate(2026, 1, 1).plusDays(it))?.speciesId }
            assertEquals(setOf("Q_LIVE_COMMON", "Q_LIVE_RARE"), picks.toSet())
        }

    @Test
    fun `an extinct species in the pool changes no other day's bird`() =
        runTest {
            val living =
                (1..20).associate { i ->
                    species("Q$i", abundance = if (i % 2 == 0) Abundance.ALLMÄN else Abundance.OVANLIG)
                }
            val withExtinct =
                living + mapOf(species("Q_EX", abundance = Abundance.OVANLIG, iucnStatus = "EX"))
            val dates = (0L until 365L).map { LocalDate(2026, 1, 1).plusDays(it) }
            val before = DailyBirdSelector { living }
            val after = DailyBirdSelector { withExtinct }
            assertEquals(dates.map { before.selectFor(it) }, dates.map { after.selectFor(it) })
        }

    @Test
    fun `only extinct species means no daily bird`() =
        runTest {
            val selector = DailyBirdSelector { mapOf(species("Q_EX", iucnStatus = "EX")) }
            assertNull(selector.selectFor(LocalDate(2026, 5, 25)))
        }
}

private fun LocalDate.plusDays(days: Long): LocalDate {
    val epoch = this.toEpochDays()
    return LocalDate.fromEpochDays((epoch + days).toInt())
}
