// Session handling: protected pages redirect to login, and ?next= can never send users to another site.
import { fakeLocation, jsonResponse, storeToken } from './setup.mjs';
import { beforeEach, describe, test } from 'node:test';
import assert from 'node:assert/strict';
import { apiRequest } from '../js/api/client.js';
import { HOME_PAGE, postLoginDestination, redirectIfAuthenticated, requireAuth, session } from '../js/auth/session.js';

beforeEach(() => {
  sessionStorage.clear();
  fakeLocation.reset();
});

describe('postLoginDestination (open-redirect protection)', () => {
  test('returns to the page the user asked for', () => {
    fakeLocation.search = '?next=history.html';
    assert.equal(postLoginDestination(), 'history.html');
  });

  test('ignores anything that is not a plain page of this site', () => {
    for (const next of ['//evil.example', 'https://evil.example/x.html', 'javascript:alert(1)', '../secret.html',
      'login.html', 'register.html', 'HISTORY.HTML', 'history.html?x=1', '']) {
      fakeLocation.search = `?next=${encodeURIComponent(next)}`;
      assert.equal(postLoginDestination(), HOME_PAGE, next);
    }
  });
});

describe('requireAuth', () => {
  test('without a session it redirects to login, remembering the page', () => {
    fakeLocation.reset('/dashboard.html');
    assert.equal(requireAuth(), false);
    assert.deepEqual(fakeLocation.replaced, ['login.html?reason=auth-required&next=dashboard.html']);
  });

  test('with a valid session it lets the page load', () => {
    storeToken();
    assert.equal(requireAuth(), true);
    assert.deepEqual(fakeLocation.replaced, []);
  });

  test('a later 401 from the API signs out and redirects with reason=expired', async () => {
    storeToken();
    session.setUser({ fullName: 'Ada' });
    fakeLocation.reset('/history.html');
    requireAuth();
    globalThis.fetch = async () => jsonResponse(401, { message: 'no' });
    await assert.rejects(apiRequest('/translations/history'));
    assert.deepEqual(fakeLocation.replaced, ['login.html?reason=expired&next=history.html']);
    assert.equal(session.user(), null);
  });
});

describe('login and register pages', () => {
  test('an already signed-in user is sent to the app', () => {
    storeToken();
    assert.equal(redirectIfAuthenticated(), true);
    assert.deepEqual(fakeLocation.replaced, [HOME_PAGE]);
  });

  test('logout clears the session and shows the signed-out message', () => {
    storeToken();
    session.setUser({ fullName: 'Ada' });
    session.logout();
    assert.equal(session.isAuthenticated(), false);
    assert.equal(session.user(), null);
    assert.deepEqual(fakeLocation.assigned, ['login.html?reason=signed-out']);
  });
});
