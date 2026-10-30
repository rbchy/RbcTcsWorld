# ADR 0021: Product descriptions in the owner's own words, as plain text

- **Status:** Accepted
- **Date:** 2026-10 (preparing the Amazon Associates application, ADR-0020)

## Context
A product had a name, a price, a picture and nothing else. For the shop's own products that is thin; for partner
(affiliate) products it is worse. Amazon reviews every new Associates account, and a site whose product pages carry
only a name and a "Buy at Amazon" button is the commonest reason for a rejection ("insufficient original content").
Copying Amazon's own product text is not allowed either. Search engines also rank such pages low.

## Decision
- **One optional `description` per product** (V30, up to 4000 characters), written by the owner in their own words.
- **Plain text, no HTML:** `<` and `>` are refused (400), so nothing on the page can ever run as code; the storefront
  shows the text with React's escaping, blank lines as paragraphs. The text is tidied on save (trimmed, line endings
  unified, runs of empty lines reduced to one).
- **Used everywhere a page describes the product:** on the product page under the name, as the page's search-engine
  description (one line, cut at a word before 155 characters), and as `description` in its schema.org data.
- **Audited** like every other product field; a PUT without it removes it (PUT replaces).
- The admin form says, in six languages, to write in one's own words and not to copy from Amazon or other shops.

## Consequences
- Each partner product can now be a small review, which is what Amazon and search engines look for.
- Plain text means no bold, lists or links in descriptions; enough for a small shop, and the safest choice.
- The audit trail keeps the first 500 characters of a changed description (as for every audited value).
- Proven by `ProductDescriptionTest`, `ProductDescriptionIntegrationTest` and `playwright/tests/affiliate.spec.ts`
  (REQ-CAT-16).
