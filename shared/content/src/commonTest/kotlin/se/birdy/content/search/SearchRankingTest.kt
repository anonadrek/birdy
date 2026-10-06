package se.birdy.content.search

import se.birdy.content.Abundance
import se.birdy.content.Abundance.ALLMÄN
import se.birdy.content.Abundance.MINDRE_ALLMÄN
import se.birdy.content.Abundance.OVANLIG
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Release 1.3.0 Task 7g item 3: the encyclopedia's search order. The species below are the real
 * matches the app's search returns for each query (names, scientific names and abundance from
 * shared/content/species), in the alphabetical order the screen used to show them in.
 */
class SearchRankingTest {
    private data class Bird(
        val sv: String,
        val en: String,
        val scientific: String,
        val abundance: Abundance,
    )

    private fun rankSwedish(
        query: String,
        birds: List<Bird>,
    ): List<String> =
        SearchRanking
            .rank(
                query,
                birds,
                names = { SearchNames(primary = it.sv, other = it.en, scientific = it.scientific) },
                abundance = { it.abundance },
            ).map { it.sv }

    private fun rankEnglish(
        query: String,
        birds: List<Bird>,
    ): List<String> =
        SearchRanking
            .rank(
                query,
                birds,
                names = { SearchNames(primary = it.en, other = it.sv, scientific = it.scientific) },
                abundance = { it.abundance },
            ).map { it.en }

    // The 18 species "tal" finds; Talgoxe was number 13.
    private val tal =
        listOf(
            Bird("Afrikansk skedstork", "African Spoonbill", "Platalea alba", OVANLIG),
            Bird("Balkansångare", "Eastern Bonelli’s Warbler", "Phylloscopus orientalis", OVANLIG),
            Bird("Italiensk sparv", "Italian Sparrow", "Passer italiae", OVANLIG),
            Bird("Korttålärka", "Greater Short-toed Lark", "Calandrella brachydactyla", OVANLIG),
            Bird("Mindre sånglärka", "Oriental Skylark", "Alauda gulgula", OVANLIG),
            Bird("Nilsolfågel", "Nile Valley Sunbird", "Hedydipna metallica", OVANLIG),
            Bird("Orientbiätare", "Asian Green Bee-eater", "Merops orientalis", OVANLIG),
            Bird("Sandlärka", "Sand Lark", "Alaudala raytal", OVANLIG),
            Bird("Skedstork", "Eurasian Spoonbill", "Platalea leucorodia", OVANLIG),
            Bird("Större turturduva", "Oriental Turtle Dove", "Streptopelia orientalis", OVANLIG),
            Bird("Svartbukig flyghöna", "Black-bellied Sandgrouse", "Pterocles orientalis", OVANLIG),
            Bird("Tajgagök", "Oriental Cuckoo", "Cuculus optatus", OVANLIG),
            Bird("Talgoxe", "Great Tit", "Parus major", ALLMÄN),
            Bird("Tallbit", "Pine Grosbeak", "Pinicola enucleator", ALLMÄN),
            Bird("Talltita", "Willow Tit", "Poecile montanus", ALLMÄN),
            Bird("Taltrast", "Song Thrush", "Turdus philomelos", ALLMÄN),
            Bird("Tibetansk korttålärka", "Hume’s Short-toed Lark", "Calandrella acutirostris", OVANLIG),
            Bird("Vit pelikan", "Great White Pelican", "Pelecanus onocrotalus", OVANLIG),
        )

    // The 9 species "kråka" finds.
    private val kraka =
        listOf(
            Bird("Alpkråka", "Red-billed Chough", "Pyrrhocorax pyrrhocorax", OVANLIG),
            Bird("Blåkråka", "European Roller", "Coracias garrulus", OVANLIG),
            Bird("Gråkråka", "Hooded Crow", "Corvus cornix", ALLMÄN),
            Bird("Huskråka", "House Crow", "Corvus splendens", OVANLIG),
            Bird("Kråka", "Carrion Crow", "Corvus corone", OVANLIG),
            Bird("Nötkråka", "Northern Nutcracker", "Nucifraga caryocatactes", ALLMÄN),
            Bird("Savannblåkråka", "Abyssinian Roller", "Coracias abyssinicus", OVANLIG),
            Bird("Spillkråka", "Black Woodpecker", "Dryocopus martius", ALLMÄN),
            Bird("Stornäbbad kråka", "Large-billed Crow", "Corvus macrorhynchos", OVANLIG),
        )

    // Part of the 54 species "blå" finds (it also matches "Black ..." once the accent is folded).
    private val bla =
        listOf(
            Bird("Amerikansk bläsand", "American Wigeon", "Mareca americana", OVANLIG),
            Bird("Bläsand", "Eurasian Wigeon", "Mareca penelope", ALLMÄN),
            Bird("Bläsgås", "Greater White-fronted Goose", "Anser albifrons", OVANLIG),
            Bird("Blå kärrhök", "Hen Harrier", "Circus cyaneus", OVANLIG),
            Bird("Blåhake", "Bluethroat", "Luscinia svecica", ALLMÄN),
            Bird("Blåkråka", "European Roller", "Coracias garrulus", OVANLIG),
            Bird("Blåmes", "Eurasian Blue Tit", "Cyanistes caeruleus", ALLMÄN),
            Bird("Blåtrast", "Blue Rock Thrush", "Monticola solitarius", OVANLIG),
            Bird("Iberisk blåskata", "Iberian Magpie", "Cyanopica cooki", OVANLIG),
            Bird("Koltrast", "Common Blackbird", "Turdus merula", ALLMÄN),
            Bird("Spillkråka", "Black Woodpecker", "Dryocopus martius", ALLMÄN),
            Bird("Tajgablåstjärt", "Red-flanked Bluetail", "Tarsiger cyanurus", OVANLIG),
        )

