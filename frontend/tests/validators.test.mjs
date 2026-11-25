// Form validation: the browser-side rules must match the backend's (dto/ValidationRules.java).
import './setup.mjs';
import { describe, test } from 'node:test';
import assert from 'node:assert/strict';
import {
  validateEmail, validateFullName, validateMatch, validateNewPassword, validateRequired,
} from '../js/components/validators.js';

describe('validateFullName', () => {
  test('requires 2–100 characters after trimming', () => {
    assert.equal(validateFullName('   '), 'Full name is required.');
    assert.match(validateFullName(' A '), /between 2 and 100/);
    assert.equal(validateFullName('Ada Lovelace'), '');
    assert.match(validateFullName('x'.repeat(101)), /between 2 and 100/);
  });
});

describe('validateEmail', () => {
  test('accepts normal addresses, including surrounding spaces', () => {
    assert.equal(validateEmail('ada@example.com'), '');
    assert.equal(validateEmail('  ada@example.co.za  '), '');
  });
  test('rejects missing or malformed addresses', () => {
    assert.equal(validateEmail(''), 'Email is required.');
    for (const bad of ['ada', 'ada@', '@example.com', 'ada@example', 'a da@example.com']) {
      assert.equal(validateEmail(bad), 'Enter a valid email address.', bad);
    }
  });
});

describe('validateNewPassword', () => {
  test('enforces 8–72 characters with a letter and a number', () => {
    assert.equal(validateNewPassword(''), 'Password is required.');
    assert.match(validateNewPassword('abc12'), /between 8 and 72/);
    assert.match(validateNewPassword('a1'.repeat(37)), /between 8 and 72/); // 74 chars: BCrypt would truncate
    assert.match(validateNewPassword('onlyletters'), /letter and one number/);
    assert.match(validateNewPassword('12345678'), /letter and one number/);
    assert.equal(validateNewPassword('Secret123'), '');
  });
});

describe('validateMatch / validateRequired', () => {
  test('confirmation must be present and equal', () => {
    assert.equal(validateMatch('', 'Secret123'), 'Please confirm your password.');
    assert.equal(validateMatch('Secret124', 'Secret123'), 'Passwords do not match.');
    assert.equal(validateMatch('Secret123', 'Secret123'), '');
  });
  test('required uses the given message', () => {
    assert.equal(validateRequired('  ', 'Needed.'), 'Needed.');
    assert.equal(validateRequired('x', 'Needed.'), '');
  });
});
