package se.birdy.app.ui.credits

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLinkStyles
import se.birdy.content.Locale
import se.birdy.content.SpeciesId
import se.birdy.content.model.PhotoCredit
import se.birdy.content.model.SpeciesImage
import se.birdy.content.model.SpeciesTextSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Release 1.3.0, legal review §2 and §4 (Task 7e-2, 7i-fix A): what a photo credit and the species
 * text's credit say, and where their links go. The words mirror the Swedish and English string
 * resources; the screen tests (ProfileCreditsTest) read the real ones.
 */
class CreditTextTest {
    private val styles = TextLinkStyles()
    private val sv = PhotoCreditWords(photoBy = "Foto: %1\$s", resized = "nedskalad", publicDomain = "public domain")
    private val en = PhotoCreditWords(photoBy = "Photo: %1\$s", resized = "resized", publicDomain = "public domain")

    private fun image(
        license: String,
        author: String,
        file: String,
    ) = SpeciesImage(
        role = "hero",
        path = "Q1/hero.webp",
        width = 1,
        height = 1,
        license = license,
        author = author,
        sourceUrl = "https://commons.wikimedia.org/wiki/File:$file",
        commonsFileName = file,
    )

    /** Every link in [text] as (linked words, url), no-break spaces read as spaces. */
    private fun links(text: AnnotatedString): List<Pair<String, String>> =
        text.getLinkAnnotations(0, text.length).map { range ->
            text.substring(range.start, range.end).replace(NO_BREAK_SPACE, ' ') to (range.item as LinkAnnotation.Url).url
        }

    /** The text with its no-break spaces read as spaces (see the test of keepTogether below). */
    private fun AnnotatedString.plain(): String = text.replace(NO_BREAK_SPACE, ' ')

    private val ccBySa = image("CC BY-SA 4.0", "Musicaline", "Turdus merula 1294.jpg")
    private val publicDomain = image("Public domain", "Robert Burton / USFWS", "Falco columbarius FWS 14007.jpg")
    private val cc0 = image("CC0", "Hobbyfotowiki", "Great tit (Parus major), North Rhine-Westphalia.jpg")

    @Test
    fun `a cc by-sa photo credits the photographer the licence commons and the resizing`() {
        val text = photoCreditText(ccBySa, sv, PhotoCreditForm.Full, styles)
        assertEquals("Foto: Musicaline · CC BY-SA 4.0 · Wikimedia Commons · nedskalad", text.plain())
        assertEquals(
            listOf(
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
                "Wikimedia Commons" to "https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg",
            ),
            links(text),
        )
        assertEquals(
            "Photo: Musicaline · CC BY-SA 4.0 · Wikimedia Commons · resized",
            photoCreditText(ccBySa, en, PhotoCreditForm.Full, styles).plain(),
        )
    }

    @Test
    fun `a public domain photo is still credited with no deed to link`() {
        val text = photoCreditText(publicDomain, sv, PhotoCreditForm.Full, styles)
        assertEquals("Foto: Robert Burton / USFWS · public domain · Wikimedia Commons · nedskalad", text.plain())
        assertEquals(
            listOf("Wikimedia Commons" to "https://commons.wikimedia.org/wiki/File:Falco_columbarius_FWS_14007.jpg"),
            links(text),
        )
    }

    @Test
    fun `a cc0 photo links the cc0 deed and is resized like every photo`() {
        val text = photoCreditText(cc0, sv, PhotoCreditForm.Full, styles)
        assertEquals("Foto: Hobbyfotowiki · CC0 · Wikimedia Commons · nedskalad", text.plain())
        assertEquals(
            listOf(
                "CC0" to "https://creativecommons.org/publicdomain/zero/1.0/",
                "Wikimedia Commons" to
                    "https://commons.wikimedia.org/wiki/File:Great_tit_(Parus_major),_North_Rhine-Westphalia.jpg",
            ),
            links(text),
        )
    }

