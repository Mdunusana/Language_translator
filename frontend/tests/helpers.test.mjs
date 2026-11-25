// Small shared helpers: language names, relative time, debounce.
import { wait } from './setup.mjs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { debounce, timeAgo } from '../js/components/format.js';
import { LANGUAGES, languageName } from '../js/components/languages.js';

test('the frontend catalog mirrors the backend catalog (same 12 codes, same order)', () => {
  assert.deepEqual(LANGUAGES.map((l) => l.code), ['en', 'af', 'zu', 'xh', 'es', 'fr', 'pt', 'de', 'zh', 'ar', 'ja', 'sw']);
});

test('language names cover catalog codes and the special values', () => {
  assert.equal(languageName('zu'), 'isiZulu');
  assert.equal(languageName('xh'), 'isiXhosa');
  assert.equal(languageName('und'), 'Unknown language');
  assert.equal(languageName('auto'), 'Detect language');
  assert.equal(languageName('ko'), 'Korean', 'falls back to the browser\'s own names');
});

test('relative time says "just now" for very recent items', () => {
  assert.equal(timeAgo(new Date().toISOString()), 'just now');
  assert.match(timeAgo(new Date(Date.now() - 5 * 60_000).toISOString()), /5 minutes ago/);
});

test('debounce runs once, after typing stops', async () => {
  let runs = 0;
  const run = debounce(() => { runs += 1; }, 30);
  run(); run(); run();
  assert.equal(runs, 0);
  await wait(60);
  assert.equal(runs, 1);
});
