// Theme Manager
const savedTheme = localStorage.getItem('theme') || 'dark';
document.documentElement.setAttribute('data-theme', savedTheme);

function toggleTheme() {
    const currentTheme = document.documentElement.getAttribute('data-theme') || 'dark';
    const newTheme = currentTheme === 'dark' ? 'light' : 'dark';
    document.documentElement.setAttribute('data-theme', newTheme);
    localStorage.setItem('theme', newTheme);
    updateThemeIcon(newTheme);
}

function updateThemeIcon(theme) {
    const icon = document.getElementById('theme-icon');
    const text = document.getElementById('theme-text');
    if (icon && text) {
        if (theme === 'light') {
            icon.textContent = '🌙';
            text.textContent = 'Dark Mode';
        } else {
            icon.textContent = '☀️';
            text.textContent = 'Light Mode';
        }
    }
}

document.addEventListener('DOMContentLoaded', () => {
    updateThemeIcon(document.documentElement.getAttribute('data-theme') || 'dark');
});
