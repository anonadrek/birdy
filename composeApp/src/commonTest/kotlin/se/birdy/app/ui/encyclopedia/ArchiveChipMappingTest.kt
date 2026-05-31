package se.birdy.app.ui.encyclopedia

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArchiveChipMappingTest {
    @Test
    fun `family sets do not overlap and total 37`() {
        val sizeSum = ArchiveChip.familySets.values.sumOf { it.size }
        val distinct =
            ArchiveChip.familySets.values
                .flatten()
                .toSet()
                .size
        assertEquals(sizeSum, distinct, "A family appears in more than one chip")
        assertEquals(37, distinct, "Expected 37 explicitly mapped families")
    }

    @Test
    fun `mapped families are never passerine`() {
        val overlap = ArchiveChip.categorizedFamilies intersect PASSERINE_FAMILIES
        assertTrue(overlap.isEmpty(), "Passerine family in a familySet: $overlap")
    }

    @Test
    fun `every mapped and passerine family exists in content`() {
        val known = CONTENT_FAMILY_COUNTS.keys
        assertTrue((ArchiveChip.categorizedFamilies - known).isEmpty(), "Mapped family not in content (typo?)")
        assertTrue((PASSERINE_FAMILIES - known).isEmpty(), "Passerine family not in content (typo?)")
    }

    @Test
    fun `categoryOf routes representative families`() {
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Alcidae", "Charadriiformes"))
        assertEquals(ArchiveChip.WADERS, ArchiveChip.categoryOf("Scolopacidae", "Charadriiformes"))
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Laridae", "Charadriiformes"))
        assertEquals(ArchiveChip.GULLS, ArchiveChip.categoryOf("Stercorariidae", "Charadriiformes"))
        assertEquals(ArchiveChip.WATERFOWL, ArchiveChip.categoryOf("Anatidae", "Anseriformes"))
        assertEquals(ArchiveChip.RAPTORS, ArchiveChip.categoryOf("Accipitridae", "Accipitriformes"))
        assertEquals(ArchiveChip.OWLS, ArchiveChip.categoryOf("Strigidae", "Strigiformes"))
        assertEquals(ArchiveChip.GAMEBIRDS, ArchiveChip.categoryOf("Phasianidae", "Galliformes"))
        assertEquals(ArchiveChip.SEABIRDS, ArchiveChip.categoryOf("Procellariidae", "Procellariiformes"))
        assertEquals(ArchiveChip.HERONS, ArchiveChip.categoryOf("Ardeidae", "Pelecaniformes"))
        assertEquals(ArchiveChip.GREBES_DIVERS, ArchiveChip.categoryOf("Podicipedidae", "Podicipediformes"))
        assertEquals(ArchiveChip.SONGBIRDS, ArchiveChip.categoryOf("Fringillidae", "Passeriformes"))
        assertEquals(ArchiveChip.OTHER, ArchiveChip.categoryOf("Picidae", "Piciformes"))
        assertEquals(ArchiveChip.OTHER, ArchiveChip.categoryOf("Columbidae", "Columbiformes"))
    }

    @Test
    fun `species counts per chip match the content snapshot`() {
        val counts = mutableMapOf<ArchiveChip, Int>()
        for ((family, n) in CONTENT_FAMILY_COUNTS) {
            val order = if (family in PASSERINE_FAMILIES) "Passeriformes" else "_nonpasserine_"
            val chip = ArchiveChip.categoryOf(family, order)
            counts[chip] = (counts[chip] ?: 0) + n
        }
        assertEquals(378, counts[ArchiveChip.SONGBIRDS])
        assertEquals(53, counts[ArchiveChip.WATERFOWL])
        assertEquals(51, counts[ArchiveChip.RAPTORS])
        assertEquals(66, counts[ArchiveChip.WADERS])
        assertEquals(51, counts[ArchiveChip.GULLS])
        assertEquals(37, counts[ArchiveChip.SEABIRDS])
        assertEquals(31, counts[ArchiveChip.HERONS])
        assertEquals(9, counts[ArchiveChip.GREBES_DIVERS])
        assertEquals(31, counts[ArchiveChip.GAMEBIRDS])
        assertEquals(23, counts[ArchiveChip.OWLS])
        assertEquals(109, counts[ArchiveChip.OTHER])
        assertEquals(839, counts.values.sum())
        assertEquals(0, counts[ArchiveChip.ALL] ?: 0, "ALL must never be returned by categoryOf")
    }

    @Test
    fun `matches treats ALL as no-filter and others by category`() {
        assertTrue(ArchiveChip.ALL.matches("Picidae", "Piciformes"))
        assertTrue(ArchiveChip.GULLS.matches("Alcidae", "Charadriiformes"))
        assertFalse(ArchiveChip.WADERS.matches("Alcidae", "Charadriiformes"))
        assertTrue(ArchiveChip.OTHER.matches("Picidae", "Piciformes"))
    }

    private companion object {
        // Snapshot of shared/content/species, mätt 2026-05-30. Regressions-ankare + typo-vakt.
        val CONTENT_FAMILY_COUNTS: Map<String, Int> =
            mapOf(
                "Anatidae" to 53,
                "Muscicapidae" to 51,
                "Fringillidae" to 41,
                "Laridae" to 40,
                "Accipitridae" to 38,
                "Scolopacidae" to 34,
                "Phasianidae" to 28,
                "Alaudidae" to 27,
                "Sylviidae" to 23,
                "Strigidae" to 22,
                "Motacillidae" to 19,
                "Corvidae" to 19,
                "Acrocephalidae" to 19,
                "Procellariidae" to 18,
                "Phylloscopidae" to 18,
                "Picidae" to 17,
                "Passeridae" to 17,
                "Emberizidae" to 17,
                "Columbidae" to 17,
                "Charadriidae" to 17,
                "Ardeidae" to 16,
                "Sturnidae" to 13,
                "Falconidae" to 12,
                "Rallidae" to 10,
                "Paridae" to 10,
                "Estrildidae" to 10,
                "Turdidae" to 9,
                "Laniidae" to 9,
                "Cuculidae" to 9,
                "Apodidae" to 9,
                "Hirundinidae" to 8,
                "Caprimulgidae" to 8,
                "Pteroclidae" to 7,
                "Alcidae" to 7,
                "Sittidae" to 6,
                "Ploceidae" to 6,
                "Hydrobatidae" to 6,
                "Alcedinidae" to 6,
                "Threskiornithidae" to 5,
                "Pycnonotidae" to 5,
                "Psittacidae" to 5,
                "Prunellidae" to 5,
                "Podicipedidae" to 5,
                "Phalacrocoracidae" to 5,
                "Otididae" to 5,
                "Nectariniidae" to 5,
                "Meropidae" to 5,
                "Leiothrichidae" to 5,
                "Stercorariidae" to 4,
                "Locustellidae" to 4,
                "Glareolidae" to 4,
                "Gaviidae" to 4,
                "Cisticolidae" to 4,
                "Ciconiidae" to 4,
                "Burhinidae" to 4,
                "Sulidae" to 3,
                "Remizidae" to 3,
                "Regulidae" to 3,
                "Pelecanidae" to 3,
                "Gruidae" to 3,
                "Zosteropidae" to 2,
                "Recurvirostridae" to 2,
                "Psittaculidae" to 2,
                "Phoenicopteridae" to 2,
                "Paradoxornithidae" to 2,
                "Odontophoridae" to 2,
                "Oceanitidae" to 2,
                "Malaconotidae" to 2,
                "Haematopodidae" to 2,
                "Coraciidae" to 2,
                "Cettiidae" to 2,
                "Certhiidae" to 2,
                "Calcariidae" to 2,
                "Viduidae" to 1,
                "Upupidae" to 1,
                "Tytonidae" to 1,
                "Turnicidae" to 1,
                "Troglodytidae" to 1,
                "Tichodromidae" to 1,
                "Struthionidae" to 1,
                "Scopidae" to 1,
                "Rostratulidae" to 1,
                "Phaethontidae" to 1,
                "Panuridae" to 1,
                "Pandionidae" to 1,
                "Oriolidae" to 1,
                "Numididae" to 1,
                "Monarchidae" to 1,
                "Jacanidae" to 1,
                "Hypocoliidae" to 1,
                "Fregatidae" to 1,
                "Dromadidae" to 1,
                "Cinclidae" to 1,
                "Bucerotidae" to 1,
                "Bombycillidae" to 1,
                "Anhingidae" to 1,
                "Aegithalidae" to 1,
            )

        val PASSERINE_FAMILIES: Set<String> =
            setOf(
                "Muscicapidae",
                "Fringillidae",
                "Alaudidae",
                "Sylviidae",
                "Motacillidae",
                "Corvidae",
                "Acrocephalidae",
                "Phylloscopidae",
                "Passeridae",
                "Emberizidae",
                "Sturnidae",
                "Paridae",
                "Estrildidae",
                "Turdidae",
                "Laniidae",
                "Hirundinidae",
                "Sittidae",
                "Ploceidae",
                "Pycnonotidae",
                "Prunellidae",
                "Nectariniidae",
                "Leiothrichidae",
                "Locustellidae",
                "Cisticolidae",
                "Remizidae",
                "Regulidae",
                "Zosteropidae",
                "Paradoxornithidae",
                "Malaconotidae",
                "Cettiidae",
                "Certhiidae",
                "Calcariidae",
                "Viduidae",
                "Troglodytidae",
                "Tichodromidae",
                "Panuridae",
                "Oriolidae",
                "Monarchidae",
                "Hypocoliidae",
                "Cinclidae",
                "Bombycillidae",
                "Aegithalidae",
            )
    }
}
