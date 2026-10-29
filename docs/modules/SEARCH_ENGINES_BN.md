# Search engine (Google, Bing) যাতে দোকানের পণ্য খুঁজে পায়

দোকানের সব পাতা `/#/...` ঠিকানায় চলে। Google `#`-এর পরের অংশ পড়ে না, তাই তার চোখে পুরো দোকানটা ছিল একটাই পাতা।
এখন প্রতিটা পণ্যের নিজের ঠিকানা, শিরোনাম, বর্ণনা আর দাম-স্টকের তথ্য আছে, আর একটা sitemap সব ঠিকানার তালিকা দেয়।
কেন এভাবে: [ADR-0019](../adr/0019-search-engines.md)।

## কী বদলেছে

| জিনিস | উদাহরণ | কেন |
|---|---|---|
| আসল ঠিকানা | `https://<domain>/products/12/travel-mug` | `#` ছাড়া, তাই Google আলাদা পাতা হিসেবে চেনে। খুললে ঠিক `#/products/12`-এর পাতাই দেখায় |
| শিরোনাম | `Travel Mug | RbcTcsWorld Shop` | ফলাফলের নীল লাইন |
| বর্ণনা | "Travel Mug for $12.50 at RbcTcsWorld Shop. Free shipping…" | ফলাফলের নিচের লেখা, দর্শকের ভাষায় |
| canonical | `<link rel="canonical" href=".../products/12/travel-mug">` | একই পণ্যের সব size আর color একটা ঠিকানায় গোনা হয় |
| পণ্যের তথ্য (JSON-LD) | দাম, USD, In stock / Out of stock, ছবি, review-এর গড় | Google ফলাফলে দাম আর স্টক দেখাতে পারে |
| `noindex` | cart, checkout, orders, account, login, admin | ব্যক্তিগত পাতা ফলাফলে আসবে না |
| `/robots.txt` | `Sitemap: https://<domain>/sitemap.xml` | search engine-কে sitemap কোথায় বলে দেয় |
| `/sitemap.xml` | সাধারণ পাতা + প্রতিটা বিক্রির পণ্য (ছবিসহ) | নতুন পণ্য যোগ করলে নিজে থেকে sitemap-এ আসে |

**ডেমো কখনো search engine-এ যায় না:** ডেমোতে robots.txt-এ `Disallow: /`, আর প্রতিটা পাতায় `noindex`।
`PUBLIC_URL` যদি `https://` না হয় (নিজের কম্পিউটার, CI), তখনো একই।

## দোকান চালু হলে মালিকের কাজ (একবার, বিনামূল্যে)

1. **Google Search Console** (search.google.com/search-console) → Add property → নিজের domain।
2. domain যাচাই করুন: domain কেনার জায়গায় (DNS) Google যে TXT record দেয় সেটা বসান।
3. **Sitemaps** → `sitemap.xml` লিখে Submit।
4. কয়েক দিন পরে **Pages**-এ দেখবেন কতগুলো পাতা Google-এ এসেছে, আর **Enhancements → Product snippets**-এ পণ্যের তথ্য ঠিক আছে কিনা।
5. **Bing Webmaster Tools**-এ একই কাজ (Search Console থেকে import করা যায়)।

একটা পণ্য পরীক্ষা করতে: Google-এর **Rich Results Test**-এ পণ্যের ঠিকানা দিন, "Product" ধরা পড়লে ঠিক আছে।

## সীমা (এখনো যা নেই)

- Facebook, WhatsApp-এ লিংক শেয়ার করলে preview-তে পণ্যের নাম-ছবি আসে না, কারণ ওরা JavaScript চালায় না। দরকার হলে পরের ধাপে server থেকে এই tag বানাতে হবে।
- category-র আলাদা ঠিকানা নেই, তাই sitemap-এ category পাতা নেই।
- দোকানের ভেতরের লিংক এখনো `#/` ঠিকানায়; Google sitemap থেকে পণ্য খুঁজে পায়।

## পুরোনো ডেমো সার্ভার

smoke test এখন robots.txt-ও দেখে। ডেমো সার্ভার পুরোনো কোডে থাকলে প্রতিদিনের "Live demo" check fail করবে।
merge-এর পরে ডেমো সার্ভারে একবার `~/RbcTcsWorld/deploy/deploy.sh` চালালেই ঠিক হবে।

## টেস্ট (REQ-SEO-01..03)

| স্তর | টেস্ট |
|---|---|
| Unit | `SeoServiceTest`: robots (দোকান/ডেমো/http), sitemap, slug, XML escape |
| Integration | `SeoIntegrationTest`: আসল catalog থেকে sitemap, T-shirt-এর ৮টা variant একটা ঠিকানা |
| API | `AccessControlMatrixTest`: দুটোই সবার জন্য খোলা |
| UI | `playwright/tests/seo.spec.ts`: আসল ঠিকানায় পণ্য, শিরোনাম, বর্ণনা, canonical, JSON-LD, noindex, root-এ robots আর sitemap |
| Deploy | `deploy/smoke.sh`: আসল দোকানে sitemap, ডেমোতে `Disallow: /` |

**Interview-এ:** "পুরো app-কে path routing-এ সরালে প্রায় ৮০টা টেস্ট আর প্রতিটা লিংক বদলাত। তার বদলে আসল ঠিকানাগুলো একই পাতা খোলে, আর canonical tag, sitemap আর structured data দিয়ে Google-কে ঠিক ঠিকানাটা বলে দিই। ব্যক্তিগত পাতা আর ডেমো noindex।"
