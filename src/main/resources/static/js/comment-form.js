/**
 * JavaScript for handling the comment form submission.
 * Shows loading spinner and handles form submission.
 */
document.addEventListener('DOMContentLoaded', function() {
    const commentForm = document.getElementById('commentForm');
    const loadingElement = document.getElementById('loading');
    const errorElement = document.getElementById('error');
    
    if (commentForm) {
        commentForm.addEventListener('submit', function(e) {
            e.preventDefault();
            
            const youtubeUrl = document.getElementById('youtubeUrl').value;
            const commentCount = document.getElementById('commentCount').value;
            
            // Show loading spinner
            loadingElement.classList.remove('d-none');
            errorElement.classList.add('d-none');
            
            // Submit the form
            this.submit();
        });
    }
}); 