/** @type {import('tailwindcss').Config} */
export default {
  content: ["./index.html", "./src/**/*.{js,jsx}"],
  theme: {
    extend: {
      colors: {
        ledger: {
          bg: "#0E1420",
          panel: "#141B2A",
          panelAlt: "#182236",
          line: "#263151",
          ink: "#E7ECF5",
          inkDim: "#8A93A8",
          wire: "#1F9C7C",
          wireDim: "#123C31",
          amber: "#D68A2C",
          amberDim: "#4A3216",
          alarm: "#D1483F",
          alarmDim: "#432019",
          accent: "#5B7FDE",
          accentDim: "#1E2A4D",
        },
      },
      fontFamily: {
        display: ['"Space Grotesk"', "sans-serif"],
        mono: ['"JetBrains Mono"', "monospace"],
      },
      letterSpacing: {
        widest2: "0.22em",
      },
    },
  },
  plugins: [],
};
