// Minimal UI helpers for /ui
(function(){
  const scroller = document.querySelector('.chat');
  if (scroller) {
    // Auto-scroll to bottom on load
    setTimeout(()=>{ scroller.scrollTop = scroller.scrollHeight; }, 50);
  }

  // Submit on Enter (with Shift for newline)
  const ta = document.querySelector('.composer textarea');
  const form = document.querySelector('.composer form');
  if (ta && form) {
    ta.addEventListener('keydown', function(e){
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        form.submit();
      }
    });
  }

  // Confirm delete actions
  document.querySelectorAll('form[data-confirm]').forEach(f => {
    f.addEventListener('submit', function(e){
      const msg = f.getAttribute('data-confirm') || 'Are you sure?';
      if (!confirm(msg)) e.preventDefault();
    })
  });
})();
