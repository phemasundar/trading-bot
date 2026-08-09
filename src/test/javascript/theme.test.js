const { toggleTheme, updateThemeIcon } = require('../../main/resources/static/theme');

describe('Theme Manager', () => {
    beforeEach(() => {
        // Reset DOM and localStorage before each test
        document.documentElement.setAttribute('data-theme', 'dark');
        localStorage.clear();
        
        // Mock the icon and text elements
        document.body.innerHTML = `
            <span id="theme-icon">☀️</span>
            <span id="theme-text">Light Mode</span>
        `;
    });

    test('updateThemeIcon should update to light mode', () => {
        updateThemeIcon('light');
        expect(document.getElementById('theme-icon').textContent).toBe('🌙');
        expect(document.getElementById('theme-text').textContent).toBe('Dark Mode');
    });

    test('updateThemeIcon should update to dark mode', () => {
        updateThemeIcon('dark');
        expect(document.getElementById('theme-icon').textContent).toBe('☀️');
        expect(document.getElementById('theme-text').textContent).toBe('Light Mode');
    });

    test('toggleTheme should switch from dark to light', () => {
        document.documentElement.setAttribute('data-theme', 'dark');
        toggleTheme();
        expect(document.documentElement.getAttribute('data-theme')).toBe('light');
        expect(localStorage.getItem('theme')).toBe('light');
    });

    test('toggleTheme should switch from light to dark', () => {
        document.documentElement.setAttribute('data-theme', 'light');
        toggleTheme();
        expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
        expect(localStorage.getItem('theme')).toBe('dark');
    });

    test('should trigger DOMContentLoaded and set initial theme', () => {
        document.documentElement.setAttribute('data-theme', 'light');
        const event = document.createEvent('Event');
        event.initEvent('DOMContentLoaded', true, true);
        document.dispatchEvent(event);
        expect(document.getElementById('theme-icon').textContent).toBe('🌙');
    });
});
