(function() {
  function resolveTheme() {
    try {
      return localStorage.getItem('theme') === 'dark' ? 'dark' : 'light';
    } catch (error) {
      return 'light';
    }
  }

  function applyTheme(theme) {
    document.documentElement.dataset.theme = theme;
    document.documentElement.style.colorScheme = theme;
  }

  function persistTheme(theme) {
    try {
      localStorage.setItem('theme', theme);
    } catch (error) {
      // Ignore storage failures and keep the theme in-memory only.
    }
  }

  function updateToggleLabel(button, theme) {
    if (!button) {
      return;
    }

    var nextTheme = theme === 'dark' ? 'light' : 'dark';
    var text = button.querySelector('.hr-theme-toggle__text');
    var label = nextTheme === 'dark' ? button.dataset.labelDark : button.dataset.labelLight;

    button.setAttribute('aria-label', label);
    button.setAttribute('title', label);

    if (text) {
      text.textContent = label;
    }
  }

  document.addEventListener('DOMContentLoaded', function() {
    var toggle = document.querySelector('[data-theme-toggle]');
    var theme = resolveTheme();

    applyTheme(theme);
    updateToggleLabel(toggle, theme);

    if (!toggle) {
      return;
    }

    toggle.addEventListener('click', function() {
      theme = document.documentElement.dataset.theme === 'dark' ? 'light' : 'dark';
      applyTheme(theme);
      persistTheme(theme);
      updateToggleLabel(toggle, theme);
    });
  });
})();

