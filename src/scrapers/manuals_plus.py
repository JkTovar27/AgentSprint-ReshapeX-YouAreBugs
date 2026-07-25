import argparse
import base64
import json
import logging
import re
import time
from pathlib import Path
from typing import Optional
from urllib.parse import urljoin

from bs4 import BeautifulSoup
from playwright.sync_api import sync_playwright

logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
logger = logging.getLogger(__name__)

CATEGORY_URL = "https://manuals.plus/es/category/sick"
PDF_API = "https://manuals.plus/m/{hash}.pdf"
DELAY = 2.0


def _start_browser():
    p = sync_playwright()
    pt = p.start()
    browser = pt.chromium.launch(headless=False)
    page = browser.new_page()
    return p, pt, browser, page


def _close_browser(p, pt, browser):
    try:
        browser.close()
    except Exception:
        pass
    try:
        pt.stop()
    except Exception:
        pass
    try:
        p.__exit__(None, None, None)
    except Exception:
        pass


def _bypass_cloudflare(page, url: str, timeout: int = 60):
    page.goto(url, timeout=timeout * 1000)
    time.sleep(10)
    html = page.content()
    if "security check" in html.lower():
        raise RuntimeError(f"Cloudflare bloqueó {url}")
    return html


def _get_soup(html: str) -> BeautifulSoup:
    return BeautifulSoup(html, "html.parser")


def _get_total_from_soup(soup: BeautifulSoup) -> int:
    page_links = set()
    for a in soup.select("a[href*='/page/']"):
        m = re.search(r'/page/(\d+)', a.get("href", ""))
        if m:
            page_links.add(int(m.group(1)))
    return max(page_links) if page_links else 1


def parse_listing(soup: BeautifulSoup) -> list[dict]:
    manuals = []
    articles = soup.find_all("article", class_="manual-card")
    if not articles:
        articles = soup.find_all("article")

    for art in articles:
        title_el = art.select_one("h3.manual-card-title a, h3 a")
        if not title_el:
            continue
        source_url = urljoin("https://manuals.plus", title_el.get("href", ""))
        title = title_el.get_text(strip=True)
        if not title:
            continue

        date_el = art.select_one(".manual-card-meta")
        date_text = date_el.get_text(strip=True) if date_el else ""

        desc_el = art.select_one(".manual-card-desc")
        description = desc_el.get_text(" ", strip=True) if desc_el else ""

        url_path = source_url.replace("https://manuals.plus", "")

        pdf_url = None
        doc_type = "other"
        if "/m/" in url_path:
            hash_val = url_path.split("/m/")[-1].split("/")[0].split("?")[0]
            if re.match(r'^[0-9a-f]{64}$', hash_val):
                pdf_url = f"https://manuals.plus/m/{hash_val}.pdf"
                doc_type = "uploaded"
        elif "/asin/" in url_path:
            doc_type = "amazon"
        elif "/ae/" in url_path:
            doc_type = "aliexpress"
        elif "/sick/" in url_path:
            doc_type = "seo"

        model = ""
        model_match = re.search(r'(?:SICK|ENFERMO)\s+([A-Za-z0-9][A-Za-z0-9./-]+)', title)
        if not model_match:
            model_match = re.search(r'\b([A-Z0-9]{2,}[\w-]*\d[\w-]*)\b', title)
        if model_match:
            model = model_match.group(1)

        manuals.append({
            "title": title,
            "model": model,
            "date": date_text,
            "description": description,
            "source_url": source_url,
            "pdf_url": pdf_url,
            "type": doc_type,
        })

    return manuals


def download_pdf(page, pdf_url: str) -> Optional[bytes]:
    try:
        result = page.evaluate("""(url) => {
            return new Promise((resolve, reject) => {
                fetch(url)
                    .then(r => {
                        if (!r.ok) throw new Error('HTTP ' + r.status);
                        return r.blob();
                    })
                    .then(blob => {
                        const reader = new FileReader();
                        reader.onload = () => resolve(reader.result);
                        reader.readAsDataURL(blob);
                    })
                    .catch(e => reject(e.message));
            });
        }""", pdf_url)

        if result and "," in result:
            b64 = result.split(",", 1)[1]
            return base64.b64decode(b64)
    except Exception as e:
        logger.warning("  Error descargando PDF %s: %s", pdf_url, e)
    return None


