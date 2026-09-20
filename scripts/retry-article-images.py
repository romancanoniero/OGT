#!/usr/bin/env python3
"""Reintenta las notas que no bajaron: acepta cualquier binario que sips pueda pasar a JPEG."""
from __future__ import annotations

import html
import re
import subprocess
import time
import urllib.parse
import urllib.request
from pathlib import Path

DEST = Path("/Users/romancanoniero/.cursor/OnlyGoodThings/composeApp/src/commonMain/composeResources/drawable")
UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
)

RETRY = [
    ("seed_n01", "https://tn.com.ar/sociedad/2026/09/16/una-especie-desaparecio-durante-150-anos-y-ahora-158-animales-vuelven-a-una-isla-gracias-a-datos-de-la-nasa/", True),
    ("seed_n04", "https://www.dossierdeprensa.mx/impulsa-brugada-adopcion-responsable-de-animales-rescatados-inicia-proceso-con-plataforma-digital/", False),
    ("seed_n09", "https://www.ucanr.edu/site/f3-local-farm-food-innovation/article/offer-kindness-rescuing-food-and-feeding-community", False),
    ("seed_n11", "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/", True),
    ("seed_n18", "https://eldesconcierto.cl/hoja-ruta/diez-anos-huertas-comunitarias-110-proyectos-y-casi-400000-kilos-alimentos-cosechados-n5462049", False),
    ("seed_n19", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/", False),
    ("seed_n20", "https://www.milenio.com/comunidad/cereza-cerecito-perritos-sobrevivientes-iztapalapa-regresan-a-hogar", True),
    ("seed_n21", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/", False),
    ("seed_n23", "https://timesofindia.indiatimes.com/science/meet-the-turtle-women-of-amazon-7-indigenous-women-are-bringing-vulnerable-river-turtles-back-from-the-brink-after-releasing-208-baby-turtles-into-the-wild/articleshow/133726609.cms", True),
    ("seed_n27", "https://www.semana.com/4patas/perros/articulo/en-video-la-historia-de-pirata-patria-el-perro-que-fue-rescatado-en-un-estado-de-salud-grave-y-duro-casi-dos-anos-sin-hogar/202625/", False),
    ("seed_n28", "https://www.cronica.com.mx/metropoli/2026/09/14/regresan-156-perros-al-refugio-franciscano-cdmx-inicia-programa-de-adopcion-responsable/", True),
    ("seed_n29", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/", False),
    ("seed_n30", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/", False),
    ("seed_n34", "https://www.wftv.com/news/local/winter-park-nonprofit-transports-12000-animals-florida-rescues/QBDFXKCZDRBBDPOQ2GV27PIU4E/", False),
    ("seed_n13", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece", False),
    ("seed_n37", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece", False),
]

ALT_PAGES = {
    "seed_n09": [
        "https://www.ucanr.edu/sites/default/files/2024-07/Offer%20Kindness.jpg",
    ],
    "seed_n21": [
        "https://cdn.upitn.com/images/2026/09/01/mexico-sea-turtles.jpg",
        "https://cdnph.upi.com/sv/ph/og/i/1251788286661/2026/1/1/1251788286661/v1.5/latam-mexico-sea-turtles-released.jpg",
    ],
}

SKIP_RE = re.compile(
    r"logo|sprite|icon|favicon|avatar|pixel|1x1|tracking|adservice|doubleclick|gravatar|emoji|badge|button|share|scorecard|og-image\.png|700x180|wp-includes",
    re.I,
)


def fetch(url: str, timeout: int = 28) -> tuple[bytes, str]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "*/*",
            "Accept-Language": "es-AR,es;q=0.9,en;q=0.8",
            "Referer": urllib.parse.urljoin(url, "/"),
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read(), resp.geturl()


def abs_url(base: str, raw: str) -> str | None:
    raw = html.unescape((raw or "").strip().strip("\"'"))
    if not raw or raw.startswith("data:"):
        return None
    if raw.startswith("//"):
        raw = "https:" + raw
    return urllib.parse.urljoin(base, raw)


def candidates(page: str, base: str) -> list[str]:
    found: list[str] = []
    pats = [
        r'<meta[^>]+property=["\']og:image(?::url|:secure_url)?["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image',
        r'<meta[^>]+name=["\']twitter:image["\'][^>]+content=["\']([^"\']+)["\']',
        r'"image"\s*:\s*"([^"]+)"',
        r'"url"\s*:\s*"(https?://[^"]+\.(?:jpe?g|png|webp|gif)[^"]*)"',
        r'<img[^>]+(?:src|data-src|data-lazy-src|data-original)=["\']([^"\']+)["\']',
        r'srcset=["\']([^"\']+)["\']',
    ]
    for pat in pats:
        for match in re.finditer(pat, page, re.I):
            blob = match.group(1)
            parts = [p.strip().split(" ")[0] for p in blob.split(",")]
            for part in parts:
                url = abs_url(base, part)
                if url and not SKIP_RE.search(url) and url not in found:
                    found.append(url)
    return found


def to_jpeg(src: Path, dest: Path) -> bool:
    try:
        subprocess.run(
            ["sips", "-s", "format", "jpeg", "-s", "formatOptions", "82", str(src), "--out", str(dest)],
            check=True,
            capture_output=True,
        )
        return dest.exists() and dest.stat().st_size > 6_000
    except Exception:
        return False


def save_image(url: str, dest: Path) -> bool:
    try:
        data, _ = fetch(url, timeout=30)
    except Exception as exc:
        print(f"  FAIL {url[:100]} ({exc})")
        return False
    if len(data) < 6_000:
        print(f"  SKIP tiny {len(data)} {url[:90]}")
        return False
    tmp = dest.with_suffix(".bin")
    tmp.write_bytes(data)
    kind = subprocess.run(["file", "-b", str(tmp)], capture_output=True, text=True).stdout.strip()
    ok = to_jpeg(tmp, dest)
    tmp.unlink(missing_ok=True)
    if ok:
        print(f"  OK {dest.name} ({kind[:60]}) <- {url[:110]}")
    else:
        print(f"  FAIL convert ({kind[:80]}) {url[:90]}")
    return ok


def main() -> None:
    # Si TN dejó la foto en n01b, copiala a n01 como primario.
    n01b = DEST / "seed_n01b.jpg"
    n01 = DEST / "seed_n01.jpg"
    if n01b.exists() and n01b.stat().st_size > 80_000:
        n01.write_bytes(n01b.read_bytes())
        print("copied seed_n01b -> seed_n01 as temporary primary")

    for stem, article, want_second in RETRY:
        print(f"RETRY {stem}")
        urls: list[str] = []
        try:
            page, final = fetch(article)
            urls = candidates(page.decode("utf-8", errors="ignore"), final)
            print(f"  candidates={len(urls)}")
        except Exception as exc:
            print(f"  FAIL page {exc}")
        urls = ALT_PAGES.get(stem, []) + urls
        primary = DEST / f"{stem}.jpg"
        extra = DEST / f"{stem}b.jpg"
        got_primary = False
        got_second = False
        for url in urls:
            if not got_primary and save_image(url, primary):
                got_primary = True
                if not want_second:
                    break
                continue
            if want_second and got_primary and not got_second and save_image(url, extra):
                got_second = True
                break
        print(f"  result primary={got_primary} second={got_second}")
        time.sleep(0.8)


if __name__ == "__main__":
    main()
