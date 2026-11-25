// The WAV encoder must produce exactly the format the backend accepts (WavAudio.java): 16 kHz, mono, 16-bit PCM.
import './setup.mjs';
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { encodeWav } from '../js/components/wav.js';
import { niceScale } from '../js/components/charts.js';

const ascii = (view, offset) => String.fromCharCode(...new Uint8Array(view.buffer, offset, 4));

test('writes a valid 44-byte RIFF/WAVE header for 16 kHz mono 16-bit PCM', () => {
  const view = new DataView(encodeWav(new Float32Array(16000), 16000));
  assert.equal(view.byteLength, 44 + 32000);
  assert.equal(ascii(view, 0), 'RIFF');
  assert.equal(view.getUint32(4, true), 36 + 32000);
  assert.equal(ascii(view, 8), 'WAVE');
  assert.equal(ascii(view, 12), 'fmt ');
  assert.equal(view.getUint16(20, true), 1, 'PCM');
  assert.equal(view.getUint16(22, true), 1, 'mono');
  assert.equal(view.getUint32(24, true), 16000, 'sample rate');
  assert.equal(view.getUint32(28, true), 32000, 'byte rate');
  assert.equal(view.getUint16(34, true), 16, 'bits per sample');
  assert.equal(ascii(view, 36), 'data');
  assert.equal(view.getUint32(40, true), 32000);
});

test('converts samples to 16-bit and clamps out-of-range values instead of wrapping', () => {
  const view = new DataView(encodeWav(new Float32Array([0, 1, -1, 0.5, 2, -3]), 16000));
  const sample = (i) => view.getInt16(44 + i * 2, true);
  assert.equal(sample(0), 0);
  assert.equal(sample(1), 32767);
  assert.equal(sample(2), -32768);
  assert.equal(sample(3), 16383);
  assert.equal(sample(4), 32767, 'clamped, not wrapped to a negative value');
  assert.equal(sample(5), -32768);
});

test('chart axis uses clean round steps', () => {
  assert.deepEqual(niceScale(0), { max: 4, step: 1 });
  assert.deepEqual(niceScale(8), { max: 8, step: 2 });
  assert.deepEqual(niceScale(13), { max: 15, step: 5 });
  assert.deepEqual(niceScale(3), { max: 3, step: 1 });
  assert.deepEqual(niceScale(120), { max: 150, step: 50 });
});
