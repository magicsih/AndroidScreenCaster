"""Check the public project page at its actual GitHub Pages subpath."""

import asyncio
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import re
import tempfile
from threading import Thread

from playwright.async_api import async_playwright, expect


async def check_page(page, url, capture):
    errors = []
    page.on("pageerror", lambda error: errors.append(str(error)))
    views = []
    for width in (1440, 768, 390, 320):
        await page.set_viewport_size({"width": width, "height": 900})
        response = await page.goto(url, wait_until="networkidle")
        assert response.status == 200
        await page.evaluate("document.fonts.ready")
        await expect(page.locator("h1")).to_have_text(re.compile(r"Your screen\.\s*Now streaming\."))
        assert not await page.evaluate("document.documentElement.scrollWidth > innerWidth"), width
        assert await page.locator("img").evaluate_all(
            "images => images.every(image => image.complete && image.naturalWidth > 0)"
        ), width
        assert await page.evaluate("document.fonts.check('16px Manrope')")
        views.append({"width": width, "screenshot": await capture(f"page-{width}.png", True)})

    # Receiver tabs must work with both pointer and keyboard input.
    browser_tab = page.get_by_role("tab", name="Browser / WebRTC")
    await browser_tab.click()
    await expect(page.locator("#panel-browser")).to_be_visible()
    await expect(page.locator("#panel-ffplay")).to_be_hidden()
    await browser_tab.press("ArrowLeft")
    await expect(page.get_by_role("tab", name="Desktop / FFplay")).to_be_focused()
    await expect(page.locator("#panel-ffplay")).to_be_visible()
    await page.get_by_role("tab", name="Desktop / FFplay").press("End")
    await expect(browser_tab).to_be_focused()
    await expect(page.locator("#panel-browser")).to_be_visible()

    # Exercise the full command-copy UI without writing to the host clipboard.
    await page.evaluate("""() => {
      window.copiedCommand = null;
      Object.defineProperty(navigator, 'clipboard', { configurable: true,
        value: { writeText: async text => { window.copiedCommand = text; } } });
    }""")
    await page.get_by_role("button", name="Copy browser server commands").click()
    await expect(page.get_by_role("status")).to_contain_text("Command copied")
    command = await page.evaluate("window.copiedCommand")
    assert "git clone https://github.com/magicsih/AndroidScreenCasterWeb.git" in command
    assert command.endswith("docker compose up -d")
    await page.evaluate("""() => {
      navigator.clipboard.writeText = async () => { throw new Error('Clipboard unavailable'); };
    }""")
    await page.get_by_role("button", name="Copy browser server commands").click()
    await expect(page.get_by_role("status")).to_contain_text("Command selected")
    assert await page.evaluate("window.getSelection().toString()") == command

    await page.get_by_role("tab", name="Desktop / FFplay").click()
    await page.get_by_role("button", name="Copy FFplay command").click()
    assert "tcp://0.0.0.0:49152?listen=1" in await page.evaluate("window.getSelection().toString()")
    await page.locator("details summary").first.click()
    await expect(page.locator("details").first).to_have_attribute("open", "")

    hrefs = await page.locator("a[href]").evaluate_all("links => links.map(link => link.getAttribute('href'))")
    for link in (
        "https://github.com/magicsih/AndroidScreenCaster",
        "https://github.com/magicsih/AndroidScreenCasterWeb",
        "https://github.com/magicsih/AndroidScreenCaster/releases/tag/v0.2.0",
        "https://www.youtube.com/watch?v=2AN6EfArfZE",
    ):
        assert link in hrefs, link
    for href in hrefs:
        if href.startswith("#") and len(href) > 1:
            assert await page.locator(href).count() == 1, href
    structured_data = json.loads(await page.locator('script[type="application/ld+json"]').text_content())
    assert structured_data["softwareVersion"] == "0.2.0"
    canonical = "https://magicsih.github.io/AndroidScreenCaster/"
    assert await page.locator('link[rel="canonical"]').get_attribute("href") == canonical

    # The initial page must not load YouTube. Playback is requested by a click.
    assert await page.locator("iframe").count() == 0
    await page.route("https://www.youtube-nocookie.com/**", lambda route: route.fulfill(
        status=200, content_type="text/html", body="<html><title>Test player</title></html>"
    ))
    await page.locator("#play-demo").click()
    await expect(page.locator("iframe")).to_have_attribute(
        "src", "https://www.youtube-nocookie.com/embed/2AN6EfArfZE?autoplay=1&rel=0"
    )
    await expect(page.locator("iframe")).to_have_attribute(
        "title", "Original AndroidScreenCaster demonstration on YouTube"
    )
    assert not errors, errors
    return {"views": views, "receiver_tabs": "passed", "command_copy_and_fallback": "passed",
            "demo_click": "passed (player mocked; no video decoding assertion)",
            "links_and_metadata": "passed", "page_errors": errors}


class QuietHandler(SimpleHTTPRequestHandler):
    def log_message(self, *_args):
        pass


async def main():
    root = Path(__file__).resolve().parents[1]
    output = root / "artifacts/pages"
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as directory:
        (Path(directory) / "AndroidScreenCaster").symlink_to(root / "site", target_is_directory=True)
        server = ThreadingHTTPServer(("127.0.0.1", 0), partial(QuietHandler, directory=directory))
        thread = Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            async with async_playwright() as playwright:
                browser = await playwright.chromium.launch()
                page = await browser.new_page()

                async def capture(name, full_page):
                    await page.screenshot(path=str(output / name), full_page=full_page)
                    return name

                report = await check_page(page, f"http://127.0.0.1:{server.server_port}/AndroidScreenCaster/", capture)
                (output / "report.json").write_text(json.dumps(report, indent=2) + "\n")
                print(json.dumps(report))
                await browser.close()
        finally:
            server.shutdown()
            server.server_close()
            thread.join()


if __name__ == "__main__":
    asyncio.run(main())
