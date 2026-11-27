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
    : (scopeOrField.form ||
       (typeof scopeOrField.querySelectorAll === "function"
         ? scopeOrField
         : (scopeOrField.ownerDocument || document)));
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
  // Focusing each field fired focusin for every value and re-opened the suggestion strip;
  // blurring the last one also hid the keyboard. Only write through the prototype setter.
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
}

let lastSignature = "";
let lastFocusedForm = null;
let lastFocusedField = null;
let filling = false;
/** Opaque ids for the fields we reported: a page-injected hidden input has no id and is never filled. */
const fieldById = new Map();
const idByField = new WeakMap();
let nextFieldId = 1;

function reportFocus(event) {
  if (filling) return;
  const field = event.target;
  if (!isFillable(field)) return;
  const focusToken = guessToken(field);
  if (!focusToken) return;
  lastFocusedForm = field.form || null;
  lastFocusedField = field;
  // Never fall back to the whole document: that would let a page add hidden inputs that receive
  // the user's saved card/address values after a single consent click.
  const scope = field.form || field.closest("form, [data-autofill-scope]") || field.parentElement;
  const fields = formFields(scope || field);
  const candidates = [];
  const tokens = [];
  for (const candidate of fields) {
    const token = guessToken(candidate);
    if (!token || tokens.includes(token)) continue;
    let id = idByField.get(candidate);
    if (id === undefined) {
      id = nextFieldId++;
      idByField.set(candidate, id);
      fieldById.set(id, candidate);
    }
    candidates.push({ id, token });
    tokens.push(token);
  }
  const kind = tokens.some((token) => CARD_TOKENS.includes(token)) ? "card" : "address";
  if (!tokens.some((token) => (kind === "card" ? CARD_TOKENS : ADDRESS_TOKENS).includes(token))) return;
  const signature = kind + "|" + tokens.join(",") + "|" + (location.hostname || "");
  if (signature === lastSignature) return;
  lastSignature = signature;
  browser.runtime.sendMessage({
    type: "autofill/fields",
    kind,
    tokens,
    fields: candidates,
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
  filling = true;
  try {
    // Fill only the exact fields we reported. A page cannot inject extra ids into that set.
    const reported = Array.isArray(message.fields) ? message.fields : [];
    for (const candidate of reported) {
      if (!candidate || values[candidate.token] === undefined) continue;
      const field = fieldById.get(candidate.id);
      if (field && field.isConnected !== false) setValue(field, values[candidate.token]);
    }
  } finally {
    filling = false;
    lastFocusedField = null;
    lastFocusedForm = null;
    lastSignature = "";
  }
});
