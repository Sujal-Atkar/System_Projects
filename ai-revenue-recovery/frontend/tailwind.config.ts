/** @type {import('tailwindcss').Config} */
module.exports = {
  content: ["./app/**/*.{js,ts,jsx,tsx}", "./components/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      colors: {
        paper: "#FAFAF8",
        "paper-raised": "#F1EFEA",
        ink: "#12161C",
        slate: {
          DEFAULT: "#4B5566",
          light: "#8890A0",
          faint: "#D8DCE3",
        },
        risk: {
          DEFAULT: "#B7791F",
          bg: "#FBF1DF",
        },
        recovered: {
          DEFAULT: "#1E7A5F",
          bg: "#E7F3EE",
        },
        ledger: {
          DEFAULT: "#2C4A8C",
          bg: "#EAEFF8",
        },
        danger: {
          DEFAULT: "#A13A34",
          bg: "#FBEAE8",
        },
      },
      fontFamily: {
        sans: ["var(--font-plex-sans)", "system-ui", "sans-serif"],
        serif: ["var(--font-plex-serif)", "Georgia", "serif"],
      },
      borderRadius: {
        sm: "3px",
        DEFAULT: "5px",
        lg: "8px",
      },
    },
  },
  plugins: [],
};
