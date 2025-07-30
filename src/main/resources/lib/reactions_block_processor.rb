require 'asciidoctor/extensions'

include Asciidoctor

# Ruby equivalent of the Kotlin ReactionsBlockProcessor with persistent user selections
class ReactionsBlockProcessor < Extensions::BlockMacroProcessor
  use_dsl
  named :reactions

  def process(parent, target, attrs)
    # Only support HTML backend
    backend = parent.document.attr('backend')
    return create_paragraph(parent, '', {}) unless backend == 'html5'

    # Get document information
    document_name = parent.document.attr('docname', 'unknown')
    document_author = parent.document.attr('author', 'anonymous')

    # Check for role attribute to determine alignment
    role = attrs['role'] || attrs[1] # attrs[1] is positional parameter
    alignment_style = role == 'right' ? 'justify-content: flex-end;' : ''

    # Create unique identifier for this reactions block
    reactions_id = "reactions-#{document_name.gsub(/[^a-zA-Z0-9]/, '-')}"

    # Define available reactions
    reactions = [
      { emoji: '👍', id: 'thumbs_up', title: 'Thumbs Up' },
      { emoji: '👎', id: 'thumbs_down', title: 'Thumbs Down' },
      { emoji: '😄', id: 'smile', title: 'Smile' },
      { emoji: '🎉', id: 'party', title: 'Celebration' },
      { emoji: '😕', id: 'confused', title: 'Confused' },
      { emoji: '❤️', id: 'heart', title: 'Love' },
      { emoji: '🚀', id: 'rocket', title: 'Rocket' }
    ]

    # Generate reaction buttons HTML
    reaction_buttons_html = reactions.map do |reaction|
      <<~HTML.strip
        <button class="reaction-button reaction-#{reaction[:id]}" 
                style="background: none; border: none; cursor: pointer; font-size: 18px; margin: 0 5px;" 
                title="#{reaction[:title]}"
                onclick="handleReactionClick('#{reaction[:id]}', '#{document_name}', '#{document_author}', '#{reactions_id}')">
            #{reaction[:emoji]}
        </button>
      HTML
    end.join("\n")

    # Generate the complete HTML with persistent selection support
    html = <<~HTML
      <div id="#{reactions_id}" class="reactions-container" style="display: flex; align-items: center; margin: 20px 0; #{alignment_style}">
          <div class="reactions-buttons" style="display: flex; gap: 2px;">
              #{reaction_buttons_html}
          </div>
          <div class="comment-bubble" 
               style="margin-left: 15px; cursor: pointer; font-size: 20px;"
               onclick="showCommentForm('#{document_name}', '#{document_author}')">
              💬
          </div>
      </div>

      <!-- Comment Form Modal -->
      <div id="commentFormModal" style="display: none; position: fixed; top: 0; left: 0; width: 100%; height: 100%; 
           background-color: rgba(0,0,0,0.5); z-index: 1000; justify-content: center; align-items: center;">
          <div style="background-color: white; padding: 20px; border-radius: 5px; width: 80%; max-width: 500px;">
              <h3>Leave a Comment</h3>
              <input type="hidden" id="commentDocName" value="">
              <input type="hidden" id="commentAuthor" value="">
              <input type="hidden" id="commentReactionType" value="">
              <textarea id="commentText" style="width: 100%; height: 100px; margin: 10px 0; padding: 5px;"></textarea>
              <div style="display: flex; justify-content: flex-end; gap: 10px;">
                  <button onclick="closeCommentForm()" style="padding: 5px 10px;">Cancel</button>
                  <button onclick="submitComment()" style="padding: 5px 10px; background-color: #4CAF50; color: white; border: none;">Submit</button>
              </div>
          </div>
      </div>

      <script>
          // Initialize reactions on page load
          document.addEventListener('DOMContentLoaded', function() {
              restoreUserReactions('#{reactions_id}');
          });

          function getStorageKey(reactionsId, reactionType) {
              return 'reaction_' + reactionsId + '_' + reactionType;
          }

          function saveUserReaction(reactionsId, reactionType, selected) {
              const key = getStorageKey(reactionsId, reactionType);
              if (selected) {
                  localStorage.setItem(key, 'true');
              } else {
                  localStorage.removeItem(key);
              }
          }

          function getUserReaction(reactionsId, reactionType) {
              const key = getStorageKey(reactionsId, reactionType);
              return localStorage.getItem(key) === 'true';
          }

          function restoreUserReactions(reactionsId) {
              const container = document.getElementById(reactionsId);
              if (!container) return;

              const reactionButtons = container.querySelectorAll('.reaction-button');
              let hasSelection = false;

              reactionButtons.forEach(btn => {
                  // Look for classes that match the pattern 'reaction-' followed by a reaction ID
                  // Exclude 'reaction-button' by checking the class is longer than 'reaction-'.length
                  const reactionType = Array.from(btn.classList)
                      .find(cls => cls.startsWith('reaction-') && cls !== 'reaction-button')
                      ?.replace('reaction-', '');
                  
                  if (reactionType && getUserReaction(reactionsId, reactionType)) {
                      btn.style.transform = 'scale(1.2)';
                      btn.style.opacity = '1';
                      hasSelection = true;
                  } else {
                      btn.style.transform = 'scale(1)';
                      btn.style.opacity = hasSelection ? '0.5' : '1';
                  }
              });
          }


          function handleReactionClick(reactionType, docName, author, reactionsId) {
              const container = document.getElementById(reactionsId);
              const reactionButtons = container.querySelectorAll('.reaction-button');
              const clickedButton = container.querySelector('.reaction-' + reactionType);
              
              // Check if this reaction is already selected
              const isCurrentlySelected = getUserReaction(reactionsId, reactionType);
              
              // Clear all previous selections for this reactions block
              reactionButtons.forEach(btn => {
                  // Same fix here - exclude 'reaction-button' class
                  const btnReactionType = Array.from(btn.classList)
                      .find(cls => cls.startsWith('reaction-') && cls !== 'reaction-button')
                      ?.replace('reaction-', '');
                  if (btnReactionType) {
                      saveUserReaction(reactionsId, btnReactionType, false);
                  }
              });

              // Toggle the clicked reaction
              const newSelectionState = !isCurrentlySelected;
              if (newSelectionState) {
                  saveUserReaction(reactionsId, reactionType, true);
              }

              // Update visual state
              reactionButtons.forEach(btn => {
                  if (btn.classList.contains('reaction-' + reactionType) && newSelectionState) {
                      btn.style.transform = 'scale(1.2)';
                      btn.style.opacity = '1';
                  } else {
                      btn.style.transform = 'scale(1)';
                      btn.style.opacity = newSelectionState ? '0.5' : '1';
                  }
              });

              // Post the data only if a reaction is selected
              if (newSelectionState) {
                  const data = {
                      documentName: docName,
                      author: author,
                      reactionType: reactionType
                  };

                  console.log('Reaction data:', data);

                  // You can implement actual AJAX post here
                   fetch('reactions/api/feedback', {
                       method: 'POST',
                       headers: { 'Content-Type': 'application/json' },
                       body: JSON.stringify(data)
                   });
              }
          }


          function showCommentForm(docName, author, reactionType = null) {
              document.getElementById('commentDocName').value = docName;
              document.getElementById('commentAuthor').value = author;
              if (reactionType) {
                  document.getElementById('commentReactionType').value = reactionType;
              }

              const modal = document.getElementById('commentFormModal');
              modal.style.display = 'flex';
          }

          function closeCommentForm() {
              const modal = document.getElementById('commentFormModal');
              modal.style.display = 'none';
              document.getElementById('commentText').value = '';
          }

          function submitComment() {
              const docName = document.getElementById('commentDocName').value;
              const author = document.getElementById('commentAuthor').value;
              const comment = document.getElementById('commentText').value;
              const reactionType = document.getElementById('commentReactionType').value;

              if (!comment.trim()) {
                  alert('Please enter a comment');
                  return;
              }

              const data = {
                  documentName: docName,
                  author: author,
                  comment: comment,
                  reactionType: reactionType
              };

              console.log('Comment data:', data);

              // You can implement actual AJAX post here
               fetch('/reactions/api/comment', {
                   method: 'POST',
                   headers: { 'Content-Type': 'application/json' },
                   body: JSON.stringify(data)
               });

              closeCommentForm();
              alert('Thank you for your feedback!');
          }
      </script>
    HTML

    create_pass_block(parent, html, {})
  end
end

Asciidoctor::Extensions.register do
  block_macro ReactionsBlockProcessor
end