    @Test
    fun `the compact credit is the photographer and the licence and the photographer opens the source`() {
        val text = photoCreditText(ccBySa, sv, PhotoCreditForm.Compact, styles)
        assertEquals("Foto: Musicaline · CC BY-SA 4.0", text.plain())
        assertEquals(
            listOf(
                "Foto: Musicaline" to "https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg",
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
            ),
            links(text),
        )
    }

    @Test
    fun `without link styles the credit reads the same and has no links`() {
        val text = photoCreditText(ccBySa, sv, PhotoCreditForm.Compact, linkStyles = null)
        assertEquals("Foto: Musicaline · CC BY-SA 4.0", text.plain())
        assertEquals(emptyList(), links(text))
    }

    // 221 file names are not plain ASCII; the link must still open (an un-encoded space or "ä"
    // can make a URL fail to open, on iOS in particular).
    @Test
    fun `a file name with spaces and non-ascii letters gives a link that opens`() {
        val image = image("CC BY 2.0", "Derek Keats", "Bearded vulture, Lämmergeier (44080366295).jpg")
        assertEquals(
            "https://commons.wikimedia.org/wiki/File:Bearded_vulture,_L%C3%A4mmergeier_(44080366295).jpg",
            links(photoCreditText(image, sv, PhotoCreditForm.Full, styles)).last().second,
        )
    }

    // A line never ends inside a licence name or "Wikimedia Commons" ("CC BY" on one line, "2.0" on
    // the next), nor starts with a separator ("· nedskalad"): no-break spaces there.
    @Test
    fun `licence names and wikimedia commons never break across lines`() {
        val text = photoCreditText(ccBySa, sv, PhotoCreditForm.Full, styles).text
        assertEquals(
            "Foto: Musicaline\u00A0· CC\u00A0BY-SA\u00A04.0\u00A0· Wikimedia\u00A0Commons\u00A0· nedskalad",
            text,
        )
        val credit = textCreditText(listOf(svSource), Locale.SV, textSv, styles)!!.text
        assertEquals(true, credit.endsWith("CC\u00A0BY-SA\u00A04.0."), credit)
    }

    // "Bildkällor" under About shows "Foto: X" on its own line and the rest below it: the same
    // credit, in the same words and with the same links, as on the species page.
    @Test
    fun `the photo credits list credits a photo exactly as its species page does`() {
        val credit =
            PhotoCredit(
                speciesId = SpeciesId("Q25234"),
                speciesName = "Koltrast",
                scientificName = "Turdus merula",
                role = "hero",
                path = "Q25234/hero.webp",
                license = "CC BY-SA 4.0",
                author = "Musicaline",
                commonsFileName = "Turdus merula 1294.jpg",
            )
        val line = photoCreditText(credit, sv, PhotoCreditForm.LicenseLine, styles)
        assertEquals("CC BY-SA 4.0 · Wikimedia Commons · nedskalad", line.plain())
        assertEquals(links(photoCreditText(ccBySa, sv, PhotoCreditForm.Full, styles)), links(line))
        assertEquals(photoCreditText(ccBySa, sv, PhotoCreditForm.Full, styles).plain(), "Foto: Musicaline · " + line.plain())
        val pd = photoCreditText(credit.copy(license = "Public domain"), en, PhotoCreditForm.LicenseLine, styles)
        assertEquals("public domain · Wikimedia Commons · resized", pd.plain())
    }

    // An older fixture without the file name falls back to the name in the source URL.
    @Test
    fun `without a stored file name the link comes from the source url`() {
        val image = ccBySa.copy(commonsFileName = "")
        assertEquals("https://commons.wikimedia.org/wiki/File:Turdus_merula_1294.jpg", image.filePageUrl)
    }

