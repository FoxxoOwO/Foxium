/*
 * ChroGecko Autofill content script.
 *
 * Watches focus inside address and payment forms, asks the app which saved entries fit, and fills
 * the fields the app sends back. Only token names travel over the port — values arrive on demand.
 */

const ADDRESS_TOKENS = ["name", "street", "city", "postal", "country", "phone", "email"];
const CARD_TOKENS = ["cc-name", "cc-number", "cc-exp", "cc-csc"];

const AUTOCOMPLETE_MAP = {
  "name": "name",
  "given-name": "name",
  "additional-name": "name",
  "family-name": "name",
  "organization": "name",
  "street-address": "street",
  "address-line1": "street",
  "address-line2": "street",
  "address-level1": "city",
  "address-level2": "city",
  "postal-code": "postal",
  "country": "country",
  "country-name": "country",
  "tel": "phone",
  "tel-national": "phone",
  "email": "email",
  "cc-name": "cc-name",
  "cc-given-name": "cc-name",
  "cc-family-name": "cc-name",
  "cc-number": "cc-number",
  "cc-exp": "cc-exp",
  "cc-exp-month": "cc-exp",
  "cc-exp-year": "cc-exp",
  "cc-csc": "cc-csc",
};

const HINT_PATTERNS = [
  [/cc[-_]?number|cardnumber|card[-_]?num/i, "cc-number"],
  [/cc[-_]?exp|expir/i, "cc-exp"],
  [/cc[-_]?csc|cc[-_]?cvv|cvc|security[-_]?code/i, "cc-csc"],
  [/card[-_]?name|cc[-_]?name|cardholder/i, "cc-name"],
  [/e[-_]?mail/i, "email"],
  [/phone|tel/i, "phone"],
  [/zip|postal|postcode/i, "postal"],
  [/country/i, "country"],
  [/city|town|address[-_]?level2/i, "city"],
  [/street|address1|address[-_]?line1|address/i, "street"],
  [/first[-_]?name|last[-_]?name|full[-_]?name|^name$/i, "name"],
];

function normalizeAutocomplete(value) {
  if (!value) return null;
  const parts = String(value).toLowerCase().trim().split(/\s+/);
  for (let i = parts.length - 1; i >= 0; i -= 1) {
    const token = AUTOCOMPLETE_MAP[parts[i]];
    if (token) return token;
  }
  return null;
}

function guessToken(field) {
  const autocomplete = normalizeAutocomplete(field.getAttribute("autocomplete"));
  if (autocomplete) return autocomplete;
  const haystack = [
    field.getAttribute("name"),
    field.getAttribute("id"),
    field.getAttribute("placeholder"),
    field.getAttribute("aria-label"),
  ]
    .filter(Boolean)
    .join(" ");
  if (!haystack) return null;
  for (const [pattern, token] of HINT_PATTERNS) {
    if (pattern.test(haystack)) return token;
  }
  return null;
}

function isFillable(field) {
  if (!field || field.disabled || field.readOnly) return false;
  const tag = field.tagName;
  if (tag === "TEXTAREA" || tag === "SELECT") return true;
  if (tag !== "INPUT") return false;
  const type = (field.getAttribute("type") || "text").toLowerCase();
  return type === "text" || type === "email" || type === "tel" || type === "number" || type === "password";
}

/** Fields of the focused element's form (or container/document when passed directly). */
function formFields(scopeOrField) {
  if (!scopeOrField) return [];
  const scope = (scopeOrField.nodeType === 9 || scopeOrField.tagName === "FORM")
    ? scopeOrField
    : (scopeOrField.form || scopeOrField.ownerDocument || document);
  return Array.from(scope.querySelectorAll("input, select, textarea")).filter(isFillable);
}

function tokensOf(fields) {
  const tokens = [];
  for (const field of fields) {
    const token = guessToken(field);
    if (token && !tokens.includes(token)) tokens.push(token);
  }
  return tokens;
}

function setValue(field, value) {
  if (!field || value === undefined || value === null) return;
  field.focus();
  const stringValue = String(value);
  const proto = field.tagName === "TEXTAREA"
    ? HTMLTextAreaElement.prototype
    : field.tagName === "INPUT"
      ? HTMLInputElement.prototype
      : null;
  const descriptor = proto ? Object.getOwnPropertyDescriptor(proto, "value") : null;
  if (descriptor && typeof descriptor.set === "function") {
    descriptor.set.call(field, stringValue);
  } else {
    field.value = stringValue;
  }
  field.dispatchEvent(new Event("input", { bubbles: true }));
  field.dispatchEvent(new Event("change", { bubbles: true }));
  field.blur();
}

let lastSignature = "";

function reportFocus(event) {
  const field = event.target;
  if (!isFillable(field)) return;
  const focusToken = guessToken(field);
  if (!focusToken) return;
  const fields = formFields(field);
  const tokens = tokensOf(fields);
  const kind = tokens.some((token) => CARD_TOKENS.includes(token)) ? "card" : "address";
  if (!tokens.some((token) => (kind === "card" ? CARD_TOKENS : ADDRESS_TOKENS).includes(token))) return;
  const signature = kind + "|" + tokens.join(",") + "|" + (location.hostname || "");
  if (signature === lastSignature) return;
  lastSignature = signature;
  browser.runtime.sendMessage({
    type: "autofill/fields",
    kind,
    tokens,
    host: location.hostname || "",
  });
}

document.addEventListener("focusin", reportFocus, true);
document.addEventListener("focusout", () => {
  lastSignature = "";
}, true);

browser.runtime.onMessage.addListener((message) => {
  if (!message || message.type !== "autofill/fill") return;
  const values = message.values || {};
  const active = document.activeElement;
  const scope = active && active.form ? active.form : document;
  const fields = formFields(scope);
  for (const field of fields) {
    const token = guessToken(field);
    if (token && values[token] !== undefined) setValue(field, values[token]);
  }
  lastSignature = "";
});
