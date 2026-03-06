/**
 * Theme toggle — persists choice in localStorage.
 */
(function() {
    const STORAGE_KEY = 'agents-ui-theme';

    function getPreferred() {
        return localStorage.getItem(STORAGE_KEY) || 'dark';
    }

    function apply(theme) {
        document.documentElement.setAttribute('data-theme', theme);
        localStorage.setItem(STORAGE_KEY, theme);
        const btn = document.getElementById('theme-toggle');
        if (btn) {
            btn.innerHTML = theme === 'dark'
                ? '<svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M8 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8Zm0 1A5 5 0 1 1 8 3a5 5 0 0 1 0 10Zm5.657-9.657a.75.75 0 0 1 0 1.06l-.707.707a.75.75 0 1 1-1.06-1.06l.707-.707a.75.75 0 0 1 1.06 0Zm-9.193 9.193a.75.75 0 0 1 0 1.06l-.707.707a.75.75 0 0 1-1.06-1.06l.707-.707a.75.75 0 0 1 1.06 0ZM8 0a.75.75 0 0 1 .75.75v1a.75.75 0 0 1-1.5 0v-1A.75.75 0 0 1 8 0ZM3 8a.75.75 0 0 1-.75.75h-1a.75.75 0 0 1 0-1.5h1A.75.75 0 0 1 3 8Zm13 0a.75.75 0 0 1-.75.75h-1a.75.75 0 0 1 0-1.5h1A.75.75 0 0 1 16 8Zm-8 5a.75.75 0 0 1 .75.75v1a.75.75 0 0 1-1.5 0v-1A.75.75 0 0 1 8 13Z"/></svg> Light'
                : '<svg viewBox="0 0 16 16" width="16" height="16"><path fill="currentColor" d="M9.598 1.591a.749.749 0 0 1 .785-.175 7.001 7.001 0 1 1-8.967 8.967.75.75 0 0 1 .961-.96 5.5 5.5 0 0 0 7.22-7.832Z"/></svg> Dark';
        }
    }

    function toggle() {
        apply(getPreferred() === 'dark' ? 'light' : 'dark');
    }

    // Apply on load
    apply(getPreferred());

    // Bind toggle button
    document.addEventListener('DOMContentLoaded', () => {
        const btn = document.getElementById('theme-toggle');
        if (btn) btn.addEventListener('click', toggle);
        apply(getPreferred()); // re-apply to update button text
    });

    window.AgentsTheme = { toggle, apply, getPreferred };
})();

