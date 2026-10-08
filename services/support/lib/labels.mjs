// Subject labels (spec section 4): a source label for mail from the app's Feedback button and a
// topic label from plain word rules, Swedish and English. No model reads the messages.

// The app's settings_feedback_subject, "Birdy v%1$s — feedback" in both locales.
const APP_SUBJECT = /^birdy v\d[\w.-]*\s*[—–-]?\s*feedback\b/i;

const PURCHASE = ['köp', 'kvitto', 'återbetal', 'refund', 'premium', 'prenumeration', 'subscription', 'purchase', 'payment', 'betalning', 'livstid', 'lifetime'];
const BUG = ['krasch', 'fel', 'bugg', 'crash', 'bug', 'error', 'fungerar inte', "doesn't work", 'does not work', 'broken', 'hänger sig', 'freezes'];

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// "fel" only counts as the bare Swedish word for "error", or followed by one of these real
// compounds/suffixes — never just any letter run, which would also swallow Felix, Felicia, feline,
// felony (and a blocklist of specific continuations like the former 'fel(?!t|l)' rule missed
// feltolkning/felkod, which start with the very letters it blocked).
const FEL_SUFFIXES = ['aktigt', 'aktig', 'meddelande', 'tolkning', 'tryck', 'rapport', 'kod', 'et', 'en', 'a'];
const wordPattern = (word) => (word === 'fel' ? `fel(?:${FEL_SUFFIXES.join('|')})?(?![\\p{L}\\p{N}])` : escape(word));
// A word counts at the start of a word: "köpte" matches "köp", "sköp" does not.
const startsAWord = (words) => new RegExp(`(?<![\\p{L}\\p{N}])(?:${words.map(wordPattern).join('|')})`, 'iu');
const PURCHASE_RE = startsAWord(PURCHASE);
const BUG_RE = startsAWord(BUG);

const normalize = (s) => s.replace(/[’‘]/g, "'").toLowerCase();

/** "[App]", "[Köp]", "[Fel]", "[App][Fel]" or "[Birdy]". */
export function labelFor({ subject = '', text = '' } = {}) {
  const source = APP_SUBJECT.test(subject.trim()) ? '[App]' : '';
  const haystack = normalize(`${subject}\n${text}`);
  const topic = PURCHASE_RE.test(haystack) ? '[Köp]' : BUG_RE.test(haystack) ? '[Fel]' : '';
  return source || topic ? `${source}${topic}` : '[Birdy]';
}
