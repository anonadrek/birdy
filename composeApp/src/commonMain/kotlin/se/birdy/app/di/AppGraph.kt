package se.birdy.app.di

import se.birdy.content.Locale
import se.birdy.content.SpeciesRepository

class AppGraph(
    val repository: SpeciesRepository,
    val defaultLocale: Locale = Locale.SV,
)
