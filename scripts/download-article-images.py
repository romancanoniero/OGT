#!/usr/bin/env python3
"""Baja 1–2 fotos reales de cada noticia (og:image / twitter:image / img del artículo)."""
from __future__ import annotations

import html
import json
import re
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path

DEST = Path("/Users/romancanoniero/.cursor/OnlyGoodThings/composeApp/src/commonMain/composeResources/drawable")
UA = (
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0.0.0 Safari/537.36"
)

# (out_stem, article_url)
ITEMS = [
    ("seed_n01", "https://tn.com.ar/sociedad/2026/09/16/una-especie-desaparecio-durante-150-anos-y-ahora-158-animales-vuelven-a-una-isla-gracias-a-datos-de-la-nasa/"),
    ("seed_n02", "https://radiomasmontecaseros.com/rescatan-y-liberan-a-dos-lobos-marinos-tras-340-kilometros-de-rehabilitacion/"),
    ("seed_n03", "https://www.pulzo.com/vivir-bien/mascotas/adopcion-en-bogota-la-emotiva-recuperacion-de-pirata-patria-con-el-idpyba-PP5193405A"),
    ("seed_n04", "https://www.dossierdeprensa.mx/impulsa-brugada-adopcion-responsable-de-animales-rescatados-inicia-proceso-con-plataforma-digital/"),
    ("seed_n05", "https://rdnewsnow.com/2026/09/14/restoration-volunteers-plant-250-trees-shrubs-at-kerry-wood-nature-centre/"),
    ("seed_n06", "https://www.hurunui.govt.nz/council/news?item=id%3A2yn0ol03217q9sv1iv7n"),
    ("seed_n07", "https://jkcommunityfarm.org/in-the-news/volunteers-plant-60k-seedlings-at-jk-community-farm/"),
    ("seed_n08", "https://patch.com/virginia/reston/food-neighbors-volunteers-collect-nearly-30-000-pounds-fight-teen-hunger"),
    ("seed_n09", "https://www.ucanr.edu/site/f3-local-farm-food-innovation/article/offer-kindness-rescuing-food-and-feeding-community"),
    ("seed_n10", "https://mongabay1.substack.com/p/whale-sharks-released-from-nets-along"),
    ("seed_n11", "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/"),
    ("seed_n12", "https://asiatoday.id/read/indonesias-blue-economy-drive-protects-ocean-biodiversity"),
    ("seed_n13", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece"),
    ("seed_n14", "https://indianmasterminds.com/news/didi-ki-rasoi-bihar-229956/"),
    ("seed_n15", "https://www.adnsur.com.ar/sociedad/-manos-a-la-costa---mas-de-30-vecinos-limpiaron-la-playa-de-kilometro-5-y-retiraron-5-000-litros-de-basura_a6a7f5c764a152b98eeb6618f"),
    ("seed_n16", "https://cordoba.gob.ar/passerini-entrego-mobiliario-a-comedores-y-merenderos-comunitarios-fabricado-con-scrap-de-industrias-cordobesas/"),
    ("seed_n17", "https://www.vocescriticas.com/noticias/2026/06/12/204133-en-homenaje-a-tomas-arias-impulsan-un-merendero-y-preparan-una-jornada-solidaria-en-barrio-gemes"),
    ("seed_n18", "https://eldesconcierto.cl/hoja-ruta/diez-anos-huertas-comunitarias-110-proyectos-y-casi-400000-kilos-alimentos-cosechados-n5462049"),
    ("seed_n19", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/"),
    ("seed_n20", "https://www.milenio.com/comunidad/cereza-cerecito-perritos-sobrevivientes-iztapalapa-regresan-a-hogar"),
    ("seed_n21", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/"),
    ("seed_n22", "https://thesun.my/news/malaysia-news/people-issues/500-green-sea-turtle-hatchlings-released-in-kertih/"),
    ("seed_n23", "https://timesofindia.indiatimes.com/science/meet-the-turtle-women-of-amazon-7-indigenous-women-are-bringing-vulnerable-river-turtles-back-from-the-brink-after-releasing-208-baby-turtles-into-the-wild/articleshow/133726609.cms"),
    ("seed_n24", "https://www.diariodealicante.net/en/sea-%E2%80%8B%E2%80%8Bturtle-hatchlings-born-Muchavista-Campello/"),
    ("seed_n25", "https://www.wvtf.org/news/2026-09-04/rva-community-fridges-opens-its-17th-free-fridge-and-it-cant-keep-them-full"),
    ("seed_n26", "https://dondeir.com/mascotas/cdmx-estrena-plataforma-para-adoptar-perros-rescatados-asi-funciona/2026/09/"),
    ("seed_n27", "https://www.semana.com/4patas/perros/articulo/en-video-la-historia-de-pirata-patria-el-perro-que-fue-rescatado-en-un-estado-de-salud-grave-y-duro-casi-dos-anos-sin-hogar/202625/"),
    ("seed_n28", "https://www.cronica.com.mx/metropoli/2026/09/14/regresan-156-perros-al-refugio-franciscano-cdmx-inicia-programa-de-adopcion-responsable/"),
    ("seed_n29", "https://www.upi.com/Top_News/World-News/2026/09/01/latam-mexico-sea-turtles-released/1251788286661/"),
    ("seed_n30", "https://www.eluniversal.com.co/cartagena/2026/02/24/limpieza-y-rescate-de-mangles-la-doble-jornada-de-global-shapers-en-la-boquilla/"),
    ("seed_n31", "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/"),
    ("seed_n32", "https://infodelestero.com/2026/09/15/un-merendero-santiagueno-pide-ayuda-para-seguir-acompanando-a-70-ninos-cada-miercoles"),
    ("seed_n33", "https://elmegafono.uy/fuego-solidario-cocinar-para-acompanar-y-visibilizar-el-trabajo-de-los-merenderos/"),
    ("seed_n34", "https://www.wftv.com/news/local/winter-park-nonprofit-transports-12000-animals-florida-rescues/QBDFXKCZDRBBDPOQ2GV27PIU4E/"),
    ("seed_n35", "https://thebaynet.com/shell-yeah-volunteers-wanted-to-plant-1-million-oysters-in-breton-bay/"),
    ("seed_n36", "https://volunteercleanup.org/event/rickenbacker-marina-with-rescue-a-reef-icc2026"),
    ("seed_n37", "https://www.thehindu.com/news/national/telangana/8-crore-centralised-kitchen-opened-in-karimnagar-to-serve-breakfast-to-49025-students/article71472979.ece"),
    ("seed_n38", "https://coralrestoration.org/celebrating-coralpalooza-2026-around-the-world/"),
    ("seed_n39", "https://veonoticias.com/santa-fe-limpieza-reforestacion-y-mantenimiento-en-el-bajo-orono-y-el-parque-federal/"),
    ("seed_n40", "https://volunteercleanup.org/event/2026-international-coastal-cleanup-at-biscayne-national-park"),
]

# Segundas fotos para carruseles (misma nota, otra imagen del HTML).
SECONDS = {"seed_n01", "seed_n02", "seed_n03", "seed_n07", "seed_n10", "seed_n11", "seed_n15", "seed_n20", "seed_n23", "seed_n28"}

SKIP_RE = re.compile(
    r"logo|sprite|icon|favicon|avatar|pixel|1x1|tracking|adservice|doubleclick|gravatar|emoji|badge|button|share",
    re.I,
)


def fetch(url: str, timeout: int = 25) -> tuple[bytes, str]:
    req = urllib.request.Request(
        url,
        headers={
            "User-Agent": UA,
            "Accept": "text/html,application/xhtml+xml,image/avif,image/webp,image/*,*/*;q=0.8",
            "Accept-Language": "es-AR,es;q=0.9,en;q=0.8",
        },
    )
    with urllib.request.urlopen(req, timeout=timeout) as resp:
        return resp.read(), resp.geturl()


def abs_url(base: str, raw: str) -> str | None:
    raw = html.unescape((raw or "").strip().strip("\"'"))
    if not raw or raw.startswith("data:"):
        return None
    return urllib.parse.urljoin(base, raw)


def meta_images(page: str, base: str) -> list[str]:
    found: list[str] = []
    patterns = [
        r'<meta[^>]+property=["\']og:image(?::secure_url)?["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+property=["\']og:image(?::secure_url)?["\']',
        r'<meta[^>]+name=["\']twitter:image(?::src)?["\'][^>]+content=["\']([^"\']+)["\']',
        r'<meta[^>]+content=["\']([^"\']+)["\'][^>]+name=["\']twitter:image(?::src)?["\']',
        r'<link[^>]+rel=["\']image_src["\'][^>]+href=["\']([^"\']+)["\']',
        r'"image"\s*:\s*"([^"]+)"',
        r'"image"\s*:\s*\{\s*"url"\s*:\s*"([^"]+)"',
        r'"contentUrl"\s*:\s*"([^"]+)"',
        r'<img[^>]+(?:src|data-src|data-lazy-src|data-original)=["\']([^"\']+)["\']',
    ]
    for pat in patterns:
        for match in re.finditer(pat, page, re.I):
            url = abs_url(base, match.group(1))
            if url and not SKIP_RE.search(url) and url not in found:
                found.append(url)
    return found


def looks_like_image(data: bytes) -> bool:
    if data[:3] == b"\xff\xd8\xff":
        return True
    if data[:8] == b"\x89PNG\r\n\x1a\n":
        return True
    if data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        return True
    if data[:6] in (b"GIF87a", b"GIF89a"):
        return True
    return False


def to_jpeg(src: Path, dest: Path) -> bool:
    try:
        subprocess.run(
            ["sips", "-s", "format", "jpeg", "-s", "formatOptions", "80", str(src), "--out", str(dest)],
            check=True,
            capture_output=True,
        )
        return dest.exists() and dest.stat().st_size > 8_000
    except Exception:
        return False


def save_image(url: str, dest: Path) -> bool:
    try:
        data, _ = fetch(url, timeout=30)
    except Exception as exc:
        print(f"  FAIL download {url[:90]} ({exc})")
        return False
    if not looks_like_image(data) or len(data) < 8_000:
        print(f"  SKIP not-image {url[:90]} ({len(data)} bytes)")
        return False
    tmp = dest.with_suffix(".tmpbin")
    tmp.write_bytes(data)
    ok = to_jpeg(tmp, dest)
    tmp.unlink(missing_ok=True)
    if ok:
        print(f"  OK {dest.name} <- {url[:110]}")
    else:
        print(f"  FAIL convert {url[:90]}")
    return ok


def main() -> None:
    DEST.mkdir(parents=True, exist_ok=True)
    ok = 0
    fail = 0
    for i, (stem, article) in enumerate(ITEMS, start=1):
        print(f"[{i:02d}/40] {stem} {article}")
        try:
            page, final = fetch(article)
            text = page.decode("utf-8", errors="ignore")
        except Exception as exc:
            print(f"  FAIL page {exc}")
            fail += 1
            time.sleep(1.2)
            continue
        images = meta_images(text, final)
        print(f"  candidates={len(images)}")
        saved = 0
        primary = DEST / f"{stem}.jpg"
        if images and save_image(images[0], primary):
            saved += 1
            ok += 1
        else:
            fail += 1
        if stem in SECONDS:
            extra = DEST / f"{stem}b.jpg"
            second = next((u for u in images[1:] if u != images[0]), None) if images else None
            if second and save_image(second, extra):
                saved += 1
        time.sleep(1.1)
    print(f"DONE primary_ok={ok} primary_fail={fail}")


if __name__ == "__main__":
    main()
