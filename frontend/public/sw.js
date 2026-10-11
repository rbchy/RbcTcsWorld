/*
 * Service worker of the installable (desktop) version. Deliberately small:
 * - page loads go to the network first; only when the server cannot be reached is the offline page shown,
 * - /api calls and assets are never cached here, so prices, stock and orders are always live,
 * - a new version replaces the old one immediately.
 */
const CACHE = 'rbctcsworld-offline-v1';
const OFFLINE = '/offline.html';

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll([OFFLINE, '/icons/icon-192.png'])).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(caches.keys()
    .then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
    .then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
  if (event.request.mode !== 'navigate') return;          // everything else: browser default, no caching
  event.respondWith(fetch(event.request).catch(() => caches.match(OFFLINE)));
});
