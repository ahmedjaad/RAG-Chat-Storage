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
  // Inline rename toggling in sidebar
  document.querySelectorAll('.rename-toggle').forEach(btn => {
    btn.addEventListener('click', function(){
      const item = btn.closest('.session-item');
      const form = item ? item.querySelector('.rename-form') : null;
      if (form) form.style.display = (form.style.display === 'none' || form.style.display === '') ? 'flex' : 'none';
    });
  });
  document.querySelectorAll('.rename-cancel').forEach(btn => {
    btn.addEventListener('click', function(){
      const form = btn.closest('.rename-form');
      if (form) form.style.display = 'none';
    });
  });

  // Auto-hide AI toast
  const toast = document.getElementById('ai-toast');
  if (toast) {
    setTimeout(()=>{ toast.style.display = 'none'; }, 5000);
  }
  // Populate userId suggestions from API
  try {
    fetch('/api/v1/users', { headers: { 'X-API-KEY': (window.UI_API_KEY||'') } })
      .then(r => r.ok ? r.json() : [])
      .then(users => {
        const dl = document.getElementById('userIdSuggestions');
        if (!dl || !Array.isArray(users)) return;
        dl.innerHTML = '';
        users.forEach(u => {
          const opt = document.createElement('option');
          // Support both raw string or object with userId
          const val = typeof u === 'string' ? u : (u.userId || '');
          if (val) { opt.value = val; dl.appendChild(opt); }
        });
      })
      .catch(()=>{});
  } catch(e){}
})();
