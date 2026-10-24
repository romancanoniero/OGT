/** Load same-origin public assets; never render an HTTP error as an icon sprite. */
(function () {
  "use strict";

  function showFailure(message) {
    const shell = document.getElementById("app-shell");
    if (shell) shell.hidden = true;
    let alert = document.getElementById("ogt-load-error");
    if (!alert) {
      alert = document.createElement("section");
      alert.id = "ogt-load-error";
      alert.setAttribute("role", "alert");
      document.body.prepend(alert);
    }
    alert.textContent = message;
  }

  // A stale server may return the public index for a missing CRM directory.
  // Do not initialize the public application or suggest this is a login failure.
  if (/^\/crm(?:\/|$)/.test(location.pathname)) {
    showFailure("El servidor devolvio la web publica en lugar del CRM. La publicacion de Control y Empresas no esta completa en esta instalacion. No es un error de tu cuenta.");
    return;
  }

  async function loadIcons() {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 8000);
    try {
      const response = await fetch("/icons/qs.svg?v=lupa2", {
        credentials: "same-origin",
        signal: controller.signal,
      });
      if (!response.ok) throw new Error("Icon resource returned HTTP " + response.status);
      const type = (response.headers.get("content-type") || "").split(";", 1)[0].trim().toLowerCase();
      if (type !== "image/svg+xml") throw new Error("Icon resource is not SVG");
      const xml = new DOMParser().parseFromString(await response.text(), "image/svg+xml");
      const svg = xml.documentElement;
      if (xml.querySelector("parsererror") || !svg || svg.localName !== "svg" ||
          svg.namespaceURI !== "http://www.w3.org/2000/svg") {
        throw new Error("Invalid SVG icon resource");
      }
      // The sprite is a versioned same-origin asset, never user-uploaded markup.
      const target = document.getElementById("icons");
      if (target) target.replaceChildren(document.importNode(svg, true));
    } finally {
      clearTimeout(timeout);
    }
  }

  function loadScript(src) {
    return new Promise((resolve, reject) => {
      const script = document.createElement("script");
      const timeout = setTimeout(() => finish(new Error("Timed out loading " + src)), 20000);
      let settled = false;
      function finish(error) {
        if (settled) return;
        settled = true;
        clearTimeout(timeout);
        script.onload = null;
        script.onerror = null;
        if (error) {
          script.remove();
          reject(error);
        } else {
          resolve();
        }
      }
      script.onload = () => finish();
      script.onerror = () => finish(new Error("Could not load " + src));
      script.src = src;
      document.body.appendChild(script);
    });
  }

  // Icons are optional decoration: a failed icon request cannot block startup.
  loadIcons().catch(error => console.warn("OGT: icons unavailable", error.message));
  loadScript("/ogt-sdk.js?v=frame-size")
    .then(() => loadScript("/app.js?v=baja"))
    .catch(error => {
      console.error("OGT: application startup failed", error.message);
      showFailure("No se pudieron cargar los archivos de OGT. La instalacion puede estar incompleta o la conexion se interrumpio. No se puede iniciar la aplicacion.");
    });
})();
