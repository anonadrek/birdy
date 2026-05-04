package se.birdy.app.di

import se.birdy.app.ui.encyclopedia.EncyclopediaViewModel
import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository

class AppGraph(
    val repository: SpeciesRepository,
    val defaultLocale: Locale = Locale.SV,
) {
    fun encyclopediaViewModel(): EncyclopediaViewModel = EncyclopediaViewModel(repository, defaultLocale)
}
