# Partner (affiliate) পণ্য: Amazon-এর লিংক দিয়ে কমিশন

নিজের পণ্য ঠিক হওয়ার আগেই দোকান থেকে আয় শুরু করা যায়: Amazon-এর মতো partner দোকানের পণ্য দেখান, গ্রাহক সেখানে কিনলে আপনি কমিশন পান।
এখন যেকোনো পণ্যকে "partner পণ্য" বানানো যায়, যেমন electronics, shoes বা clothing। তখন পণ্যের পাতায় দাম আর cart-এর বদলে **Buy at Amazon** বোতাম দেখায়।
কেন এভাবে: [ADR-0020](../adr/0020-affiliate-products.md)।

> আমি আইনি বা ট্যাক্স পরামর্শদাতা নই। নিচের নিয়মগুলো Amazon Associates-এর Operating Agreement আর FTC-র নির্দেশনা থেকে নেওয়া। নিয়ম বদলায়, তাই যোগ দেওয়ার আগে Amazon-এর নিজের পাতাটা নিজে একবার পড়ে নিন।

## ধাপ ১: Amazon Associates-এ যোগ দিন (একবার)

1. আগে দোকানটা নিজের domain-এ চালু থাকতে হবে (Phase 5, আপনার অংশ)। Amazon আবেদন দেখে একটা খোলা, আসল ওয়েবসাইট দেখতে চায়, ডেমো নয়।
2. affiliate-program.amazon.com → Sign up। ওয়েবসাইটের ঠিকানায় দোকানের domain দিন।
3. আপনি একটা **tag** পাবেন, যেমন `rbctcsworld-20`। প্রতিটা লিংকে এটা থাকে, এটা দিয়েই Amazon বোঝে বিক্রিটা আপনার।
4. **প্রথম ১৮০ দিনে অন্তত ৩টা বিক্রি** না হলে Amazon account বন্ধ করে দেয়। আপনি পরে আবার আবেদন করতে পারেন।

## ধাপ ২: দোকানে partner পণ্য যোগ করুন

1. Amazon-এ পণ্যটা খুলুন, উপরের **SiteStripe** বার থেকে **Get Link → Text** দিয়ে আপনার tag-সহ লিংক কপি করুন।
2. Admin → Products → **Add product**:
   - নাম আর SKU (নিজের দেওয়া, যেমন `AMZ-SHOE-001`)।
   - Category: `electronics`, `shoes` বা `clothing`।
   - Price: যেকোনো আন্দাজ। এটা কোথাও দেখায় না, শুধু ঘরটা পূরণ করতে লাগে।
   - Stock: যেকোনো সংখ্যা।
   - **Sold by a partner shop → Partner link**-এ লিংকটা দিন।
3. ছবি: **নিজের তোলা ছবি আপলোড করুন**, বা খালি রাখুন (category-র ছবি দেখাবে)। Amazon-এর ছবি কপি করা নিষেধ।
4. Save। তালিকায় নামের পাশে **Amazon** লেবেল দেখাবে।

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
