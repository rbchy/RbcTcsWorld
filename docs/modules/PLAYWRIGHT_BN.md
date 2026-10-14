# Playwright (TypeScript): একই শপ, আরেকটা টুল দিয়ে টেস্ট

`automation/`-এর Java + Selenium suite-এর পাশে `playwright/` ফোল্ডারে একটা TypeScript suite আছে। একই storefront পরীক্ষা করে, কিন্তু এমন কিছু জিনিস দিয়ে যা Selenium দিয়ে কঠিন বা ধীর:

| Playwright-এ যা আছে | এই প্রজেক্টে কোথায় |
|---|---|
| তিনটা browser engine: Chromium, Firefox, **WebKit (Safari-র engine, Linux-এও চলে)** | `playwright.config.ts`-এর projects |
| ফোনের স্ক্রিন: Pixel 7, iPhone 14 (touch, viewport, user agent) | `mobile-android`, `mobile-iphone` project; `@mobile` ট্যাগের টেস্ট |
| Network mocking: server-এর উত্তর নিজে বানানো, দেরি করানো, কেটে দেওয়া | `tests/network.spec.ts` |
| Visual regression: ছবির pixel তুলনা | `tests/visual.spec.ts` (১২টা পণ্যের ছবি) |
| Auto-wait আর web-first assertion (`toHaveText`, `toBeVisible`) | সব টেস্টে; `sleep` নেই |
| Trace viewer: ব্যর্থ টেস্টের প্রতিটা ধাপ, DOM, network, console | CI artifact `playwright-report` |
| API testing একই টুলে | `tests/api.spec.ts`, আর `support/shop-api.ts` দিয়ে test data |

## ফোল্ডারের কাঠামো

```
playwright/
├── playwright.config.ts      projects (৩ browser + ২ ফোন), retry, trace, report
├── support/
│   ├── shop-api.ts           API দিয়ে test data: customer, admin, product, order
│   └── fixtures.ts           api, customer, signedIn (লগইন করা browser) fixture
├── pages/                    page object: catalog-page.ts, shell.ts
└── tests/
    ├── catalog.spec.ts       ছবি সত্যিই লোড হয় কি না, fallback, search, পণ্যের পাতা
    ├── journey.spec.ts       কেনাকাটা: কুপন, declined কার্ড, তারপর 4242 → PAID (API-তেও যাচাই)
    ├── session.spec.ts       Log out (server-এ revoke), Exit (goodbye + window.close), ফোনে আইকন, admin-এর ছবি
    ├── network.spec.ts       500 error, server বন্ধ, DEF-016 (দেরিতে আসা পুরোনো উত্তর), ছবির server বন্ধ
    ├── visual.spec.ts        ১২টা SVG ছবির screenshot তুলনা
    ├── a11y.spec.ts          axe-core: WCAG 2.1 AA
    └── api.spec.ts           API-র field, বিপজ্জনক ছবির লিংক 400, অনুমতি 401/403
```

## Mac-এ চালানো

শপ চালু থাকতে হবে, desktop launcher দিয়ে অথবা `docker compose --profile app up -d`। তারপর:

```bash
cd ~/Eclipse-Workspace-QA/RbcTcsWorld_ECommerceSuite/playwright
npm ci                                        # প্রথমবার
npx playwright install chromium firefox webkit   # প্রথমবার, browser নামায়

npx playwright test                           # সব (৩ browser + ২ ফোন)
npx playwright test --project=webkit          # শুধু Safari-র engine
npm run test:mobile                           # শুধু ফোন
npx playwright test tests/journey.spec.ts     # একটা ফাইল
npx playwright test --ui                      # UI mode: টেস্ট বেছে চালানো, ধাপে ধাপে দেখা (সবচেয়ে কাজের)
npx playwright test --headed --project=chromium   # browser খোলা রেখে দেখা
npm run report                                # HTML রিপোর্ট খোলা
```

অন্য ঠিকানায় চালাতে: `UI_URL=https://129-146-10-20.sslip.io ADMIN_PASSWORD=... npx playwright test`। Live demo-তে admin-এর পাসওয়ার্ড আলাদা।

