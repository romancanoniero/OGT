/**
 * Encuadre 16:9 (el del feed) sobre la imagen original.
 * Arrastre, pellizco, rueda y trackpad. Cancelar no sube nada.
 */
(function () {
  var ASPECT = 16 / 9;
  var MIN_ZOOM = 1;
  var MAX_ZOOM = 4;
  var MAX_EDGE = 2048;
  var JPEG = 0.92;

  function coverScale(iw, ih, rotationDeg, viewW, viewH) {
    var rad = rotationDeg * Math.PI / 180;
    var c = Math.abs(Math.cos(rad));
    var s = Math.abs(Math.sin(rad));
    var projW = iw * c + ih * s;
    var projH = iw * s + ih * c;
    if (projW <= 1 || projH <= 1 || viewW <= 1 || viewH <= 1) return 1;
    return Math.max(viewW / projW, viewH / projH);
  }

  function clampPan(panX, panY, iw, ih, rotationDeg, zoom, viewW, viewH) {
    var scale = coverScale(iw, ih, rotationDeg, viewW, viewH) * clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    var rad = rotationDeg * Math.PI / 180;
    var c = Math.abs(Math.cos(rad));
    var s = Math.abs(Math.sin(rad));
    var drawnW = (iw * c + ih * s) * scale;
    var drawnH = (iw * s + ih * c) * scale;
    var maxX = viewW <= 1 ? 0 : Math.max(0, (drawnW - viewW) / 2) / viewW;
    var maxY = viewH <= 1 ? 0 : Math.max(0, (drawnH - viewH) / 2) / viewH;
    return { x: clamp(panX, -maxX, maxX), y: clamp(panY, -maxY, maxY) };
  }

  function outputSize(iw, ih, rotationDeg, zoom) {
    var viewW = 1000;
    var viewH = viewW / ASPECT;
    var scale = coverScale(iw, ih, rotationDeg, viewW, viewH) * clamp(zoom, MIN_ZOOM, MAX_ZOOM);
    var sampleW = scale <= 0 ? 16 : Math.max(16, Math.round(viewW / scale));
    var outW = Math.min(MAX_EDGE, sampleW);
    if (outW % 2) outW -= 1;
    if (outW < 16) outW = 16;
    var outH = Math.round(outW / ASPECT);
    if (outH % 2) outH -= 1;
    if (outH < 8) outH = 8;
    return { w: outW, h: outH };
  }

  function clamp(value, min, max) {
    return Math.min(max, Math.max(min, value));
  }

  function loadOriginal(file) {
    return new Promise(function (resolve, reject) {
      var url = URL.createObjectURL(file);
      var img = new Image();
      img.onload = function () { resolve({ img: img, url: url }); };
      img.onerror = function () {
        URL.revokeObjectURL(url);
        reject(new Error("No se pudo leer la foto"));
      };
      img.src = url;
    });
  }

  function ensureStyle() {
    if (document.getElementById("ogt-frame-style")) return;
    var style = document.createElement("style");
    style.id = "ogt-frame-style";
    style.textContent = [
      ".ogt-frame{position:fixed;inset:0;z-index:80;background:#fff;color:#111213;font-family:\"Plus Jakarta Sans\",Inter,sans-serif;",
      "display:flex;flex-direction:column;padding:16px 20px 20px;opacity:0;transform:translateY(12px);",
      "transition:opacity 240ms cubic-bezier(.4,0,.2,1),transform 280ms cubic-bezier(.4,0,.2,1)}",
      ".ogt-frame.on{opacity:1;transform:none}",
      ".ogt-frame-bar{display:flex;align-items:center;justify-content:space-between;gap:12px}",
      ".ogt-frame-bar strong{font-size:16px;font-weight:600}",
      ".ogt-frame button{border:0;border-radius:999px;padding:8px 16px;font:600 14px/1.2 inherit;cursor:pointer}",
      ".ogt-frame button:active{transform:scale(.96)}",
      ".ogt-frame .ghost{background:#f4f5f7;color:#111213}",
      ".ogt-frame .go{background:#0d3d4d;color:#fff}",
      ".ogt-frame button:disabled{background:#d8dce2;color:#8b929a;cursor:default;transform:none}",
      ".ogt-frame-stage{flex:1;display:grid;place-items:center;min-height:0;margin-top:16px}",
      ".ogt-frame-well{width:min(100%,920px);aspect-ratio:16/9;background:#f4f5f7;border-radius:16px;overflow:hidden;",
      "border:1px solid #e5e7eb;touch-action:none;cursor:grab;position:relative}",
      ".ogt-frame-well canvas{width:100%;height:100%;display:block}",
      ".ogt-frame p{margin:14px 0 0;text-align:center;color:#6b7280;font-size:13px}",
      ".ogt-frame .err{color:#ba1a1a}"
    ].join("");
    document.head.appendChild(style);
  }

  function open(opts) {
    ensureStyle();
    var file = opts.file;
    var root = document.createElement("div");
    root.className = "ogt-frame";
    root.innerHTML = [
      '<div class="ogt-frame-bar">',
      '<button type="button" class="ghost" data-cancel>Cancelar</button>',
      '<strong>Encuadre</strong>',
      '<button type="button" class="go" data-ok disabled>Listo</button>',
      "</div>",
      '<div class="ogt-frame-stage"><div class="ogt-frame-well" data-well><canvas></canvas></div></div>',
      "<p>Arrastrá para mover. Pellizcá o usá la rueda para acercar. Con Shift y la rueda, rotá.</p>",
      '<p class="err" data-err hidden></p>'
    ].join("");
    document.body.appendChild(root);
    requestAnimationFrame(function () { root.classList.add("on"); });

    var well = root.querySelector("[data-well]");
    var canvas = well.querySelector("canvas");
    var cancelBtn = root.querySelector("[data-cancel]");
    var ok = root.querySelector("[data-ok]");
    var err = root.querySelector("[data-err]");
    var zoom = 1;
    var panX = 0;
    var panY = 0;
    var rotation = 0;
    var img = null;
    var objectUrl = "";
    var pointers = new Map();
    var pinch = null;
    var closed = false;

    function fail(message) {
      err.hidden = false;
      err.textContent = message;
    }

    function paint() {
      if (!img) return;
      var cw = well.clientWidth;
      var ch = well.clientHeight;
      if (cw < 2 || ch < 2) return;
      var dpr = Math.min(window.devicePixelRatio || 1, 2);
      canvas.width = Math.round(cw * dpr);
      canvas.height = Math.round(ch * dpr);
      var ctx = canvas.getContext("2d");
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      var iw = img.naturalWidth;
      var ih = img.naturalHeight;
      zoom = clamp(zoom, MIN_ZOOM, MAX_ZOOM);
      var pan = clampPan(panX, panY, iw, ih, rotation, zoom, cw, ch);
      panX = pan.x;
      panY = pan.y;
      var s = coverScale(iw, ih, rotation, cw, ch) * zoom;
      ctx.fillStyle = "#f4f5f7";
      ctx.fillRect(0, 0, cw, ch);
      ctx.save();
      ctx.translate(cw / 2 + panX * cw, ch / 2 + panY * ch);
      ctx.rotate(rotation * Math.PI / 180);
      ctx.scale(s, s);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      ctx.drawImage(img, -iw / 2, -ih / 2, iw, ih);
      ctx.restore();
      ctx.strokeStyle = "rgba(17,18,19,.28)";
      ctx.lineWidth = 1;
      ctx.beginPath();
      for (var i = 1; i <= 2; i++) {
        ctx.moveTo(cw * i / 3 + 1, 0);
        ctx.lineTo(cw * i / 3 + 1, ch);
        ctx.moveTo(0, ch * i / 3 + 1);
        ctx.lineTo(cw, ch * i / 3 + 1);
      }
      ctx.stroke();
      ctx.strokeStyle = "rgba(255,255,255,.9)";
      ctx.beginPath();
      for (var j = 1; j <= 2; j++) {
        ctx.moveTo(cw * j / 3, 0);
        ctx.lineTo(cw * j / 3, ch);
        ctx.moveTo(0, ch * j / 3);
        ctx.lineTo(cw, ch * j / 3);
      }
      ctx.stroke();
    }

    function pair() {
      var pts = Array.from(pointers.values());
      if (pts.length < 2) return null;
      var a = pts[0];
      var b = pts[1];
      return {
        dist: Math.hypot(b.x - a.x, b.y - a.y),
        angle: Math.atan2(b.y - a.y, b.x - a.x) * 180 / Math.PI
      };
    }

    function close(done) {
      if (closed) return;
      closed = true;
      root.classList.remove("on");
      window.setTimeout(function () {
        root.remove();
        if (objectUrl) URL.revokeObjectURL(objectUrl);
        if (done) done();
      }, 240);
    }

    root.querySelector("[data-cancel]").addEventListener("click", function () {
      close(opts.onCancel);
    });
    ok.addEventListener("click", function () {
      if (!img || ok.disabled) return;
      ok.disabled = true;
      cancelBtn.disabled = true;
      var iw = img.naturalWidth;
      var ih = img.naturalHeight;
      var size = outputSize(iw, ih, rotation, zoom);
      var out = document.createElement("canvas");
      out.width = size.w;
      out.height = size.h;
      var ctx = out.getContext("2d");
      ctx.fillStyle = "#f4f5f7";
      ctx.fillRect(0, 0, size.w, size.h);
      var s = coverScale(iw, ih, rotation, size.w, size.h) * zoom;
      ctx.translate(size.w / 2 + panX * size.w, size.h / 2 + panY * size.h);
      ctx.rotate(rotation * Math.PI / 180);
      ctx.scale(s, s);
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      ctx.drawImage(img, -iw / 2, -ih / 2, iw, ih);
      out.toBlob(function (blob) {
        if (!blob) {
          ok.disabled = false;
          cancelBtn.disabled = false;
          ok.textContent = "Listo";
          fail("No se pudo encuadrar la foto");
          return;
        }
        var name = String(file.name || "foto").replace(/\.\w+$/, "") + ".jpg";
        var cropped = new File([blob], name, { type: "image/jpeg" });
        Promise.resolve(opts.onConfirm(cropped)).then(function () {
          close(opts.onQueued);
        }).catch(function (e) {
          ok.disabled = false;
          cancelBtn.disabled = false;
          ok.textContent = "Listo";
          fail((e && e.message) || "No se pudo subir la foto");
        });
      }, "image/jpeg", JPEG);
    });

    well.addEventListener("pointerdown", function (ev) {
      well.setPointerCapture(ev.pointerId);
      pointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
      pinch = pair();
    });
    well.addEventListener("pointermove", function (ev) {
      if (!pointers.has(ev.pointerId)) return;
      var prev = pointers.get(ev.pointerId);
      pointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
      var cw = well.clientWidth || 1;
      var ch = well.clientHeight || 1;
      if (pointers.size < 2) {
        panX += (ev.clientX - prev.x) / cw;
        panY += (ev.clientY - prev.y) / ch;
      } else {
        var next = pair();
        if (pinch && next && pinch.dist > 1) {
          zoom *= next.dist / pinch.dist;
          var turn = next.angle - pinch.angle;
          if (turn > 180) turn -= 360;
          if (turn < -180) turn += 360;
          rotation += turn;
        }
        pinch = next;
      }
      paint();
    });
    function dropPointer(ev) {
      pointers.delete(ev.pointerId);
      pinch = pair();
    }
    well.addEventListener("pointerup", dropPointer);
    well.addEventListener("pointercancel", dropPointer);
    well.addEventListener("wheel", function (ev) {
      ev.preventDefault();
      var cw = well.clientWidth || 1;
      var ch = well.clientHeight || 1;
      if (ev.ctrlKey || ev.metaKey) zoom *= Math.exp(-ev.deltaY * 0.01);
      else if (ev.shiftKey) rotation += ev.deltaY * 0.15;
      else {
        panX -= ev.deltaX / cw;
        panY -= ev.deltaY / ch;
      }
      paint();
    }, { passive: false });
    window.addEventListener("resize", paint);

    loadOriginal(file).then(function (loaded) {
      img = loaded.img;
      objectUrl = loaded.url;
      ok.disabled = false;
      paint();
    }).catch(function () {
      fail("No se pudo leer la foto");
    });
  }

  function compress(file) {
    if (!file || !String(file.type || "").startsWith("image/")) return Promise.resolve(file);
    return loadOriginal(file).then(function (loaded) {
      var img = loaded.img;
      var iw = img.naturalWidth;
      var ih = img.naturalHeight;
      var edge = Math.max(iw, ih);
      var jpeg = file.type === "image/jpeg";
      if (jpeg && edge <= MAX_EDGE && file.size <= 2500000) {
        URL.revokeObjectURL(loaded.url);
        return file;
      }
      var scale = Math.min(1, MAX_EDGE / edge);
      var w = Math.max(1, Math.round(iw * scale));
      var h = Math.max(1, Math.round(ih * scale));
      var canvas = document.createElement("canvas");
      canvas.width = w;
      canvas.height = h;
      var ctx = canvas.getContext("2d");
      ctx.imageSmoothingEnabled = true;
      ctx.imageSmoothingQuality = "high";
      ctx.drawImage(img, 0, 0, w, h);
      URL.revokeObjectURL(loaded.url);
      return new Promise(function (resolve) {
        canvas.toBlob(function (blob) {
          if (!blob || (scale === 1 && blob.size >= file.size)) {
            resolve(file);
            return;
          }
          var name = String(file.name || "foto").replace(/\.\w+$/, "") + ".jpg";
          resolve(new File([blob], name, { type: "image/jpeg" }));
        }, "image/jpeg", JPEG);
      });
    }).catch(function () { return file; });
  }

  function fileFromOriginal(url) {
    return fetch(url, { credentials: "omit", cache: "no-store" }).then(function (response) {
      if (!response.ok) throw new Error("No se pudo leer la foto original");
      return response.blob().then(function (blob) {
        var name = decodeURIComponent(String(url).split("/").pop().split("?")[0] || "original.jpg");
        return new File([blob], name, { type: blob.type || "image/jpeg" });
      });
    });
  }

  window.OgtFrame = { open: open, compress: compress, fileFromOriginal: fileFromOriginal, aspect: ASPECT };
})();
