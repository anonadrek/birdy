package se.birdy.content

/**
 * Extinct (EX) or extinct in the wild (EW) on the global IUCN red list (`iucn_status` in the
 * species data). The one definition behind the encyclopedia's "Utdöd" tag
 * (se.birdy.app.util.isExtinct) and the daily bird, which never suggests a species nobody can go
 * out and find (release 1.3.0: Garfågel, Kanariestrandskata and Smalnäbbad spov).
 */
fun isExtinctIucnStatus(iucnStatus: String?): Boolean = iucnStatus == "EX" || iucnStatus == "EW"