    private val textSv =
        TextCreditWords(
            oneArticle = "Texten är en AI-sammanfattning av %1\$s och får delas under %2\$s.",
            twoArticles = "Texten är en AI-sammanfattning av Wikipedia-artiklarna på %1\$s och %2\$s och får delas under %3\$s.",
            article = "Wikipedia-artikeln",
            articleIn = mapOf(Locale.SV to "den svenska Wikipedia-artikeln", Locale.EN to "den engelska Wikipedia-artikeln"),
            languageName = mapOf(Locale.SV to "svenska", Locale.EN to "engelska"),
        )
    private val textEn =
        TextCreditWords(
            oneArticle = "The text is an AI summary of %1\$s and may be shared under %2\$s.",
            twoArticles = "The text is an AI summary of the Wikipedia articles in %1\$s and %2\$s and may be shared under %3\$s.",
            article = "the Wikipedia article",
            articleIn = mapOf(Locale.SV to "the Swedish Wikipedia article", Locale.EN to "the English Wikipedia article"),
            languageName = mapOf(Locale.SV to "Swedish", Locale.EN to "English"),
        )
    private val svSource = SpeciesTextSource(Locale.SV, "59064377", "https://sv.wikipedia.org/w/index.php?oldid=59064377")
    private val enSource = SpeciesTextSource(Locale.EN, "1334945574", "https://en.wikipedia.org/w/index.php?oldid=1334945574")

    @Test
    fun `the text credit links the article version shown and the licence deed`() {
        val text = textCreditText(listOf(svSource), Locale.SV, textSv, styles)!!
        assertEquals(
            "Texten är en AI-sammanfattning av Wikipedia-artikeln och får delas under CC BY-SA 4.0.",
            text.plain(),
        )
        assertEquals(
            listOf(
                "Wikipedia-artikeln" to "https://sv.wikipedia.org/w/index.php?oldid=59064377",
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/deed.sv",
            ),
            links(text),
        )
        val english = textCreditText(listOf(enSource), Locale.EN, textEn, styles)!!
        assertEquals(
            "The text is an AI summary of the Wikipedia article and may be shared under CC BY-SA 4.0.",
            english.plain(),
        )
        assertEquals(
            listOf(
                "the Wikipedia article" to "https://en.wikipedia.org/w/index.php?oldid=1334945574",
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/",
            ),
            links(english),
        )
    }

    @Test
    fun `the english fallback on the swedish app credits the english article`() {
        val text = textCreditText(listOf(enSource), Locale.SV, textSv, styles)!!
        assertEquals(
            "Texten är en AI-sammanfattning av den engelska Wikipedia-artikeln och får delas under CC BY-SA 4.0.",
            text.plain(),
        )
        assertEquals("den engelska Wikipedia-artikeln" to enSource.articleUrl, links(text).first())
    }

    // Where the stored revision was a disambiguation page, the credit links the species' current
    // article through Wikidata, and its words name the article, never a version.
    @Test
    fun `without a stored revision the credit links the article through wikidata and claims no version`() {
        val fallback = SpeciesTextSource(Locale.EN, null, "https://www.wikidata.org/wiki/Special:GoToLinkedPage/enwiki/Q335113")
        val text = textCreditText(listOf(fallback), Locale.EN, textEn, styles)!!
        assertEquals(
            "The text is an AI summary of the Wikipedia article and may be shared under CC BY-SA 4.0.",
            text.plain(),
        )
        assertEquals("the Wikipedia article" to fallback.articleUrl, links(text).first())
        val swedish = textCreditText(listOf(fallback), Locale.SV, textSv, styles)!!.plain()
        assertEquals(false, "version" in swedish || "version" in text.plain(), swedish)
    }

    @Test
    fun `texts in two languages link both articles`() {
        val text = textCreditText(listOf(svSource, enSource), Locale.SV, textSv, styles)!!
        assertEquals(
            "Texten är en AI-sammanfattning av Wikipedia-artiklarna på svenska och engelska och får delas under CC BY-SA 4.0.",
            text.plain(),
        )
        assertEquals(
            listOf(
                "svenska" to svSource.articleUrl,
                "engelska" to enSource.articleUrl,
                "CC BY-SA 4.0" to "https://creativecommons.org/licenses/by-sa/4.0/deed.sv",
            ),
            links(text),
        )
    }

    @Test
    fun `no text shown means no text credit`() {
        assertNull(textCreditText(emptyList(), Locale.SV, textSv, styles))
    }
}