def extract_pdf_text(pdf_bytes: bytes) -> str:
    try:
        import fitz
        doc = fitz.open(stream=pdf_bytes, filetype="pdf")
        text = "\n".join(page.get_text() for page in doc)
        doc.close()
        return text.strip()
    except ImportError:
        logger.warning("PyMuPDF no instalado, omitiendo extracción de texto")
        return ""
    except Exception as e:
        logger.warning("Error extrayendo texto PDF: %s", e)
        return ""


def _navigate_to_page(page, url: str):
    """Navega a una URL usando location.href para evitar gatillar Cloudflare."""
    page.evaluate(f"window.location.href = '{url}'")
    time.sleep(10)


def scrape_sick_manuals(
    max_pages: int = 0,
    output: str = "data/raw/sick_manuals.json",
    download_pdfs: bool = False,
    delay: float = DELAY,
) -> list[dict]:
    p, pt, browser, page = _start_browser()
    all_manuals = []

    try:
        html = _bypass_cloudflare(page, CATEGORY_URL)
        soup = _get_soup(html)
        total = _get_total_from_soup(soup)
        pages = min(max_pages, total) if max_pages > 0 else total
        logger.info("Scraping %d páginas de %d", pages, total)

        for page_num in range(1, pages + 1):
            if page_num > 1:
                url = f"{CATEGORY_URL}/page/{page_num}"
                logger.info("Página %d/%d: %s", page_num, pages, url)
                try:
                    _navigate_to_page(page, url)
                    html = page.content()
                except Exception as e:
                    logger.error("Error navegando a página %d: %s", page_num, e)
                    continue
            else:
                logger.info("Página 1/%d", pages)

            soup = _get_soup(html)
            entries = parse_listing(soup)
            logger.info("  → %d entradas", len(entries))

            for entry in entries:
                logger.info("  Procesando: %s", entry["title"][:80])

                if entry["type"] == "uploaded" and entry.get("pdf_url") and download_pdfs:
                    logger.info("  PDF disponible: %s", entry["pdf_url"])
                    pdf_bytes = download_pdf(page, entry["pdf_url"])
                    if pdf_bytes and len(pdf_bytes) > 1000:
                        text = extract_pdf_text(pdf_bytes)
                        entry["text"] = text
                        logger.info("  PDF descargado y texto extraído (%d caracteres)", len(text))
                    else:
                        entry["text"] = ""
                        logger.warning("  PDF no válido o vacío")
                elif entry["type"] == "uploaded" and entry.get("pdf_url"):
                    entry["text"] = ""
                    logger.info("  PDF disponible (usar --download para extraer texto): %s", entry["pdf_url"])
                elif entry["type"] == "seo":
                    entry["text"] = entry.get("description", "")
                    logger.info("  Sin PDF, usando descripción del listado")
                else:
                    entry["text"] = ""
                    logger.info("  Sin PDF (tipo: %s)", entry["type"])

                all_manuals.append(entry)
                time.sleep(delay)

            if page_num < pages:
                time.sleep(delay)

    finally:
        _close_browser(p, pt, browser)

    if output:
        path = Path(output)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(
            json.dumps(all_manuals, ensure_ascii=False, indent=2),
            encoding="utf-8",
        )
        logger.info("Datos guardados en %s (%d entradas)", path, len(all_manuals))

    return all_manuals


def main():
    parser = argparse.ArgumentParser(
        description="Scraper de manuales SICK en manuals.plus"
    )
    parser.add_argument("--max-pages", type=int, default=0,
                        help="Máximo de páginas (0 = todas)")
    parser.add_argument("--output", default="data/raw/sick_manuals.json",
                        help="Archivo JSON de salida")
    parser.add_argument("--download", action="store_true",
                        help="Descargar PDFs y extraer texto")
    parser.add_argument("--delay", type=float, default=DELAY,
                        help="Segundos entre requests")
    args = parser.parse_args()

    data = scrape_sick_manuals(
        max_pages=args.max_pages,
        output=args.output,
        download_pdfs=args.download,
        delay=args.delay,
    )

    logger.info("Resumen: %d manuales extraídos", len(data))
    uploaded = sum(1 for d in data if d["type"] == "uploaded")
    seo = sum(1 for d in data if d["type"] == "seo")
    other = sum(1 for d in data if d["type"] in ("amazon", "aliexpress"))
    with_pdf = sum(1 for d in data if d.get("pdf_url"))
    with_text = sum(1 for d in data if d.get("text"))
    logger.info("  Uploaded: %d, SEO: %d, Amazon/AE: %d", uploaded, seo, other)
    logger.info("  Con PDF URL: %d, Con texto extraído: %d", with_pdf, with_text)


if __name__ == "__main__":
    main()
