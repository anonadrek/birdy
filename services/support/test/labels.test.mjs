import { test } from 'node:test';
import assert from 'node:assert/strict';
import { addressOf } from '../lib/address.mjs';
import { labelFor } from '../lib/labels.mjs';

test('addressOf takes the address out of a display name, lower case', () => {
  assert.equal(addressOf('Anna Andersson <Anna@Example.se>'), 'anna@example.se');
  assert.equal(addressOf(' anna@example.se '), 'anna@example.se');
});

test('addressOf takes the LAST angle-bracket address when there is more than one (a quoted display name containing one)', () => {
  assert.equal(addressOf('"Anna <x>" <anna@ex.se>'), 'anna@ex.se');
});

test('the app feedback subject gets [App], in both of the app subject forms', () => {
  assert.equal(labelFor({ subject: 'Birdy v1.3.0 — feedback', text: '' }), '[App]');
  assert.equal(labelFor({ subject: 'Birdy v1.2.0-rc3 - feedback', text: 'Hej' }), '[App]');
});

test('purchase words give [Köp], in Swedish and English', () => {
  assert.equal(labelFor({ subject: 'Köpet syns inte', text: '' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Hello', text: 'I want a refund for Premium' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Livstid', text: '' }), '[Köp]');
});

test('bug words give [Fel], and an apostrophe of any kind matches', () => {
  assert.equal(labelFor({ subject: 'Appen kraschar', text: '' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Hi', text: 'The map doesn’t work' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Hi', text: 'It freezes on start' }), '[Fel]');
});

test('purchase wins over bug, and the app label comes first', () => {
  assert.equal(labelFor({ subject: 'Köpet kraschade appen', text: '' }), '[Köp]');
  assert.equal(labelFor({ subject: 'Birdy v1.3.0 — feedback', text: 'Appen kraschar' }), '[App][Fel]');
});

test('words only match at the start of a word', () => {
  // "fel" inside "helfel" or "sköp" must not count; "felaktig" and "köpte" do.
  assert.equal(labelFor({ subject: 'Ett helfel', text: '' }), '[Birdy]');
  assert.equal(labelFor({ subject: 'Felaktig art', text: '' }), '[Fel]');
  assert.equal(labelFor({ subject: 'Jag köpte Premium', text: '' }), '[Köp]');
});

test('nothing matching gives [Birdy]', () => {
  assert.equal(labelFor({ subject: 'Tack för appen', text: 'Fin fågelbok!' }), '[Birdy]');
  assert.equal(labelFor({}), '[Birdy]');
});
