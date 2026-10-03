# Project website

The public introduction is at **https://magicsih.github.io/AndroidScreenCaster/**.
It introduces the Android sender and browser companion; the streaming receiver
still runs on the user's own trusted network.

## Source and deployment

`site/` is a standalone static website: HTML, CSS and a small JavaScript file.
There is no framework, package build or runtime server. GitHub Pages publishes
only this directory, not the repository or its validation evidence.

The `Project website` workflow checks desktop and mobile layouts, real image
loading, receiver tabs, keyboard input, command copying and its failure fallback,
demo activation, links and metadata. It saves screenshots and a JSON report.
The browser check mocks the YouTube player; it does not assert video decoding.

PRs run with read-only repository permissions and do not deploy. A successful
`master` check uploads the site and a separate job deploys with `pages: write`
and `id-token: write`. The `github-pages` environment restricts deployments to
the protected default branch. Actions are pinned to verified stable commit SHAs.

For local preview, serve the `site/` directory with an HTTP server. To run the
same checks as CI, use an isolated Python environment with
`tools/requirements-site.txt`, install Playwright Chromium, and run
`python tools/site_checks.py`. The test starts and stops its own local server.

## Content and asset sources

- `assets/original-demo.jpg` is the existing project's YouTube thumbnail,
  obtained from `https://img.youtube.com/vi/2AN6EfArfZE/maxresdefault.jpg`.
  The page labels this as an earlier-version demo and loads the privacy-enhanced
  YouTube embed only after a click. A direct YouTube link remains available.
- `assets/android-app.png` is the API 37 screenshot from
  `docs/images/screen-caster.png` in this repository.
- `assets/browser-viewer.png` is the API 36 physical-device screenshot from the
  companion's `docs/images/physical-viewer.png`.
- Manrope and IBM Plex Mono are self-hosted from
  [Google Fonts](https://github.com/google/fonts). Their SIL Open Font License
  texts are included alongside the font files. The favicon is project artwork.

Published capability and runtime claims follow the two repositories' validation
reports. Do not add unmeasured latency, audio, remote control, or universal device
compatibility claims.

## Google Search Console

Add **https://magicsih.github.io/AndroidScreenCaster/** as a **URL-prefix**
property in [Search Console](https://search.google.com/search-console).
Choose **HTML tag** verification and put the exact issued tag in the `<head>` of
`site/index.html`. Submit it through the normal PR flow, wait for the Pages
deployment, confirm the tag appears in the public page source, then select
**Verify** in Search Console. Keep the tag in place.

The published sitemap is
**https://magicsih.github.io/AndroidScreenCaster/sitemap.xml**. Submit that URL
in the property's Sitemaps screen, then inspect the homepage and request indexing.
Indexing and search ranking are not guaranteed. Verifying the Pages address does
not verify either `github.com` repository address.

No verification token is invented or included by default. Search Console setup
requires the token issued for the intended Google account and property.

- [Google ownership verification](https://support.google.com/webmasters/answer/9008080)
- [Google recrawl requests](https://developers.google.com/search/docs/crawling-indexing/ask-google-to-recrawl)
- [GitHub Pages custom workflows](https://docs.github.com/en/pages/getting-started-with-github-pages/using-custom-workflows-with-github-pages)
