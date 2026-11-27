import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const AUTOFILL_JS = readFileSync(
  new URL("../app/src/main/assets/extensions/sys_autofill/content.js", import.meta.url),
  "utf8",
);

function makeField(attrs, form) {
  const events = [];
  return {
    tagName: "INPUT",
    disabled: false,
    readOnly: false,
    value: "",
    form,
    ownerDocument: null,
    getAttribute(name) {
      return Object.prototype.hasOwnProperty.call(attrs, name) ? attrs[name] : null;
    },
    closest: () => null,
    parentElement: null,
    focusCount: 0,
    blurCount: 0,
    focus() {
      this.focusCount += 1;
    },
    blur() {
      this.blurCount += 1;
    },
    closest() {
      return null;
    },
    dispatchEvent(event) {
      events.push(event.type);
    },
    events,
  };
}

function makeForm(id, fieldSpecs) {
  const form = {
    tagName: "FORM",
    id,
    isConnected: true,
    fields: [],
    querySelectorAll() {
      return form.fields;
    },
  };
  form.fields = fieldSpecs.map((spec) => makeField(spec, form));
  return form;
}

function makeContainer(id, fieldSpecs) {
  const container = {
    tagName: "DIV",
    id,
    isConnected: true,
    fields: [],
    querySelectorAll() {
      return container.fields;
    },
  };
  container.fields = fieldSpecs.map((spec) => {
    const field = makeField(spec, null);
    field.parentElement = container;
    return field;
  });
  return container;
}

function runHarness() {
  const docListeners = new Map();
  const messageListeners = [];
  const sentMessages = [];

  const shippingForm = makeForm("shipping", [
    { autocomplete: "email", type: "email" },
    { autocomplete: "name", type: "text" },
    { autocomplete: "postal-code", type: "text" },
  ]);
  const billingForm = makeForm("billing", [
    { autocomplete: "email", type: "email" },
    { autocomplete: "name", type: "text" },
    { autocomplete: "postal-code", type: "text" },
  ]);

  const body = { tagName: "BODY" };
  const allFields = [...shippingForm.fields, ...billingForm.fields];
  const document = {
    activeElement: body,
    body,
    addEventListener(type, fn) {
      if (!docListeners.has(type)) docListeners.set(type, []);
      docListeners.get(type).push(fn);
    },
    querySelectorAll() {
      return allFields;
    },
  };
  for (const field of allFields) field.ownerDocument = document;

  function dispatchDoc(type, event) {
    for (const fn of docListeners.get(type) || []) fn(event);
  }

  function dispatchMessage(msg) {
    for (const fn of messageListeners) fn(msg);
  }

  class MockEvent {
    constructor(type, options = {}) {
      this.type = type;
      this.bubbles = Boolean(options.bubbles);
    }
  }

  const sandbox = {
    document,
    console,
    location: { hostname: "shop.example" },
    HTMLInputElement: { prototype: {} },
    HTMLTextAreaElement: { prototype: {} },
    Event: MockEvent,
    browser: {
      runtime: {
        sendMessage(msg) {
          sentMessages.push(msg);
          return Promise.resolve();
        },
        onMessage: {
          addListener(fn) {
            messageListeners.push(fn);
          },
        },
      },
    },
  };
  sandbox.globalThis = sandbox;

  vm.runInNewContext(AUTOFILL_JS, sandbox);

  // Focus a field inside shippingForm, then blur to body before filling (as happens when tapping
  // the native Compose suggestion strip). Only shippingForm should receive values.
  document.activeElement = shippingForm.fields[0];
  dispatchDoc("focusin", { target: shippingForm.fields[0] });
  assert.equal(sentMessages.length, 1);
  assert.equal(sentMessages[0].kind, "address");
  const shippingReport = sentMessages.at(-1);
  assert.ok(shippingReport.fields.length >= 3, "focus report must carry opaque field ids");

  document.activeElement = body;
  dispatchDoc("focusout", { target: shippingForm.fields[0] });

  dispatchMessage({
    type: "autofill/fill",
    fields: shippingReport.fields,
    values: {
      email: "alice@example.com",
      name: "Alice Novak",
      postal: "11000",
    },
  });

  assert.equal(shippingForm.fields[0].value, "alice@example.com");
  assert.equal(shippingForm.fields[1].value, "Alice Novak");
  assert.equal(shippingForm.fields[2].value, "11000");

  assert.equal(billingForm.fields[0].value, "");
  assert.equal(billingForm.fields[1].value, "");
  assert.equal(billingForm.fields[2].value, "");

  // Filling must not steal focus: focus() would fire focusin and re-open the suggestion strip.
  for (const field of [...shippingForm.fields, ...billingForm.fields]) {
    assert.equal(field.focusCount, 0);
    assert.equal(field.blurCount, 0);
  }

  // A standalone input (no <form>) must only fill its own container, not the whole document.
  const standalone = makeContainer("standalone", [
    { autocomplete: "email", type: "email" },
  ]);
  const unrelated = makeContainer("unrelated", [
    { autocomplete: "name", type: "text" },
  ]);
  allFields.push(...standalone.fields, ...unrelated.fields);
  for (const field of [...standalone.fields, ...unrelated.fields]) field.ownerDocument = document;

  document.activeElement = standalone.fields[0];
  dispatchDoc("focusin", { target: standalone.fields[0] });
  const standaloneReport = sentMessages.at(-1);
  document.activeElement = body;
  dispatchDoc("focusout", { target: standalone.fields[0] });

  // A field injected by the page after the report has no opaque id and must stay empty.
  const injected = makeField({ autocomplete: "cc-number" }, null);
  allFields.push(injected);

  dispatchMessage({
    type: "autofill/fill",
    fields: standaloneReport.fields,
    values: { email: "standalone@example.com", name: "Wrong", "cc-number": "4111111111111111" },
  });
  assert.equal(standalone.fields[0].value, "standalone@example.com");
  assert.equal(unrelated.fields[0].value, "");
  assert.equal(injected.value, "");
}

runHarness();
console.log("autofill checks passed");
