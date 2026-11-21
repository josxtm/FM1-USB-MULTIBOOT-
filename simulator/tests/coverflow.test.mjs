import test from "node:test";
import assert from "node:assert/strict";
import fs from "node:fs";
import vm from "node:vm";
import { fileURLToPath } from "node:url";

const file = fileURLToPath(new URL("../index.html", import.meta.url));
const html = fs.readFileSync(file, "utf8");
const match = html.match(/<script>([\s\S]*?)<\/script>/);

function setup() {
  assert.ok(match, "el prototipo debe tener su código local incorporado");
  const elements = new Map();
  const frames = [];
  const windowEvents = new Map();
  const canvasContext = new Proxy({}, {
    get(_target, prop) {
      if (prop === "createLinearGradient") return () => ({ addColorStop() {} });
      return () => {};
    }
  });
  function element(id) {
    if (!elements.has(id)) {
      elements.set(id, {
        id,
        textContent: "",
        listeners: new Map(),
        classList: { toggle() {} },
        addEventListener(type, callback) { this.listeners.set(type, callback); },
        getContext() { return canvasContext; },
        fire(type, event = {}) {
          assert.ok(this.listeners.has(type), "no event handler for " + id + ":" + type);
          this.listeners.get(type)(event);
        }
      });
    }
    return elements.get(id);
  }
  const sandbox = {
    document: { getElementById: element },
    window: {
      matchMedia: () => ({ matches: false }),
      addEventListener(type, callback) { windowEvents.set(type, callback); }
    },
    performance: { now: () => 1000 },
    requestAnimationFrame(callback) { frames.push(callback); }
  };
  vm.runInNewContext(match[1], sandbox, { filename: "coverflow-inline.js" });
  return {
    element, frames, windowEvents,
    tick() { assert.ok(frames.length > 0); frames.shift()(1016); }
  };
}

test("contiene resolución física objetivo y aviso de demo", () => {
  assert.match(html, /width="240" height="240"/);
  assert.match(html, /SIN USB/);
  assert.match(html, /no se conecta al FM1/i);
});

test("navegación anterior y siguiente envolvente", () => {
  const app = setup();
  assert.equal(app.element("name").textContent, "DARK-1");
  app.element("prev").fire("click");
  assert.equal(app.element("name").textContent, "SLOOP");
  app.element("next").fire("click");
  assert.equal(app.element("name").textContent, "DARK-1");
  app.element("next").fire("click");
  assert.equal(app.element("name").textContent, "ORIGINAL");
  app.tick();
});

test("PLAY no arranca ni instala firmware y REC devuelve al menú", () => {
  const app = setup();
  app.element("play").fire("click");
  assert.match(app.element("status").textContent, /no se carga ni modifica/i);
  app.element("back").fire("click");
  assert.match(app.element("status").textContent, /Menú de selección/);
});

test("deslizamiento horizontal y teclas controlan la selección", () => {
  const app = setup();
  const touch = app.element("touchRegion");
  touch.fire("pointerdown", { clientX: 150, clientY: 50 });
  touch.fire("pointerup", { clientX: 60, clientY: 55 });
  assert.equal(app.element("name").textContent, "ORIGINAL");
  const key = app.windowEvents.get("keydown");
  key({ key: "ArrowLeft", preventDefault() {} });
  assert.equal(app.element("name").textContent, "DARK-1");
  key({ key: "Enter", preventDefault() {} });
  assert.match(app.element("status").textContent, /selección visual/i);
  key({ key: "Escape", preventDefault() {} });
  assert.match(app.element("status").textContent, /Menú/);
});

test("INDEX.CSV real valida y reemplaza catálogo de muestra sin habilitar arranque", async () => {
  const app = setup();
  const valid = "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n" +
    "NEWFW|MI NUEVO FW|2.0|NEWFW.BIN|324|" + "a".repeat(64) + "|UNVERIFIED\n";
  app.element("catalogInput").fire("change", {
    target: { files: [{ size: valid.length, text: async () => valid }] }
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(app.element("name").textContent, "MI NUEVO FW");
  assert.equal(app.element("version").textContent, "2.0");
  assert.equal(app.element("compat").textContent, "UNVERIFIED");
  app.element("play").fire("click");
  assert.match(app.element("status").textContent, /No se carga ni modifica/i);
});

test("INDEX.CSV malicioso se rechaza sin cambiar el catálogo", async () => {
  const app = setup();
  const invalid = "FM1CAT|1\nID|LABEL|VERSION|FILE|SIZE|SHA256|COMPAT\n" +
    "BAD|ABC|1|../X.BIN|10|" + "b".repeat(64) + "|UNVERIFIED\n";
  app.element("catalogInput").fire("change", {
    target: { files: [{ size: invalid.length, text: async () => invalid }] }
  });
  await new Promise(resolve => setImmediate(resolve));
  assert.equal(app.element("name").textContent, "DARK-1");
  assert.match(app.element("status").textContent, /rechazado/i);
});
