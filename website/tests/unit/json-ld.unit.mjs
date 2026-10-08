// node --test "tests/unit/*.unit.mjs"  (the .unit.mjs suffix keeps Playwright from picking it up)
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { serializeJsonLd } from '../../src/lib/json-ld.mjs';

test('escapes < so an embedded </script> can never close the script element early', () => {
  const value = { '@graph': [{ name: '</script><script>alert(1)</script>' }] };
  const serialized = serializeJsonLd(value);

  assert.equal(serialized.includes('<'), false, 'no literal < should remain in the serialized output');
  assert.equal(serialized.includes('</script'), false);
  assert.deepEqual(JSON.parse(serialized), value, 'JSON.parse on the escaped output returns the original value unchanged');
});

test('leaves ordinary JSON-LD content unchanged (no < to escape)', () => {
  const value = { '@type': 'WebSite', name: 'Birdy', url: 'https://birdy.community/' };
  const serialized = serializeJsonLd(value);

  assert.equal(serialized, JSON.stringify(value));
  assert.deepEqual(JSON.parse(serialized), value);
});
