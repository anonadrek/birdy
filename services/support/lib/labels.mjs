// Subject labels (spec section 4): a source label for mail from the app's Feedback button and a
// topic label from plain word rules, Swedish and English. No model reads the messages.

// The app's settings_feedback_subject, "Birdy v%1$s — feedback" in both locales.
const APP_SUBJECT = /^birdy v\d[\w.-]*\s*[—–-]?\s*feedback\b/i;

const PURCHASE = ['köp', 'kvitto', 'återbetal', 'refund', 'premium', 'prenumeration', 'subscription', 'purchase', 'payment', 'betalning', 'livstid', 'lifetime'];
const BUG = ['krasch', 'fel', 'bugg', 'crash', 'bug', 'error', 'fungerar inte', "doesn't work", 'does not work', 'broken', 'hänger sig', 'freezes'];

const escape = (s) => s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
// "fel" alone also matches the start of the English words "felt"/"fell"/"fellow" — block exactly
// those two continuations ('t', 'l') while still allowing Swedish compounds ("felet", "felaktig",
// "felmeddelande") and the bare word with punctuation ("fel", "fel.", "fel,").
const wordPattern = (word) => (word === 'fel' ? 'fel(?!t|l)' : escape(word));
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
