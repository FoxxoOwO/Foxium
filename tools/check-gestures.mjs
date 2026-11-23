import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import vm from "node:vm";

const GESTURES_JS = readFileSync("app/src/main/assets/extensions/sys_gestures/content.js", "utf8");

function runHarness() {
  const windowListeners = new Map();
  const documentListeners = new Map();
  const messages = [];
  let nextTimerId = 1;
  const timers = new Map();

  function addListener(map, type, fn) {
    if (!map.has(type)) map.set(type, []);
    map.get(type).push(fn);
  }

  function dispatch(map, type, event = {}) {
    for (const fn of map.get(type) || []) {
      fn(event);
    }
  }

  const sandbox = {
    window: {
      innerWidth: 400,
      scrollY: 0,
    },
    document: {
      documentElement: { clientWidth: 400, scrollTop: 0 },
      body: {},
      addEventListener(type, fn) {
        addListener(documentListeners, type, fn);
      },
    },
    location: {
      href: "https://example.com/",
      reload() {},
    },
    browser: {
      runtime: {
        sendMessage(msg) {
          messages.push(msg);
          return Promise.resolve();
        },
      },
    },
    addEventListener(type, fn) {
      addListener(windowListeners, type, fn);
    },
    setTimeout(fn, delay) {
      const id = nextTimerId++;
      timers.set(id, { fn, delay });
      return id;
    },
    clearTimeout(id) {
      timers.delete(id);
    },
    Date,
    Math,
    URL,
    Promise,
  };
  sandbox.globalThis = sandbox;

  vm.runInNewContext(GESTURES_JS, sandbox);

  const linkNode = {
    nodeType: 1,
    textContent: "Example link",
    closest(selector) {
      if (selector === "a[href]") {
        return {
          textContent: "Example link",
          ownerDocument: { baseURI: "https://example.com/" },
          getAttribute(name) {
            return name === "href" ? "https://example.com/target" : null;
          },
        };
      }
      return null;
    },
  };

  function flushTimers() {
    for (const [id, entry] of Array.from(timers.entries())) {
      timers.delete(id);
      entry.fn();
    }
  }

  function touchEvent(x, y = 200) {
    return {
      touches: [{ clientX: x, clientY: y }],
      changedTouches: [{ clientX: x, clientY: y }],
      target: linkNode,
    };
  }

  // 1. Left-edge long press (< 32px) must not open context menu.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(10));
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);

  // 2. Right-edge long press (> 400 - 32px) must not open context menu.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(390));
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);

  // 3. Center long press on a link opens context menu after timer fires.
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(200));
  flushTimers();
  const contextMessages = messages.filter((m) => m.type === "contextmenu/request");
  assert.equal(contextMessages.length, 1);
  assert.equal(contextMessages[0].link, "https://example.com/target");

  // 4. Center long press cancelled by pointercancel / scroll / blur / visibilitychange must not open context menu.
  for (const cancelType of ["pointercancel", "scroll", "blur"]) {
    messages.length = 0;
    dispatch(windowListeners, "touchstart", touchEvent(200));
    dispatch(windowListeners, cancelType, {});
    flushTimers();
    assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);
  }
  messages.length = 0;
  dispatch(windowListeners, "touchstart", touchEvent(200));
  dispatch(documentListeners, "visibilitychange", {});
  flushTimers();
  assert.equal(messages.filter((m) => m.type === "contextmenu/request").length, 0);
}

runHarness();
console.log("check-gestures: ok");
