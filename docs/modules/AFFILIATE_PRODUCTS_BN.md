# Partner (affiliate) পণ্য: Amazon-এর লিংক দিয়ে কমিশন

নিজের পণ্য ঠিক হওয়ার আগেই দোকান থেকে আয় শুরু করা যায়: Amazon-এর মতো partner দোকানের পণ্য দেখান, গ্রাহক সেখানে কিনলে আপনি কমিশন পান।
এখন যেকোনো পণ্যকে "partner পণ্য" বানানো যায়, যেমন electronics, shoes বা clothing। তখন পণ্যের পাতায় দাম আর cart-এর বদলে **Buy at Amazon** বোতাম দেখায়।
কেন এভাবে: [ADR-0020](../adr/0020-affiliate-products.md)।

> আমি আইনি বা ট্যাক্স পরামর্শদাতা নই। নিচের নিয়মগুলো Amazon Associates-এর Operating Agreement আর FTC-র নির্দেশনা থেকে নেওয়া। নিয়ম বদলায়, তাই যোগ দেওয়ার আগে Amazon-এর নিজের পাতাটা নিজে একবার পড়ে নিন।

## Amazon Associates: কোন ক্রমে কী করবেন

Amazon আবেদনের পরে একজন মানুষ দিয়ে সাইট দেখে। সাইট তখন খোলা না থাকলে, "under construction" দেখালে, বা পাতায় নিজের লেখা না থাকলে আবেদন বাতিল হয়। আর আবেদনের দিন থেকেই **১৮০ দিনে ৩টা বিক্রির** ঘড়ি চলে। তাই আগে সাইট প্রস্তুত, তারপর আবেদন।

### ধাপ ১: সাইট চালু (আবেদনের আগে, বাধ্যতামূলক)

1. দোকান নিজের domain-এ চালু করুন (Phase 5: সার্ভার, domain, `deploy/production`)। [PRODUCTION_BN](../deploy/PRODUCTION_BN.md)
2. domain-এর নামে **"amazon" শব্দ রাখবেন না**, এটা Amazon-এর নিয়মে নিষেধ।
3. `.env`-এ দোকানের তথ্য দিন (`STORE_LEGAL_NAME`, ঠিকানা, `STORE_CONTACT_EMAIL`), যাতে privacy policy আর terms-এ আসল নাম-ঠিকানা দেখায়।
4. নমুনা পণ্যগুলো (আঁকা ছবির mouse, mug, QA বই ইত্যাদি) Admin → Products → **Delete** দিয়ে সরান। Delete করলে পণ্য গ্রাহকের কাছ থেকে লুকায়, পুরোনো অর্ডারে থেকে যায়। নকল পণ্য দেখলে reviewer সাইটকে আসল দোকান মনে করবে না।

### ধাপ ২: ১০-১৫টা partner পণ্য, নিজের লেখা আর ছবি দিয়ে

প্রতিটা পণ্যের জন্য:

1. Amazon-এ পণ্যটা খুলুন। ঠিকানা থেকে `https://www.amazon.com/dp/` আর তার পরের ১০ অক্ষরের কোড (ASIN) কপি করুন, যেমন `https://www.amazon.com/dp/B0XXXXXXXX`। tag পরে যোগ হবে।
2. Admin → Products → **Add product**:
   - **Name**: নিজের ভাষায় ছোট নাম। SKU নিজের দেওয়া, যেমন `AMZ-SHOE-001`।
   - **Category**: `electronics`, `shoes` বা `clothing`।
   - **Price, Stock**: যেকোনো আন্দাজ। এগুলো partner পণ্যে কোথাও দেখায় না।
   - **Description** (নতুন ঘর): **নিজের ভাষায়** ৩-৬ লাইন: জিনিসটা কী, কার জন্য ভালো, কী খেয়াল রাখতে হবে (মাপ, ব্যাটারি, ধোয়া ইত্যাদি)। নিজে ব্যবহার করে থাকলে সেই অভিজ্ঞতা লিখুন। **Amazon-এর বর্ণনা বা review কপি করবেন না।** reviewer ঠিক এটাই দেখেন, আর Google-ও এটা দিয়েই পাতা খুঁজে পায়।
   - **Partner link**: ধাপ ১-এর লিংক।
3. **ছবি**: নিজের তোলা ছবি আপলোড করুন, অথবা খালি রাখুন (category-র আঁকা ছবি দেখাবে)। Amazon-এর ছবি কপি করা নিষেধ।
4. Save। তালিকায় নামের পাশে **Amazon** লেবেল দেখাবে।

### ধাপ ৩: আবেদন (affiliate-program.amazon.com → Sign up)

হাতে রাখুন: Amazon-এর নিজের account, দোকানের ঠিকানা, SSN (বা ব্যবসা খুললে EIN), ব্যাংক account (টাকা আসবে direct deposit-এ)।

1. **Account information**: টাকা কে পাবেন, তাঁর নাম আর ঠিকানা।
2. **Website and mobile app list**: দোকানের ঠিকানা, যেমন `https://your-store.com`।
3. **Profile**:
   - **Store ID**: এটাই আপনার **tag** হবে, যেমন `rbctcsworld-20`।
   - সাইট কী নিয়ে: "An online store with hand-picked electronics, shoes and clothing, each with our own short review" জাতীয় সত্যি বর্ণনা।
   - Topics: Electronics, Clothing & Accessories, Shoes।
   - Traffic কোথা থেকে: যা সত্যি (search engine, social media)।
