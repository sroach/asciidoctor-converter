function openPopup(iconElement) {
    const wrapper = iconElement.parentElement;
    const svg = wrapper.querySelector('svg:not(.expand-icon svg)');
    const popup = document.getElementById('popup');
    const popupContent = document.getElementById('popupContent');

    // Clone the SVG
    const svgClone = svg.cloneNode(true);

    // Clear previous content and add new SVG
    const existingSvg = popupContent.querySelector('svg');
    if (existingSvg) {
        existingSvg.remove();
    }
    popupContent.appendChild(svgClone);

    // Show popup
    popup.classList.add('active');
    document.body.style.overflow = 'hidden';
}

function closePopup(event) {
    // Close only if clicking overlay or close button
    if (!event || event.target.id === 'popup' || event.target.classList.contains('close-btn')) {
        const popup = document.getElementById('popup');
        popup.classList.remove('active');
        document.body.style.overflow = '';
    }
}

// Close on Escape key
document.addEventListener('keydown', function(e) {
    if (e.key === 'Escape') {
        closePopup();
    }
});