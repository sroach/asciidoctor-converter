// Simple clipboard functionality without external dependencies
function copyToClipboard(text) {
    if (navigator.clipboard && window.isSecureContext) {
        return navigator.clipboard.writeText(text);
    } else {
        // Fallback for older browsers
        const textArea = document.createElement('textarea');
        textArea.value = text;
        textArea.style.position = 'fixed';
        textArea.style.left = '-999999px';
        textArea.style.top = '-999999px';
        document.body.appendChild(textArea);
        textArea.focus();
        textArea.select();

        return new Promise((resolve, reject) => {
            if (document.execCommand('copy')) {
                resolve();
            } else {
                reject();
            }
            document.body.removeChild(textArea);
        });
    }
}

document.addEventListener('DOMContentLoaded', function() {
    // Add copy buttons to all code blocks
    const codeBlocks = document.querySelectorAll('pre.highlight, div.listingblock pre, .literalblock pre');

    codeBlocks.forEach(function(block, index) {
        // Create wrapper div if it doesn't exist
        let wrapper = block.closest('.listingblock, .literalblock');
        if (!wrapper) {
            wrapper = document.createElement('div');
            wrapper.className = 'listingblock';
            block.parentNode.insertBefore(wrapper, block);
            wrapper.appendChild(block);
        }

        // Add copy button
        const button = document.createElement('button');
        button.className = 'copy-button';
        button.innerHTML = '📋 Copy';
        button.title = 'Copy to clipboard';

        // Insert button
        wrapper.style.position = 'relative';
        wrapper.appendChild(button);

        // Add click handler
        button.addEventListener('click', function(e) {
            e.preventDefault();

            // Get the text content of the code block
            const code = block.textContent || block.innerText;

            copyToClipboard(code).then(function() {
                const originalText = button.innerHTML;
                button.innerHTML = '✅ Copied!';
                button.style.backgroundColor = '#28a745';

                setTimeout(function() {
                    button.innerHTML = originalText;
                    button.style.backgroundColor = '';
                }, 2000);
            }).catch(function() {
                button.innerHTML = '❌ Error';
                button.style.backgroundColor = '#dc3545';

                setTimeout(function() {
                    button.innerHTML = '📋 Copy';
                    button.style.backgroundColor = '';
                }, 2000);
            });
        });
    });
});