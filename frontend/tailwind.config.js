/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        // rgb(var(--x) / <alpha-value>) rather than plain hex (2026-09-19, FLEXCUBE dashboard
        // light/dark toggle) - lets every existing "ledger-x/60"-style opacity utility keep
        // working unchanged while the actual R G B triplet swaps per-theme via CSS custom
        // properties (see index.css's ":root" vs "[data-theme='light']" blocks). Only wherever
        // that [data-theme] attribute is actually set (scoped to the FlexcubeDashboard page's own
        // root element, not documentElement) do these repaint - the existing converter page never
        // sets it, so it renders with exactly the same dark values as before, untouched.
        ledger: {
          void: "rgb(var(--ledger-void) / <alpha-value>)",
          bg: "rgb(var(--ledger-bg) / <alpha-value>)",
          panel: "rgb(var(--ledger-panel) / <alpha-value>)",
          panelAlt: "rgb(var(--ledger-panelAlt) / <alpha-value>)",
          line: "rgb(var(--ledger-line) / <alpha-value>)",
          ink: "rgb(var(--ledger-ink) / <alpha-value>)",
          inkDim: "rgb(var(--ledger-inkDim) / <alpha-value>)",
          wire: "rgb(var(--ledger-wire) / <alpha-value>)",
          wireDim: "rgb(var(--ledger-wireDim) / <alpha-value>)",
          amber: "rgb(var(--ledger-amber) / <alpha-value>)",
          amberDim: "rgb(var(--ledger-amberDim) / <alpha-value>)",
          alarm: "rgb(var(--ledger-alarm) / <alpha-value>)",
          alarmDim: "rgb(var(--ledger-alarmDim) / <alpha-value>)",
          accent: "rgb(var(--ledger-accent) / <alpha-value>)",
          accentDim: "rgb(var(--ledger-accentDim) / <alpha-value>)",
          violet: "rgb(var(--ledger-violet) / <alpha-value>)",
          cyan: "rgb(var(--ledger-cyan) / <alpha-value>)",
        },
      },
      fontFamily: {
        display: ['"Space Grotesk"', "sans-serif"],
        mono: ['"JetBrains Mono"', "monospace"],
        // Rajdhani (2026-09-19, "futuristic" redesign request) - deliberately a SEPARATE token
        // from "display", not a replacement for it: font-display (Space Grotesk) is shared with
        // the main converter page (App.jsx and friends), which nobody asked to restyle. font-hud
        // is used only inside pages/FlexcubeDashboard.jsx - a technical, HUD-style face that stays
        // legible at small sizes, unlike more decorative sci-fi faces (e.g. Orbitron) that get hard
        // to read below ~14px.
        hud: ['"Rajdhani"', "sans-serif"],
      },
      letterSpacing: {
        widest2: "0.22em",
      },
    },
  },
  plugins: [],
};
