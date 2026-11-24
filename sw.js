const CACHE_NAME = 'amar-hisab-v1.0.0';
const CORE_ASSETS = [
  './',
  './index.html',
  'https://cdn.jsdelivr.net/npm/chart.js@4.4.3/dist/chart.umd.min.js'
];

// Install: Cache core shell and Chart.js CDN
self.addEventListener('install', event => {
  event.waitUntil(
    caches.open(CACHE_NAME).then(cache => cache.addAll(CORE_ASSETS)).then(() => self.skipWaiting())
  );
});

// Activate: Clean up outdated caches
self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys =>
      Promise.all(keys.filter(k => k !== CACHE_NAME).map(k => caches.delete(k)))
    ).then(() => self.clients.claim())
  );
});

// Fetch: Network-first for HTML/API, Cache-first with background update for static assets
self.addEventListener('fetch', event => {
  const req = event.request;
  if (req.method !== 'GET') return;

  // Skip Firebase Auth / Firestore live API calls from static caching
  if (req.url.includes('googleapis.com') || req.url.includes('firebaseapp.com') || req.url.includes('gstatic.com/firebasejs')) {
    return;
  }

  if (req.mode === 'navigate') {
    event.respondWith(
      fetch(req)
        .then(res => {
          const copy = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(req, copy));
          return res;
        })
        .catch(() => caches.match(req).then(r => r || caches.match('./index.html') || caches.match('./')))
    );
    return;
  }

  event.respondWith(
    caches.match(req).then(cached => {
      const networkFetch = fetch(req).then(res => {
        if (res && res.status === 200) {
          const copy = res.clone();
          caches.open(CACHE_NAME).then(c => c.put(req, copy));
        }
        return res;
      }).catch(() => cached);
      return cached || networkFetch;
    })
  );
});