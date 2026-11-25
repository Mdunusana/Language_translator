// API error handling: every failure becomes an ApiError with a user-friendly message, tokens are handled safely.
import { jsonResponse, storeToken } from './setup.mjs';
import { beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { api, ApiError, apiRequest, onUnauthorized, tokenStore } from '../js/api/client.js';

let calls;
function stubFetch(responder) {
  calls = [];
  globalThis.fetch = async (url, init) => {
    calls.push({ url, init });
    return responder(url, init);
  };
}

beforeEach(() => {
  sessionStorage.clear();
  onUnauthorized(null);
});

describe('requests', () => {
  test('sends the stored token as a Bearer header and parses JSON', async () => {
    storeToken('abc');
    stubFetch(() => jsonResponse(200, { ok: true }));
    assert.deepEqual(await apiRequest('/users/profile'), { ok: true });
    assert.equal(calls[0].url, '/api/users/profile');
    assert.equal(calls[0].init.headers.Authorization, 'Bearer abc');
  });

  test('never sends a token to public endpoints, so a stale one cannot break login', async () => {
    storeToken('stale');
    stubFetch(() => jsonResponse(200, { accessToken: 'new' }));
    await api.login({ email: 'a@b.co', password: 'x' });
    assert.equal(calls[0].init.headers.Authorization, undefined);
  });

  test('drops an expired token instead of sending it', async () => {
    storeToken('old', -1000);
    stubFetch(() => jsonResponse(200, {}));
    await apiRequest('/users/profile');
    assert.equal(calls[0].init.headers.Authorization, undefined);
    assert.equal(tokenStore.get(), null);
  });

  test('JSON bodies get a JSON content type; FormData is left to the browser (multipart boundary)', async () => {
    stubFetch(() => jsonResponse(201, {}));
    await apiRequest('/x', { method: 'POST', body: { a: 1 } });
    assert.equal(calls[0].init.headers['Content-Type'], 'application/json');
    assert.equal(calls[0].init.body, '{"a":1}');

    const form = new FormData();
    form.append('sourceLanguage', 'en');
    await apiRequest('/x', { method: 'POST', body: form });
    assert.equal(calls[1].init.headers['Content-Type'], undefined);
    assert.equal(calls[1].init.body, form);
  });

  test('history filters become query parameters, skipping empty ones', async () => {
    stubFetch(() => jsonResponse(200, { items: [] }));
    await api.history({ search: 'good morning', language: '', type: 'VOICE', page: 0 });
    assert.equal(calls[0].url, '/api/translations/history?search=good+morning&type=VOICE&page=0');
  });

  test('audio endpoints return a Blob', async () => {
    stubFetch(() => new Response(new Blob([new Uint8Array([1, 2, 3])]), { status: 200, headers: { 'Content-Type': 'audio/mpeg' } }));
    const blob = await api.synthesize({ text: 'hi', language: 'zu' });
    assert.ok(blob instanceof Blob);
    assert.equal(blob.size, 3);
    assert.match(calls[0].init.headers.Accept, /audio\/mpeg/);
  });
});

describe('error handling', () => {
  test('server errors become ApiError with status, message and field errors', async () => {
    stubFetch(() => jsonResponse(400, { status: 400, message: 'One or more fields are invalid.', fieldErrors: { email: 'Enter a valid email address.' } }));
    await assert.rejects(api.register({}), (error) => {
      assert.ok(error instanceof ApiError);
      assert.equal(error.status, 400);
      assert.equal(error.message, 'One or more fields are invalid.');
      assert.deepEqual(error.fieldErrors, { email: 'Enter a valid email address.' });
      return true;
    });
  });

  test('a non-JSON error still gives a readable message', async () => {
    stubFetch(() => new Response('<html>Bad gateway</html>', { status: 502, headers: { 'Content-Type': 'text/html' } }));
    await assert.rejects(apiRequest('/x'), { status: 502, message: 'Request failed with status 502.' });
  });

  test('an unreachable server gives status 0 and a helpful message', async () => {
    stubFetch(() => { throw new TypeError('Failed to fetch'); });
    await assert.rejects(apiRequest('/x'), { status: 0, message: 'Cannot reach the LinguaVoice server. Check that the backend is running.' });
  });

  test('a request that takes too long is aborted with a timeout message', async () => {
    stubFetch((url, init) => new Promise((resolve, reject) => {
      init.signal.addEventListener('abort', () => reject(new DOMException('aborted', 'AbortError')));
    }));
    await assert.rejects(apiRequest('/x', { timeoutMs: 20 }), { status: 0, message: /took too long/ });
  });

  test('a 401 on an authenticated call clears the token and triggers sign-out', async () => {
    storeToken('revoked');
    let signedOut = 0;
    onUnauthorized(() => { signedOut += 1; });
    stubFetch(() => jsonResponse(401, { message: 'Authentication is required to access this resource.' }));
    await assert.rejects(apiRequest('/users/profile'), { status: 401 });
    assert.equal(signedOut, 1);
    assert.equal(tokenStore.get(), null);
  });

  test('a 401 from login (wrong password) does NOT trigger sign-out', async () => {
    let signedOut = 0;
    onUnauthorized(() => { signedOut += 1; });
    stubFetch(() => jsonResponse(401, { message: 'Invalid email or password.' }));
    await assert.rejects(api.login({ email: 'a@b.co', password: 'nope' }), { message: 'Invalid email or password.' });
    assert.equal(signedOut, 0);
  });
});
