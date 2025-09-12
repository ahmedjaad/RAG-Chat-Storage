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

  // Mobile sidebar toggle
  const sidebar = document.querySelector('.sidebar');
  const menuBtn = document.getElementById('menuToggle');
  if (menuBtn && sidebar) {
    menuBtn.addEventListener('click', () => {
      sidebar.classList.toggle('open');
    });
    // Close sidebar when clicking a session link
    document.querySelectorAll('.session-item a.title').forEach(a => a.addEventListener('click', ()=>{
      sidebar.classList.remove('open');
    }));
  }

  // Populate userId suggestions from API
  try {
    fetch('/ui/users.json')
      .then(r => r.ok ? r.json() : [])
      .then(users => {
        const dl = document.getElementById('userIdSuggestions');
        if (!dl || !Array.isArray(users)) return;
        dl.innerHTML = '';
        users.forEach(val => {
          const opt = document.createElement('option');
          if (val) { opt.value = val; dl.appendChild(opt); }
        });
      })
      .catch(()=>{});
  } catch(e){}
})();
