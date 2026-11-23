'use strict';

window.SemiEditorTheme = {
  setTheme(theme) {
    const dark = theme === 'dark';
    document.documentElement.dataset.theme = dark ? 'dark' : 'light';
    document.querySelector('meta[name="theme-color"]').content = dark ? '#191a1c' : '#fffdf3';
  },
};

SemiEditorTheme.setTheme(new URLSearchParams(location.search).get('theme')
  || (matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'));
