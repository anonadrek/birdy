package se.birdy.app.ui.encyclopedia

enum class ArchiveChip {
    ALL,
    SONGBIRDS,
    WATER, // legacy — tas bort i Task 4 när ChipBar inte längre refererar den
    RAPTORS,
    OWLS,
    WADERS,
    WATERFOWL,
    GULLS,
    SEABIRDS,
    HERONS,
    GREBES_DIVERS,
    GAMEBIRDS,
    OTHER,
    ;

    /** Tom ALL = inget filter; annars matchar arten den ekologiska kategorin. */
    fun matches(
        family: String,
        iocOrder: String,
    ): Boolean = this == ALL || categoryOf(family, iocOrder) == this

    companion object {
        const val PASSERINE_ORDER = "Passeriformes"

        // Legacy order-baserade set — tas bort i Task 2 när ArchiveViewModel bytt till matches().
        val orderSets: Map<ArchiveChip, Set<String>> =
            mapOf(
                ALL to emptySet(),
                SONGBIRDS to setOf("Passeriformes"),
                WATER to setOf("Anseriformes", "Suliformes", "Pelecaniformes", "Podicipediformes", "Gaviiformes"),
                RAPTORS to setOf("Accipitriformes", "Falconiformes"),
                OWLS to setOf("Strigiformes"),
                WADERS to setOf("Charadriiformes"),
            )

        /** Ekologiska chips → latinska familjer (matchar SpeciesSummary.family). SONGBIRDS via ordning; OTHER = komplement. */
        val familySets: Map<ArchiveChip, Set<String>> =
            mapOf(
                WATERFOWL to setOf("Anatidae"),
                RAPTORS to setOf("Accipitridae", "Falconidae", "Pandionidae"),
                WADERS to
                    setOf(
                        "Scolopacidae",
                        "Charadriidae",
                        "Glareolidae",
                        "Burhinidae",
                        "Recurvirostridae",
                        "Haematopodidae",
                        "Rostratulidae",
                        "Jacanidae",
                        "Dromadidae",
                    ),
                GULLS to setOf("Laridae", "Stercorariidae", "Alcidae"),
                SEABIRDS to
                    setOf(
                        "Procellariidae",
                        "Hydrobatidae",
                        "Oceanitidae",
                        "Sulidae",
                        "Phalacrocoracidae",
                        "Anhingidae",
                        "Fregatidae",
                        "Phaethontidae",
                    ),
                HERONS to
                    setOf("Ardeidae", "Ciconiidae", "Threskiornithidae", "Pelecanidae", "Phoenicopteridae", "Scopidae"),
                GREBES_DIVERS to setOf("Podicipedidae", "Gaviidae"),
                GAMEBIRDS to setOf("Phasianidae", "Odontophoridae", "Numididae"),
                OWLS to setOf("Strigidae", "Tytonidae"),
            )

        val categorizedFamilies: Set<String> = familySets.values.flatten().toSet()

        /** Returnerar den ekologiska chip:en för en art. Aldrig ALL; faller till OTHER. */
        fun categoryOf(
            family: String,
            iocOrder: String,
        ): ArchiveChip {
            if (iocOrder == PASSERINE_ORDER) return SONGBIRDS
            familySets.forEach { (chip, families) -> if (family in families) return chip }
            return OTHER
        }
    }
}
