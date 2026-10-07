// A minimal species record in the shape of website/src/data/species/<QID>.json.
export function record(overrides = {}) {
  const base = {
    qid: 'Q25307',
    status: 'pending',
    publish: false,
    slug: { sv: 'skata', en: 'eurasian-magpie' },
    names: { sv: 'Skata', en: 'Eurasian Magpie', scientific: 'Pica pica' },
    group: 'songbirds',
    images: [
      { role: 'hero', file: 'Q25307/hero.webp', width: 1600, height: 1067, author: 'Julian Herzog', license: 'CC BY 4.0', licenseUrl: 'https://creativecommons.org/licenses/by/4.0/', sourceUrl: 'https://commons.wikimedia.org/wiki/File:x.jpg' },
      { role: 'extra', file: 'Q25307/extra.webp', author: 'Someone', license: 'CC BY-SA 4.0' },
    ],
    audio: { file: 'Q25307/voice.mp3', durationSec: 20, trimmed: false, author: 'Oona Räisänen (Mysid)', license: 'Public domain', licenseUrl: null, sourceUrl: 'https://commons.wikimedia.org/wiki/File:Pica_pica.ogg' },
    data: { totalReports: 457391 },
    text: null,
  };
  return { ...base, ...overrides };
}

export function withHero(rec, hero) {
  return { ...rec, images: rec.images.map((img) => (img.role === 'hero' ? { ...img, ...hero } : img)) };
}

export function withAudio(rec, audio) {
  return { ...rec, audio: { ...rec.audio, ...audio } };
}

export function approved(rec, leadText) {
  return {
    ...rec,
    status: 'ok',
    verification: { method: 'auto', at: '2026-10-07', model: 'x', spotChecked: false },
    text: { en: { lead: [{ text: leadText, factIds: ['f01'] }, { text: 'Second sentence.', factIds: [] }] } },
  };
}
