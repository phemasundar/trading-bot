// ... existing code ...
async function executeStrategy() {
    // ... code to collect filters ...
    const filter = {};
    document.querySelectorAll('[data-filter]').forEach(input => {
        const key = input.dataset.filter;
        let value = input.value;
        
        // Handle nested keys like 'shortLeg.minDelta'
        if (key.includes('.')) {
            const parts = key.split('.');
            let current = filter;
            for (let i = 0; i < parts.length - 1; i++) {
                if (!current[parts[i]]) current[parts[i]] = {};
                current = current[parts[i]];
            }
            current[parts[parts.length - 1]] = parseValue(value);
        } else {
            filter[key] = parseValue(value);
        }
    });
    // ... rest of the function ...
}
// ... existing code ...
