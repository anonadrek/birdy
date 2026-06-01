package se.birdy.content.build

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

class FamilyGroupsTest {
    private val groups = FamilyGroups.loadDefault()

    // Real content (839 arter). Arbetskatalog för jvmTest = modulroten shared/content.
    private val content = SpeciesYamlParser().parseAll(Path.of("species"))

    @Test
    fun `order list matches the locked 15 group ids`() {
        val expected =
            listOf(
                "songbirds", "waterfowl", "waders", "gulls_terns", "auks", "seabirds",
                "grebes_divers", "herons_storks", "raptors", "owls", "gamebirds",
                "doves", "woodpeckers", "cranes_rails", "other",
            )
        assertEquals(expected, groups.groupIds)
    }

    @Test
    fun `routes representative families`() {
        assertEquals("auks", groups.groupFor("Alcidae", "Charadriiformes"))
        assertEquals("gulls_terns", groups.groupFor("Laridae", "Charadriiformes"))
        assertEquals("gulls_terns", groups.groupFor("Stercorariidae", "Charadriiformes"))
        assertEquals("waders", groups.groupFor("Scolopacidae", "Charadriiformes"))
        assertEquals("raptors", groups.groupFor("Falconidae", "Falconiformes"))
        assertEquals("woodpeckers", groups.groupFor("Picidae", "Piciformes"))
        assertEquals("doves", groups.groupFor("Columbidae", "Columbiformes"))
        assertEquals("songbirds", groups.groupFor("Paridae", "Passeriformes"))
        assertEquals("other", groups.groupFor("Cuculidae", "Cuculiformes"))
        assertEquals("other", groups.groupFor("Nonexistentidae", "Madeupiformes"))
    }

    @Test
    fun `every content family is explicitly mapped`() {
        val unmapped =
            content
                .map { (_, y) -> y.taxonomy.family to y.taxonomy.ioc_order }
                .distinct()
                .filterNot { (fam, order) -> groups.isExplicitlyMapped(fam, order) }
                .map { it.first }
                .toSortedSet()
        assertTrue(unmapped.isEmpty(), "Omappade familjer (lägg till i family_groups.yaml): $unmapped")
    }

    @Test
    fun `species counts per group match the locked taxonomy`() {
        val counts =
            content
                .groupingBy { (_, y) -> groups.groupFor(y.taxonomy.family, y.taxonomy.ioc_order) }
                .eachCount()
        assertEquals(378, counts["songbirds"])
        assertEquals(53, counts["waterfowl"])
        assertEquals(66, counts["waders"])
        assertEquals(44, counts["gulls_terns"])
        assertEquals(7, counts["auks"])
        assertEquals(37, counts["seabirds"])
        assertEquals(9, counts["grebes_divers"])
        assertEquals(31, counts["herons_storks"])
        assertEquals(51, counts["raptors"])
        assertEquals(23, counts["owls"])
        assertEquals(31, counts["gamebirds"])
        assertEquals(17, counts["doves"])
        assertEquals(17, counts["woodpeckers"])
        assertEquals(13, counts["cranes_rails"])
        assertEquals(62, counts["other"])
        assertEquals(839, counts.values.sum())
    }
}
