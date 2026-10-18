/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    './src/pages/**/*.{js,ts,jsx,tsx,mdx}',
    './src/components/**/*.{js,ts,jsx,tsx,mdx}',
    './src/app/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        primary: {
          50: '#fef7ee',
          100: '#fdedd6',
          200: '#fad9ac',
          300: '#f6c075',
          400: '#f1a13d',
          500: '#ed8915',
          600: '#de6f0d',
          700: '#b8530c',
          800: '#934011',
          900: '#783412',
        },
      },
    },
  },
  plugins: [],
};