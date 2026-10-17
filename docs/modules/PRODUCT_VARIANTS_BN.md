# Product variant (সাইজ আর রং): Amazon আর Shein-এর মতো

## সমস্যাটা কী ছিল

আগে প্রতিটা product ছিল একটাই জিনিস। Shein বা Amazon-এ একটা জামা S/M/L/XL সাইজে আর কয়েকটা রঙে আসে, আর প্রতিটা জোড়ার স্টক, দাম আর ছবি আলাদা। গ্রাহক ক্যাটালগে একটাই কার্ড দেখেন, তারপর product পাতায় রং আর সাইজ বেছে নেন।

## নকশা: Amazon-এর parent/child মডেল

একটা বড় সিদ্ধান্ত নিতে হয়েছে: আলাদা `product_variants` টেবিল বানাব, নাকি প্রতিটা variant নিজেই একটা product হবে?

| বিকল্প | সুবিধা | অসুবিধা |
|---|---|---|
| আলাদা variant টেবিল | দেখতে "পরিষ্কার" | cart, order, inventory, warehouse, allocator, report, review, wishlist, সবখানে নতুন কলাম আর নতুন লজিক লাগত। multi-warehouse-এর প্রমাণিত overselling সুরক্ষাও নতুন করে লিখতে হতো |
| **প্রতিটা variant একটা product (বেছে নিয়েছি)** | cart, order, warehouse, report আর review একটুও বদলাতে হয়নি, সব আগের মতোই প্রতি product ধরে কাজ করে। Amazon-ও এভাবেই করে (parent ASIN, child ASIN) | product টেবিলে ৩টা কলাম বাড়ল |

তাই `products` টেবিলে তিনটা কলাম যোগ হয়েছে (`V18__product_variants.sql`):

- `variant_group`: একই জিনিসের সব variant-এর একই গ্রুপ, যেমন `TEE-CLASSIC`
- `size`: S, M, XL, 10.5, 12 oz ইত্যাদি
- `color`: Navy, Heather Grey ইত্যাদি

Seed data-তে একটা T-shirt যোগ হয়েছে: ২টা রং × ৪টা সাইজ = ৮টা SKU। Navy XL আর Grey M-এর স্টক ইচ্ছা করে ০ রাখা হয়েছে, যাতে "sold out" অবস্থাও দেখা আর টেস্ট করা যায়। XL-এর দাম $21.99, বাকিগুলোর $19.99। প্রতিটা রঙের নিজের ছবি আছে। স্টক V17-এর নিয়মে গুদামে ভাগ হয়েছে, তাই `products.stock = SUM(warehouse_stock)` নিয়মটা এখনো ঠিক থাকে।

## ব্যবসার নিয়ম (`ProductService.variantOf`)

1. সাইজ বা রং দিলে variant group দিতেই হবে, না দিলে **400**।
2. Group দিলে সাইজ বা রঙের অন্তত একটা দিতে হবে, না দিলে **400**।
3. একই গ্রুপে বিক্রিতে থাকা দুটো product-এর সাইজ আর রং একই হতে পারবে না, ছোট-বড় হাতের অক্ষর আলাদা ধরা হয় না। হলে **409**, যেমন "Variant group TEE already has NAVY / m (SKU TEE-1)"।
4. Group সবসময় বড় হাতের অক্ষরে রাখা হয়। ফাঁকা লেখা মানে null, আর একাধিক space একটায় নামানো হয়।
5. Edit-এর সময় product নিজের সাইজ আর রং রাখতে পারে, কিন্তু অন্য variant-এর জায়গায় বসতে পারে না।
6. কোনো variant মুছে ফেললে তার সাইজ আর রঙের জোড়াটা আবার ব্যবহার করা যায়।
7. Variant-এর তিনটা ঘরই audit trail-এ ওঠে।

## API

