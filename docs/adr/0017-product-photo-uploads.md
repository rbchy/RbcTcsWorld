# ADR 0017: Product photos uploaded by the owner, re-encoded by the backend and kept in the database

- **Status:** Accepted
- **Date:** 2026-10 (after store Phase 5; the "Products" row of the roadmap)

## Context
Until now a product picture was either a file baked into the frontend build (`/products/<name>.svg`) or an `https://`
link. Neither works for a real store: the owner cannot rebuild the frontend for every new product, and a link to
someone else's server can break, change or track visitors. The owner needs to take a photo with a phone and put it
on the product page.

An uploaded file is also the classic way into a web server: a script disguised as a picture, an SVG with JavaScript,
a "decompression bomb" (a few kB that unpack to gigabytes), and phone photos that carry the GPS position of the
owner's home in their EXIF data.

## Decision
- **One endpoint, admin only:** `POST /api/products/pictures` (multipart, field `file`). It returns the address of the
  stored picture; the admin form puts it into the product's ordinary `imageUrl`, so products, the audit trail and
  older clients do not change. `ProductRequest.imageUrl` accepts exactly `/api/products/pictures/<sha256>.jpg|png`.
- **Nothing of the upload is kept but its pixels** (`PictureProcessor`, plain JDK `ImageIO`, no new dependency):
  JPEG and PNG only, recognised by their first bytes (not by name or content type); size read from the header before
  decoding (at most 50 megapixels, at least 200 px on the short side, at most 10 MB); big photos decoded with
  subsampling; turned upright by their EXIF orientation; scaled to at most 1600 px; drawn again and saved as a new
  JPEG (quality 0.86), or PNG when really transparent. EXIF, GPS, comments and anything hidden after the image are
  gone. SVG is never accepted. At most two photos are decoded at a time (memory); a third upload gets 429.
- **The name is the content:** the file name is the SHA-256 of the stored bytes. The same photo uploaded twice is
  stored once (200 instead of 201), and the address can be cached for a year (`Cache-Control: immutable`) because it
  never shows anything else.
- **Stored in PostgreSQL** (`product_pictures`, `bytea`, migration V27), read and written with plain JDBC. A small shop
  has tens to hundreds of photos of a few hundred kB; the daily encrypted backup, the off-site copy and the restore
  drill (ADR-0016) cover them with no extra step, and `product_pictures` is one of the tables the drill counts.
- **Served by the backend** at `GET /api/products/pictures/<name>`, public, with ETag/304 and the API's strict CSP
  and `nosniff` headers. Any name that is not 64 hex digits plus the picture's own extension is 404.
- **The browser helps, the server decides:** the admin page converts WebP and HEIC (where the browser can read it)
  to JPEG and shrinks photos above 2400 px before uploading, so a 12 MB phone photo uploads quickly on mobile data.
  The server does all the checks again; a script calling the API directly gets the same treatment.
- **Request size:** 1 MB stays the limit everywhere (Caddy, nginx), except this one admin path: 11 MB, so a 10 MB
  photo with its multipart framing passes. Above the limit the API answers 413.
- **Public demo:** uploads are off (400 with a clear message, and the demo build shows a note instead of the
  button), for the same reason outside links are off there (ADR-0009): anybody can be the demo's admin.

## Consequences
- No new service, no bucket, no new library; the backup story stays one database dump.
- The database grows with every photo and an unused upload is never deleted. At this shop's size that is a few MB a
  year. If the catalog grows to thousands of photos, the next step is an S3 bucket behind the same address and a
  clean-up of pictures no product uses.
- Re-encoding costs a little quality and turns a photo into a JPEG even when the owner uploaded a PNG without
  transparency; that is the price of never storing what someone else wrote.
- CMYK JPEGs (some print workflows) cannot be read by the JDK and are refused with "cannot be read"; the owner saves
  them again as normal JPEG.
- Proven by `PictureProcessorTest`, `ProductPictureServiceTest`, `ProductPictureIntegrationTest`, the demo test,
  `playwright/tests/product-photos.spec.ts` and the CI job "Production stack", which uploads a 4 MB photo through
  Caddy and nginx, backs it up and restores it.