    @Test
    fun `tal puts talgoxe first and the other common names starting with tal next`() {
        assertEquals(listOf("Talgoxe", "Tallbit", "Talltita", "Taltrast"), rankSwedish("tal", tal).take(4))
    }

    @Test
    fun `a match in the middle of a word comes after every word start`() {
        val ranked = rankSwedish("tal", tal)
        // "Korttålärka" only matches with the accent folded, in the middle of the word.
        assertTrue(ranked.indexOf("Korttålärka") > ranked.indexOf("Taltrast"), ranked.toString())
        // Found only through its scientific name (Pelecanus onocrotalus): after the Swedish-name matches.
        assertTrue(ranked.indexOf("Vit pelikan") > ranked.indexOf("Tibetansk korttålärka"), ranked.toString())
    }

    @Test
    fun `bla puts blames first`() {
        assertEquals("Blåmes", rankSwedish("blå", bla).first())
    }

    @Test
    fun `names written with the typed accent come before names that only match without it`() {
        val ranked = rankSwedish("blå", bla)
        assertTrue(ranked.indexOf("Blå kärrhök") < ranked.indexOf("Bläsand"), ranked.toString())
        assertTrue(ranked.indexOf("Blåtrast") < ranked.indexOf("Bläsand"), ranked.toString())
    }

    @Test
    fun `a swedish word start beats a match that is only in the english name`() {
        val ranked = rankSwedish("blå", bla)
        // Koltrast is found by "Blackbird" with the accent folded away.
        assertTrue(ranked.indexOf("Bläsand") < ranked.indexOf("Koltrast"), ranked.toString())
        assertTrue(ranked.indexOf("Iberisk blåskata") < ranked.indexOf("Koltrast"), ranked.toString())
    }

    @Test
    fun `kraka puts the exact name first then a word start then compounds by how common they are`() {
        assertEquals(
            listOf(
                "Kråka",
                "Stornäbbad kråka",
                "Gråkråka",
                "Nötkråka",
                "Spillkråka",
                "Alpkråka",
                "Blåkråka",
                "Huskråka",
                "Savannblåkråka",
            ),
            rankSwedish("kråka", kraka),
        )
    }

    @Test
    fun `an exact scientific name wins over a word start in a swedish name`() {
        val birds =
            listOf(
                Bird("Pica pica-lik skata", "Magpie lookalike", "Fictus fictus", ALLMÄN),
                Bird("Skata", "Eurasian Magpie", "Pica pica", ALLMÄN),
            )
        assertEquals("Skata", rankSwedish("pica pica", birds).first())
    }

    @Test
    fun `case and extra spaces in the query do not matter`() {
        assertEquals("Talgoxe", rankSwedish("  TAL ", tal).first())
    }

    @Test
    fun `english users get their own names first`() {
        val ranked = rankEnglish("great", tal)
        // "Great Tit" and "Great White Pelican" start with the word; "Greater Short-toed Lark" too.
        assertEquals(listOf("Great Tit", "Great White Pelican", "Greater Short-toed Lark"), ranked.take(3))
    }

    @Test
    fun `within one level a common species beats a scarcer one before the alphabet decides`() {
        val birds =
            listOf(
                Bird("Aaa tal", "A", "A a", OVANLIG),
                Bird("Bbb tal", "B", "B b", MINDRE_ALLMÄN),
                Bird("Ccc tal", "C", "C c", ALLMÄN),
            )
        assertEquals(listOf("Ccc tal", "Bbb tal", "Aaa tal"), rankSwedish("tal", birds))
    }

    @Test
    fun `a blank query keeps the order it was given`() {
        assertEquals(tal.map { it.sv }, rankSwedish("  ", tal))
    }

    // Review minors: the last tie-break is Swedish alphabetical order (å, ä, ö after z), not
    // Unicode order (which puts ä before å).
    @Test
    fun `the alphabet tie-break follows the swedish alphabet`() {
        val birds =
            listOf(
                Bird("Ötal", "A", "A a", ALLMÄN),
                Bird("Ätal", "B", "B b", ALLMÄN),
                Bird("Åtal", "C", "C c", ALLMÄN),
                Bird("Ztal", "D", "D d", ALLMÄN),
            )
        // All four match "tal" in the middle of the word, all are equally common and as long.
        assertEquals(listOf("Ztal", "Åtal", "Ätal", "Ötal"), rankSwedish("tal", birds))
    }

    @Test
    fun `a query typed with a separate accent mark counts as typed`() {
        // "bla" + U+030A COMBINING RING ABOVE, the decomposed form of "blå".
        val ranked = rankSwedish("bla\u030A", bla)
        assertEquals("Blåmes", ranked.first())
        assertTrue(ranked.indexOf("Blå kärrhök") < ranked.indexOf("Bläsand"), ranked.toString())
    }
}
