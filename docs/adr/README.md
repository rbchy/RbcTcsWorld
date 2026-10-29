# Architecture Decision Records (ADR)

কেন এভাবে বানানো হয়েছে: বড় সিদ্ধান্তগুলোর কারণ, বিকল্প আর ফলাফল, প্রতিটা আলাদা ছোট ফাইলে। "কীভাবে"
চালাতে হয় তা আছে `docs/modules/`-এ; "কেন" আছে এখানে। সিদ্ধান্ত বদলালে পুরোনো ADR মোছা হয় না, নতুন ADR লিখে
পুরোনোটাকে "Superseded" করা হয়।

| # | সিদ্ধান্ত | এক লাইনে কেন |
|---|---|---|
| [0001](0001-record-architecture-decisions.md) | ADR লেখা হবে | "কেন" প্রশ্নের উত্তর এক জায়গায় |
| [0002](0002-modular-monolith.md) | একটা Spring Boot অ্যাপ, প্রতিটা ব্যবসায়িক অংশ আলাদা package | একজনে পুরোটা টেস্ট করা যায়, CI দ্রুত; পরে package ধরে আলাদা করা যায় |
| [0003](0003-flyway-owns-the-schema.md) | Database schema শুধু Flyway বদলায় | H2, PostgreSQL, Docker, demo সব জায়গায় হুবহু একই schema |
| [0004](0004-rotating-refresh-tokens.md) | ১৫ মিনিটের access token + ঘুরতে থাকা refresh token | আসল logout, চুরি হওয়া token ধরা পড়ে, reload-এ ভুল করে logout হয় না |
| [0005](0005-transactional-outbox-for-emails.md) | অর্ডারের ইমেইল outbox টেবিল দিয়ে | ইমেইল হারায় না, দুবার যায় না, checkout অপেক্ষা করে না |
| [0006](0006-payment-gateway-abstraction.md) | Payment interface; default mock, Stripe contract-tested | আসল কার্ড ছাড়াই বাস্তব পেমেন্ট টেস্ট; ভুলেও আসল টাকা কাটবে না |
| [0007](0007-test-hooks-and-one-driver-factory.md) | `data-testid` + page object + এক DriverFactory (desktop ও ফোন) | ভাষা বা ডিজাইন বদলালে টেস্ট ভাঙে না; ফোনেও একই page object |
| [0008](0008-playwright-beside-selenium.md) | Selenium-এর পাশে Playwright | network mocking, WebKit, visual টেস্ট, trace: প্রতিটা টুল যা ভালো পারে |
| [0009](0009-public-demo-on-one-server.md) | Oracle-এর ফ্রি সার্ভারে demo, Caddy HTTPS, demo mode | ফ্রি, সবসময় চালু, গোপন তথ্য নিরাপদ |
| [0010](0010-from-portfolio-to-live-store.md) | আসল দোকান portfolio কোড থেকে, কিন্তু আলাদা repo-তে | portfolio স্থির থাকে, দোকানের নিজের ইতিহাস; দায়িত্ব (PCI, backup, ট্যাক্স) এখন মালিকের |
| [0011](0011-stripe-elements-and-webhooks.md) | কার্ড Stripe Elements-এ; অর্ডার PAID শুধু Stripe-এর কথায় (webhook/confirm) | দোকান কখনো কার্ড নম্বর দেখে না (PCI SAQ A); ব্রাউজারের কথায় টাকা ধরা হয় না; একবারই পেমেন্ট |
| [0012](0012-stripe-tax.md) | ট্যাক্স `TaxCalculator` দিয়ে; default flat 6 %, চাইলে Stripe Tax | ঠিকানা অনুযায়ী সঠিক ট্যাক্স, অনুমানের ট্যাক্সে কখনো অর্ডার নয়, পেমেন্টের পরে বিক্রি রিপোর্টে |
| [0013](0013-shippo-labels-and-tracking.md) | Shipping label `ShippingProvider` দিয়ে; default simulated, চাইলে Shippo | আসল carrier label আর tracking নম্বর, পেমেন্ট ছাড়া label নয়, Shippo নিশ্চিত করলে তবেই DELIVERED |
| [0014](0014-amazon-ses-mail.md) | দোকানের নিজের domain থেকে Amazon SES (SDK ছাড়া, SigV4); bounce/complaint SNS দিয়ে | inbox-এ পৌঁছায়, ফেরত আসা ঠিকানায় আর পাঠানো হয় না, সই ছাড়া কোনো রিপোর্ট নয় |
| [0015](0015-policies-from-the-shops-settings.md) | নীতিমালার সংখ্যা দোকানের setting থেকে; lawyer না দেখা পর্যন্ত "Draft" | নীতিমালা কখনো কোডের চেয়ে আলাদা কথা বলে না; অসম্পূর্ণ নীতিমালা চূড়ান্ত দেখায় না |
| [0016](0016-production-stack-backups-and-alerts.md) | আসল দোকান আলাদা stack-এ; encrypted off-site backup, সাপ্তাহিক restore মহড়া, alert | data হারায় না, backup সত্যিই ফেরত আনা যায় তার প্রমাণ, সমস্যা হলে আগে মালিক জানেন |
| [0017](0017-product-photo-uploads.md) | পণ্যের ছবি admin আপলোড করেন; backend নতুন করে আঁকে, database-এ রাখে | ছবির সাথে কোনো লুকানো তথ্য বা script ঢোকে না, ফোনের ছবি সোজা হয়, backup-এ ছবিও থাকে |
| [0018](0018-contact-form.md) | যোগাযোগ ফর্ম; উত্তর admin পাতা থেকে, ইমেইল outbox দিয়ে, CAPTCHA ছাড়া | প্রতিটা প্রশ্নের রেকর্ড আর উত্তর, ফর্ম spam relay হয় না, অন্যের নামে লেখা যায় না |
| [0019](0019-search-engines.md) | আসল ঠিকানা (`/products/12/mug`), page tag, JSON-LD, robots.txt আর sitemap; hash router থাকে | Google প্রতিটা পণ্য আলাদা পাতা হিসেবে পায়, ব্যক্তিগত পাতা আর ডেমো কখনো ফলাফলে নয় |
| [0020](0020-affiliate-products.md) | Partner (affiliate, যেমন Amazon) পণ্য: একটা লিংক, দাম আর cart ছাড়া, ঘোষণাসহ | নিজের পণ্যের আগেই আয়, Amazon আর FTC-র নিয়ম মেনে, partner পণ্য কখনো আমাদের অর্ডারে নয় |

**Interview-এ:** "Architecture-এর প্রতিটা বড় সিদ্ধান্তের ADR আছে: কী বিকল্প ছিল, কেন এটা বেছেছি, আর কী দাম দিতে হয়েছে (যেমন logout-এর পরেও ১৫ মিনিট access token কাজ করে, সেটা জেনে-বুঝে মেনে নেওয়া)।"