4. **Tax interview**: W-9, SSN বা EIN দিয়ে। আমি ট্যাক্স পরামর্শদাতা নই, কমিশন আয় কীভাবে দেখাবেন সেটা হিসাবরক্ষককে জিজ্ঞেস করুন।
5. **Payment**: direct deposit, ব্যাংক account দিয়ে।

### ধাপ ৪: লিংকে tag বসান

1. Amazon-এ লগইন থাকলে পাতার উপরে **SiteStripe** বার আসে: **Get Link → Text** দিলে tag-সহ লিংক পাবেন, যেমন `https://www.amazon.com/dp/B0XXXXXXXX?tag=rbctcsworld-20`।
2. Admin → Products → প্রতিটা partner পণ্য → **Edit** → Partner link বদলে tag-সহ লিংক দিন → **Update**।
3. নিজের ফোনে একটা পণ্যের **Buy at Amazon** চাপুন: ঠিকানায় `tag=rbctcsworld-20` দেখা গেলে ঠিক আছে। (নিজে কিনবেন না, নিজের কেনা বিক্রি গোনা হয় না আর নিয়মবিরুদ্ধ।)

### ধাপ ৫: ১৮০ দিনে ৩টা বিক্রি

- দোকানের **নিজের ঠিকানা** (affiliate লিংক নয়) Facebook, Instagram, WhatsApp-এ শেয়ার করুন। মানুষ দোকানে এসে বোতাম চাপবেন।
- Affiliate লিংক সরাসরি ইমেইল, PDF বা ছাপা কাগজে দেবেন না, এগুলো নিষেধ।
- Google Search Console-এ sitemap জমা দিন ([SEARCH_ENGINES_BN](SEARCH_ENGINES_BN.md))।
- প্রতি সপ্তাহে ২-৩টা নতুন পণ্য বা পুরোনো বর্ণনা উন্নত করুন। চালু সাইটই reviewer আর Google দুজনেই পছন্দ করে।
- ৩টা বিক্রি হলে Amazon আবার দেখে চূড়ান্ত অনুমোদন দেয়। না হলে account বন্ধ হয়, তবে পরে আবার আবেদন করা যায়।

### বিকল্প পথ

দোকানের সাইট চালুর আগে Amazon YouTube, Instagram, Facebook বা TikTok-এর business account দিয়েও আবেদন নেয়। তবে সেই account-এ নিয়মিত নিজের তৈরি পোস্ট থাকতে হয়।

## গ্রাহক কী দেখেন

| জায়গা | কী দেখায় |
|---|---|
| তালিকার কার্ড | ছবি, নাম, **Buy at Amazon** বোতাম, wishlist। দাম আর cart দেখায় না |
| তালিকার উপরে | ঘোষণা: "As an Amazon Associate, RbcTcsWorld Shop earns from qualifying purchases." |
| পণ্যের পাতা | "Sold and shipped by Amazon…", ঘোষণা, তার ঠিক নিচে বোতাম (নতুন tab-এ খোলে) |
| Cart | partner পণ্য cart-এ যায় না। আগে থেকে cart-এ থাকলে "unavailable" দেখায়, আর অর্ডার আটকায় |
| Terms of service | "Products sold by partner shops" অংশ |

## যে নিয়মগুলো দোকান নিজেই মেনে চলে

| নিয়ম | দোকানে কীভাবে |
|---|---|
| Amazon-এর দাম শুধু তাদের API থেকে, ২৪ ঘণ্টার বেশি পুরোনো নয় | partner পণ্যের দাম দেখানোই হয় না |
| ঘোষণা লিংকের আগে, চোখে পড়ার মতো | বোতামের ঠিক উপরে আর তালিকার উপরে |
| paid লিংক search engine-কে জানাতে হবে | `rel="sponsored"` |
| ইমেইলে affiliate লিংক নিষেধ | partner পণ্য অর্ডার হয় না, তাই কোনো অর্ডার-ইমেইলে আসে না |
| ডেমোতে নয় | ডেমো partner লিংক নেয় না |

**আপনাকে নিজে খেয়াল রাখতে হবে:**
- Contact message-এর উত্তরে Amazon লিংক দেবেন না, কারণ সেটাও ইমেইল।
- Facebook, Instagram-এ লিংক দেওয়ার আগে Amazon-এর social media নিয়ম পড়ে নিন।
- নিজে বা পরিবারের কাউকে দিয়ে নিজের লিংকে কেনানো নিয়মবিরুদ্ধ।

## পরের ধাপ (বিক্রি শুরু হলে)

Amazon-এর Creators API (আগের PA-API) পেতে আগে বিক্রি লাগে। পেয়ে গেলে API থেকে আসল দাম, স্টক আর ছবি এনে দেখানো যাবে, দিনে অন্তত একবার নতুন করে আনা হবে। সেটা আলাদা PR হবে।

## টেস্ট (REQ-AFF-01..03)

| স্তর | টেস্ট |
|---|---|
| Unit | `CartServiceTest`, `OrderServiceTest`: cart আর অর্ডারে partner পণ্য আটকায়; `ProductImagePolicyTest`: ডেমো |
| Integration | `AffiliateIntegrationTest`: লিংক রাখা আর মোছা, cart 400, অর্ডার 400, শুধু https; ডেমো test |
| UI | `playwright/tests/affiliate.spec.ts`: বোতাম, `rel=sponsored`, ঘোষণা, দাম আর cart নেই, admin form |