**নতুন টেস্ট লেখার সহজ উপায়:**

```bash
npx playwright codegen http://localhost:5173
```

Browser-এ যা ক্লিক করবেন, Playwright সেটা কোড হিসেবে লিখে দেয়। কোডটা নিয়ে `getByTestId` দিয়ে সাজিয়ে নিন।

**ব্যর্থ টেস্ট বোঝা:** রিপোর্টে টেস্টে ক্লিক করুন → **Trace**। প্রতিটা ধাপের screenshot, DOM, network আর console দেখা যায়। CI-তে: Actions → run → Artifacts → `playwright-report` নামিয়ে `npx playwright show-report <ফোল্ডার>`।

## Visual টেস্ট: ছবি ইচ্ছা করে বদলালে

```bash
npm run update-snapshots            # নতুন baseline PNG তৈরি
```

`tests/__screenshots__/`-এর নতুন PNG খুলে দেখে নিন, তারপর commit করুন।

- Baseline Linux Chromium-এ তৈরি। Mac-এ visual টেস্ট চালালে অল্প pixel পার্থক্য আসতে পারে, তাই এটা CI-তে চলে।
- ছবিগুলোতে লেখা নেই, তাই font-এর পার্থক্যে ভাঙে না। ১% সহনশীলতা রাখা আছে।

## কিছু উল্লেখযোগ্য টেস্ট

- **DEF-016 আবার ফিরে আসেনি তো?** "mo" খোঁজার উত্তর Playwright ২ সেকেন্ড আটকে রাখে, তারপর একটা ভুল পণ্য পাঠায়। ততক্ষণে "mouse"-এর উত্তর এসে গেছে। টেস্ট দেখে, দেরিতে আসা পুরোনো উত্তর পাতায় বসেনি। আসল server দিয়ে এটা নির্ভরযোগ্যভাবে করা প্রায় অসম্ভব।
- **Exit-এর `window.close()`:** আসল window বন্ধ হলে টেস্টও শেষ হয়ে যেত। তাই `addInitScript` দিয়ে `window.close` বদলে গুনে দেখা হয় যে ঠিক একবার ডাকা হয়েছে।
- **ছবি "সত্যিই" লোড হয়েছে কি না:** `src` attribute ঠিক থাকলেও ছবি ভাঙা হতে পারে। তাই browser-এর `naturalWidth > 0` দেখা হয়।
- **ফোনে Log out:** বোতামের লেখা চোখে দেখা যায় না (শুধু আইকন), কিন্তু `getByRole('button', { name: 'Log out' })` তবু খুঁজে পায়। মানে screen reader-ও নামটা পড়বে।

## Selenium বনাম Playwright (interview-এর জন্য)

| বিষয় | Selenium (automation/) | Playwright (playwright/) |
|---|---|---|
| ভাষা | Java | TypeScript |
| Browser | আসল Chrome, Firefox, Edge, Safari (Mac) | Chromium, Firefox, WebKit (নিজের build) |
| অপেক্ষা | `WebDriverWait` নিজে লিখতে হয় | Auto-wait, web-first assertion |
| Network | শুধু দেখা (BiDi দিয়ে কিছুটা) | ধরা, বদলানো, দেরি করানো, কেটে দেওয়া |
| ডিবাগ | Screenshot, log | Trace viewer, UI mode, codegen |
| কখন ভালো | আসল browser আর grid দরকার, Java টিম, Appium-এর সাথে একই API | দ্রুত, স্থির UI টেস্ট; network-নির্ভর অবস্থা; visual টেস্ট |

> Interview-এ: "একই অ্যাপ দুই টুলে টেস্ট করেছি। Selenium আসল browser আর Safari-র জন্য, Playwright WebKit, ফোন, network mocking আর visual regression-এর জন্য। দুটোই একই `data-testid` ব্যবহার করে, তাই UI বদলালেও কোনোটা ভাঙে না।"
