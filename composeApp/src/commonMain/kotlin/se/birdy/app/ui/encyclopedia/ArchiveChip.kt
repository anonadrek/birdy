package se.birdy.app.ui.encyclopedia

enum class ArchiveChip {
    ALL,
    SONGBIRDS,
    WATER,
    RAPTORS,
    OWLS,
    WADERS,
    ;

    companion object {
        val orderSets: Map<ArchiveChip, Set<String>> =
            mapOf(
                ALL to emptySet(),
                SONGBIRDS to setOf("Passeriformes"),
                WATER to setOf("Anseriformes", "Suliformes", "Pelecaniformes", "Podicipediformes", "Gaviiformes"),
                RAPTORS to setOf("Accipitriformes", "Falconiformes"),
                OWLS to setOf("Strigiformes"),
                WADERS to setOf("Charadriiformes"),
            )
    }
}
