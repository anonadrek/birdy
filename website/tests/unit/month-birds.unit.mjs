import assert from 'node:assert/strict';
import { test } from 'node:test';
import { monthBirds, monthRatio } from '../../src/lib/month-birds.mjs';

const OCT = 9;
const flat = Array(12).fill(50);
const peak = (m, v = 100) => flat.map((x, i) => (i === m ? v : x));

test('monthRatio: månadens värde delat med årets medel', () => {
  assert.equal(monthRatio(flat, OCT), 1);
  assert.equal(monthRatio([0, 0, 0, 0, 0, 0, 0, 0, 0, 120, 0, 0], OCT), 12);
  assert.equal(monthRatio(undefined, OCT), undefined);
  assert.equal(monthRatio([1, 2, 3], OCT), undefined);
  assert.equal(monthRatio(Array(12).fill(0), OCT), undefined);
});

test('monthBirds: högst kvot först, bara arter över årsmedel, högst fyra', () => {
  const species = [
    { qid: 'Q1', months: peak(OCT, 60) },
    { qid: 'Q2', months: peak(OCT, 100) },
    { qid: 'Q3', months: flat },
    { qid: 'Q4', months: peak(3, 100) },
    { qid: 'Q5', months: peak(OCT, 80) },
    { qid: 'Q6', months: peak(OCT, 70) },
    { qid: 'Q7', months: peak(OCT, 90) },
    { qid: 'Q8' },
  ];
  assert.deepEqual(monthBirds(species, OCT).map((s) => s.qid), ['Q2', 'Q7', 'Q5', 'Q6']);
  assert.deepEqual(monthBirds(species, OCT, 10).map((s) => s.qid), ['Q2', 'Q7', 'Q5', 'Q6', 'Q1']);
});

test('monthBirds: lika kvot avgörs på QID', () => {
  const species = [{ qid: 'Q30', months: peak(OCT) }, { qid: 'Q100', months: peak(OCT) }, { qid: 'Q2', months: peak(OCT) }];
  assert.deepEqual(monthBirds(species, OCT).map((s) => s.qid), ['Q100', 'Q2', 'Q30']);
});

test('monthBirds: färre än fyra visar de som finns, ingen ger en tom lista', () => {
  assert.deepEqual(monthBirds([{ qid: 'Q1', months: peak(OCT) }, { qid: 'Q2', months: flat }], OCT).map((s) => s.qid), ['Q1']);
  assert.deepEqual(monthBirds([{ qid: 'Q2', months: flat }], OCT), []);
  assert.deepEqual(monthBirds([], OCT), []);
});
