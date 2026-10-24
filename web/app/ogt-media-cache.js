/**
 * Activa la cache de fotos y videos. El archivo lo guarda el service worker.
 */
(function () {
  if (!("serviceWorker" in navigator)) return;
  if (location.protocol !== "http:" && location.protocol !== "https:") return;
  navigator.serviceWorker.register("/ogt-media-sw.js?v=cache3").catch(function () {});
})();