| Endpoint | কাজ |
|---|---|
| `GET /api/products?grouped=true` | Storefront-এর জন্য: প্রতিটা জিনিসের একটা কার্ড (গ্রুপের বিক্রিতে থাকা প্রথম variant)। Admin `grouped` ছাড়া ডাকে, তাই সব variant দেখে |
| `GET /api/products/{id}/variants` | ওই জিনিসের সব সাইজ আর রং |
| `GET /api/products/variant-groups?names=A,B` | ক্যাটালগ কার্ডের সারাংশ: কয়টা অপশন, কোন কোন রং, সাইজ (S থেকে XL ক্রমে), দামের পরিসর, মোট স্টক। পুরো এক পাতার কার্ডের জন্য **একটাই request** যায় |

Cart, order আর wishlist-এর লাইনে নাম আসে "Classic Cotton T-Shirt (Navy, M)" আকারে। এই নামটা অর্ডারে snapshot হিসেবে রাখা হয়, তাই পরে কেউ product বদলালেও পুরোনো অর্ডারে ঠিক জিনিসটার নামই থাকে।

"Similar products"-এ একই জিনিসের অন্য সাইজ বা রং দেখায় না, কারণ সেগুলো তো picker-এই আছে।

## Frontend

- **ক্যাটালগ কার্ড:** রঙের ছোট গোল দাগ, "8 options: 2 colours, 4 sizes", "From $19.99", আর **Choose options** বোতাম। সাইজ না বেছে কার্ট-এ দেওয়া যায় না।
- **Product পাতা:**
  - রঙের swatch বোতাম আর সাইজের বোতাম, বাছাই করা অপশন `aria-pressed` দিয়ে চিহ্নিত।
  - বাছলে সেই variant-এর পাতা খোলে, আর দাম, স্টক, ছবি, SKU সব বদলে যায়।
  - এই রঙে যে সাইজ নেই সেটা বাছা যায় না। Sold out সাইজ দেখা যায় কিন্তু কেনা যায় না।
  - নতুন **buy box**: পরিমাণ (১ থেকে ১০, validation-সহ), Add to cart, আর "In stock / Only 3 left / Out of stock" চিহ্ন।
- **Admin product form:** "Size and colour" অংশে Variant group, Size আর Colour ঘর। Browser-এ backend-এর নিয়মগুলোই আগে যাচাই হয়। টেবিলে নামের নিচে "Navy / M TEE-CLASSIC" দেখায়।
- নতুন ছবি: `tee-navy.svg`, `tee-grey.svg`, আর `clothing.svg` (clothing category-র ছবি)। Visual baseline-ও যোগ করা হয়েছে।

## টেস্ট: পিরামিডের প্রতিটা স্তরে

| স্তর | ফাইল | কয়টা |
|---|---|---|
| Unit (Mockito) | `ProductVariantTest` | ১৫ |
| Integration (Spring + H2) | `ProductVariantIntegrationTest` | ৫ |
| API (REST Assured) | `ProductVariantApiTest` | ৪ |
| BDD (Cucumber) | `variants.feature` | ৪টা scenario |
| UI (Playwright, ফোনসহ) | `variants.spec.ts` | ২ |
| Visual | `visual.spec.ts`-এ নতুন ৩টা ছবি | ৩ |
| Contract | `product.json` / `product-list.json`-এ নতুন ৩টা ঘর | – |

Traceability matrix-এ যোগ হয়েছে **REQ-CAT-07 থেকে REQ-CAT-10**। এখন মোট ১১৮টা requirement, সবগুলোর টেস্ট আছে।

## Interview-এ কীভাবে বলবেন

> "I modelled variants the way Amazon does, as child products in a variant group. Every size and colour keeps its own SKU, price and warehouse stock, so the cart, the order snapshot, the overselling guard and the reports didn't change. Only the catalog grouping, the picker and the uniqueness rule (409 on a duplicate size/colour in a group) are new. I tested each layer: unit tests for the rules, integration tests on the real database, API and BDD tests, and Playwright on desktop and phones."
