// The LinguaVoice language catalog, mirrored from the backend (service/LanguageCatalog.java).
// Used to name languages in history, filters and preferences, which must work even for languages the
// current translation provider doesn't offer (old history) and when no provider is configured.

export const LANGUAGES = [
  { code: 'en', name: 'English' },
  { code: 'af', name: 'Afrikaans' },
  { code: 'zu', name: 'isiZulu' },
  { code: 'xh', name: 'isiXhosa' },
  { code: 'es', name: 'Spanish' },
  { code: 'fr', name: 'French' },
  { code: 'pt', name: 'Portuguese' },
  { code: 'de', name: 'German' },
  { code: 'zh', name: 'Mandarin Chinese' },
  { code: 'ar', name: 'Arabic' },
  { code: 'ja', name: 'Japanese' },
  { code: 'sw', name: 'Swahili' },
];

const displayNames = (() => {
  try { return new Intl.DisplayNames(['en'], { type: 'language' }); } catch { return null; }
})();

export function languageName(code) {
  if (code === 'und') return 'Unknown language';
  if (code === 'auto') return 'Detect language';
  const known = LANGUAGES.find((language) => language.code === code);
  if (known) return known.name;
  try { return displayNames?.of(code) ?? code; } catch { return code; }
}

/** Appends one <option> per catalog language to a <select>. */
export function fillLanguageOptions(select) {
  for (const language of LANGUAGES) {
    select.append(Object.assign(document.createElement('option'), { value: language.code, textContent: language.name }));
  }
}
