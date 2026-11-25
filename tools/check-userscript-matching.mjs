import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const source = readFileSync(
  new URL("../app/src/main/assets/extensions/sys_userscripts/matching.js", import.meta.url),
  "utf8",
);
const sandbox = { module: { exports: {} }, URL, RegExp };
vm.runInNewContext(source, sandbox, { filename: "matching.js" });
const { matchPattern, includeMatches, shouldRun } = sandbox.module.exports;

assert.equal(matchPattern("<all_urls>", "https://example.com/"), true);
assert.equal(matchPattern("<all_urls>", "about:blank"), false);
assert.equal(matchPattern("https://example.com/*", "https://example.com/a?b=1"), true);
assert.equal(matchPattern("https://example.com/*", "http://example.com/a"), false);
assert.equal(matchPattern("https://example.com/*", "https://sub.example.com/a"), false);
assert.equal(matchPattern("*://*.example.com/*", "https://www.example.com/x"), true);
assert.equal(matchPattern("*://*.example.com/*", "https://example.com/x"), true);
assert.equal(matchPattern("*://*.example.com/*", "https://notexample.com/x"), false);
assert.equal(matchPattern("https://example.com/foo/*", "https://example.com/foo/bar"), true);
assert.equal(matchPattern("https://example.com/foo/*", "https://example.com/foo"), false);
assert.equal(matchPattern("file:///*", "file:///tmp/a.html"), true);
assert.equal(matchPattern("*://example.com/*", "https://example.com/a"), true);
assert.equal(matchPattern("*://example.com/*", "http://example.com/a"), true);
assert.equal(matchPattern("*://example.com/*", "ftp://example.com/a"), false);
assert.equal(matchPattern("*:///*", "file:///tmp/a.html"), false);
assert.equal(matchPattern("https://example.com/*", "not a url"), false);
// Shorthand patterns without a scheme and explicit ports are valid Tampermonkey forms.
assert.equal(matchPattern("example.com/*", "https://example.com/x"), true);
assert.equal(matchPattern("*/foo*", "https://example.com/foobar"), true);
assert.equal(matchPattern("*.example.com/foo*bar", "https://www.example.com/foo-X-bar"), true);
assert.equal(matchPattern("https://example.com:8080/*", "https://example.com:8080/x"), true);
assert.equal(matchPattern("https://example.com:8080/*", "https://example.com/x"), false);
assert.equal(matchPattern("https://example.com/*", "https://example.com:8080/x"), true);

assert.equal(includeMatches("https://example.com/*", "https://example.com/a?b=1"), true);
assert.equal(includeMatches("/^https:\\/\\/example\\.com\\/\\d+$/", "https://example.com/42"), true);
assert.equal(includeMatches("/^https:\\/\\/example\\.com\\/\\d+$/", "https://example.com/x"), false);

const script = {
  match: ["https://example.com/*"],
  include: ["*://*.example.org/*"],
  exclude: ["https://example.com/private/*"],
};
assert.equal(shouldRun(script, "https://example.com/page"), true);
assert.equal(shouldRun(script, "https://example.com/private/x"), false);
assert.equal(shouldRun(script, "https://sub.example.org/page"), true);
assert.equal(shouldRun({ match: [], include: [], exclude: [] }, "https://example.com/"), false);

console.log("userscript matching checks passed");
