/**
 * Cache del navegador para fotos y videos de /media.
 * La segunda vez se responde desde acá y no se vuelve a bajar.
 */
var CACHE = "ogt-media-v1";

self.addEventListener("install", function (event) {
  event.waitUntil(self.skipWaiting());
});

self.addEventListener("activate", function (event) {
  event.waitUntil(self.clients.claim());
});

self.addEventListener("fetch", function (event) {
  var request = event.request;
  if (request.method !== "GET" || !isMedia(request.url)) return;
  event.respondWith(fromCache(request));
});

function isMedia(raw) {
  var path = "";
  try { path = new URL(raw).pathname; } catch (e) { return false; }
  return /\/media\/.+\.(?:jpe?g|png|gif|webp|mp4|webm|mov)$/i.test(path);
}

function fromCache(request) {
  var url = request.url.split("#")[0];
  return caches.open(CACHE).then(function (cache) {
    return cache.match(url).then(function (hit) {
      if (hit) return ranged(hit, request);
      return fetch(url, { credentials: "omit" }).then(function (net) {
        if (!(net && net.ok && net.status === 200)) return net;
        return storable(net).then(function (stored) {
          return cache.put(url, stored.clone()).then(function () {
            report("guardado " + url);
            return ranged(stored, request);
          }, function (error) {
            report("put " + error);
            return ranged(stored, request);
          });
        }, function (error) {
          report("storable " + error);
          return net;
        });
      }, function (error) {
        report("fetch " + error);
        return fetch(request);
      });
    });
  }).catch(function (error) {
    report("cache " + error);
    return fetch(request);
  });
}

function report(message) {
  self.clients.matchAll().then(function (list) {
    list.forEach(function (client) { client.postMessage({ ogtCache: String(message) }); });
  });
}

function storable(response) {
  return response.blob().then(function (blob) {
    var headers = new Headers(response.headers);
    headers.delete("cache-control");
    headers.set("cache-control", "public, max-age=31536000");
    return new Response(blob, {
      status: response.status,
      statusText: response.statusText,
      headers: headers
    });
  });
}

function ranged(response, request) {
  var range = request.headers.get("range");
  if (!range) return response;
  return response.clone().arrayBuffer().then(function (buf) {
    var match = /bytes=(\d+)-(\d*)/.exec(range);
    if (!match) return response;
    var start = Number(match[1]);
    var end = match[2] ? Number(match[2]) : buf.byteLength - 1;
    if (end >= buf.byteLength) end = buf.byteLength - 1;
    if (!(start >= 0) || start > end || start >= buf.byteLength) {
      return new Response(null, {
        status: 416,
        headers: { "Content-Range": "bytes */" + buf.byteLength }
      });
    }
    var slice = buf.slice(start, end + 1);
    var headers = new Headers(response.headers);
    headers.set("Content-Range", "bytes " + start + "-" + end + "/" + buf.byteLength);
    headers.set("Content-Length", String(slice.byteLength));
    headers.set("Accept-Ranges", "bytes");
    return new Response(slice, { status: 206, statusText: "Partial Content", headers: headers });
  });
}
