/* SPDX-License-Identifier: MIT */
/* Host-side state machine for the Cover Flow concept. No firmware operations. */
(function (root, factory) {
  const api = factory();
  if (typeof module === "object" && module.exports) module.exports = api;
  if (root) root.FM1Core = api;
})(typeof window !== "undefined" ? window : undefined, function () {
  "use strict";

  const DEMO_FIRMWARES = Object.freeze([
    { id: "dark", title: "DARK-1", subtitle: "CUSTOM SYNTH", color: "#163d47", accent: "#63f1da" },
    { id: "factory", title: "ORIGINAL", subtitle: "FACTORY SOUND", color: "#263d60", accent: "#b7caff" },
    { id: "felucca", title: "FELUCCA", subtitle: "DEMO ENTRY", color: "#582f57", accent: "#ffc4e4" },
    { id: "sloop", title: "SLOOP", subtitle: "DEMO ENTRY", color: "#654325", accent: "#ffd69b" }
  ]);

  function createState(entries = DEMO_FIRMWARES, present = true) {
    const items = entries.map(function (entry) {
      return {
        id: String(entry.id),
        title: String(entry.title).slice(0, 20),
        subtitle: String(entry.subtitle || "").slice(0, 24),
        color: entry.color || "#243242",
        accent: entry.accent || "#62e8d3"
      };
    });
    return { entries: items, selected: 0, cardPresent: !!present,
      mode: present ? (items.length ? "READY" : "EMPTY") : "NO_CARD",
      message: "" };
  }

  function wrap(n, len) {
    if (!len) return 0;
    return ((n % len) + len) % len;
  }

  function relativePosition(index, selected, count) {
    if (!count) return 0;
    let d = wrap(index - selected, count);
    if (d > count / 2) d -= count;
    return d;
  }

  function dispatch(state, event) {
    const type = typeof event === "string" ? event : event && event.type;
    if (type === "SET_CARD") {
      const present = !!event.present;
      return { ...state, cardPresent: present,
        mode: present ? (state.entries.length ? "READY" : "EMPTY") : "NO_CARD",
        message: present ? "" : "LECTOR O TARJETA NO DISPONIBLE" };
    }
    if (type === "RESET") return createState(state.entries, state.cardPresent);
    if (type === "PREV" || type === "NEXT") {
      if (!state.cardPresent || !state.entries.length) return state;
      const delta = type === "NEXT" ? 1 : -1;
      return { ...state, selected: wrap(state.selected + delta, state.entries.length),
        mode: "READY", message: "" };
    }
    if (type === "SELECT") {
      if (state.mode === "NO_CARD") return { ...state, message: "CONECTA LA MICROSD PARA NAVEGAR" };
      if (!state.entries.length) return { ...state, mode: "EMPTY", message: "SIN FIRMWARES VALIDOS" };
      return { ...state, mode: "LAUNCH_BLOCKED",
        message: "SIMULADOR: NINGUN FIRMWARE SE EJECUTA" };
    }
    if (type === "BACK") {
      if (state.mode === "LAUNCH_BLOCKED" || state.mode === "RECOVERY") {
        return { ...state, mode: state.cardPresent ?
          (state.entries.length ? "READY" : "EMPTY") : "NO_CARD", message: "" };
      }
      return { ...state, mode: "RECOVERY",
        message: "RECUPERACION: SOLO MAQUETA; SIN ACCESO A FLASH" };
    }
    return state;
  }

  return { DEMO_FIRMWARES, createState, wrap, relativePosition, dispatch };
});
