/**
 * JavaScript for handling comment filtering on the results page.
 * Shows/hides comment sections based on the selected filter.
 */
document.addEventListener('DOMContentLoaded', function() {
    const filterDropdown = document.getElementById('commentFilter');
    
    if (filterDropdown) {
        filterDropdown.addEventListener('change', function() {
            // Get the selected filter value
            const filterValue = this.value;
            
            // Hide all sections
            const allSections = document.querySelectorAll('.comment-section');
            allSections.forEach(function(section) {
                section.style.display = 'none';
            });
            
            // Show the selected section
            const selectedSection = document.getElementById(filterValue + '-comments');
            if (selectedSection) {
                selectedSection.style.display = 'block';
            }
        });
    }
}); 