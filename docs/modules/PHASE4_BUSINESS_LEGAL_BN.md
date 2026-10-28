# Phase 4: ব্যবসা আর আইনি কাজ (মালিকের গাইড)

দোকান আসল টাকা নেওয়ার আগে আইনের চোখে একটা ব্যবসা থাকতে হবে, ট্যাক্স নেওয়ার অনুমতি থাকতে হবে, আর গ্রাহকের জন্য
নীতিমালা থাকতে হবে। এই গাইডে মালিকের কাজগুলো ক্রম অনুযায়ী, আর engineering-এর অংশ (নীতিমালার পাতা) কীভাবে চালু করবেন।

> **জরুরি:** এটা আইনি বা ট্যাক্স পরামর্শ নয়। ফি আর নিয়ম অক্টোবর ২০২৬-এ যাচাই করা, কিন্তু বদলাতে পারে।
> প্রতিটা ধাপের আগে সরকারি সাইটে দেখে নিন, আর একজন accountant ও lawyer-এর সাথে কথা বলুন।

## ক্রম আর খরচ এক নজরে

| ধাপ | কোথায় | খরচ | কতক্ষণ |
|---|---|---|---|
| ১. নাম ঠিক করা (চাইলে reserve) | Pennsylvania Department of State | reserve করলে $70 (১২০ দিন) | সাথে সাথে |
| ২. LLC খোলা (Certificate of Organization) | file.dos.pa.gov | $125 | সাধারণত কয়েক দিন |
| ৩. EIN | IRS-এর সাইট (irs.gov) | ফ্রি | online-এ সাথে সাথে |
| ৪. Sales tax license | mypath.pa.gov | ফ্রি | কয়েক দিন |
| ৫. ব্যবসার bank account | আপনার bank | bank অনুযায়ী | ১ দিন |
| ৬. Stripe live verification | Stripe Dashboard | ফ্রি | ১–৩ দিন |
| ৭. নীতিমালা lawyer-কে দেখানো | lawyer | lawyer-এর ফি | ১–২ সপ্তাহ |
| প্রতি বছর: Annual report | file.dos.pa.gov | $7 | ৩০ সেপ্টেম্বরের মধ্যে |

## ১. LLC (Limited Liability Company)

- **কেন:** দোকানের দেনা বা মামলা আপনার ব্যক্তিগত সম্পদে আসে না।
- **কোথায়:** Pennsylvania Department of State-এর online portal **file.dos.pa.gov** → *Certificate of Organization* (domestic LLC), ফি **$125**।
- **যা লাগবে:**
  - LLC-এর নাম। শেষে "LLC" থাকতে হবে, যেমন "RbcTcsWorld LLC"।
  - Pennsylvania-র একটা registered office ঠিকানা: নিজের বাসা, অথবা একটা Commercial Registered Office Provider (CROP)। CROP-এর খরচ সাধারণত বছরে $125–300।
  - Organizer-এর নাম আর ঠিকানা।
- নাম আগে reserve করতে চাইলে $70 (১২০ দিনের জন্য)। দরকার নেই, যদি সাথে সাথেই LLC খোলেন।
- **প্রতি বছর annual report:** ১ জানুয়ারি থেকে **৩০ সেপ্টেম্বর**-এর মধ্যে, ফি **$7**, file.dos.pa.gov-এ।
  - ২০২৫ আর ২০২৬-এর report না দিলে এখনো শাস্তি নেই।
  - **২০২৭ থেকে** report না দিলে deadline-এর ছয় মাস পরে LLC প্রশাসনিকভাবে বাতিল (administrative dissolution) হয়ে যায়।
  - ক্যালেন্ডারে বছরে একবার reminder রাখুন।

## ২. EIN (Employer Identification Number)

- ব্যবসার "Social Security নম্বর"। Bank account, Stripe, sales tax license, সবখানে লাগবে।
- **IRS-এর সাইট (irs.gov) থেকে সরাসরি online-এ আবেদন, সম্পূর্ণ ফ্রি**, নম্বর সাথে সাথে পাওয়া যায়।
  যেসব সাইট EIN-এর জন্য টাকা চায়, সেগুলো IRS নয়।
- LLC খোলার **পরে** নিন, যাতে EIN LLC-এর নামে হয়।

## ৩. Pennsylvania sales tax license

- **কোথায়:** Department of Revenue-এর **myPATH** (mypath.pa.gov) → নতুন account → *Register a new business*। **কোনো ফি নেই।**
- **হার:**
  - Pennsylvania-তে ৬%।
  - Allegheny County-তে আরও ১% (মোট ৭%), Philadelphia-তে আরও ২% (মোট ৮%)।
  - তিনটাই একই state return-এ জমা হয়।
- **কী ট্যাক্স হয় না:** বেশিরভাগ কাপড় আর অনেক খাবার PA-তে ট্যাক্সমুক্ত। তাই কী বিক্রি করবেন (Phase 0) ঠিক হলে, পণ্য অনুযায়ী Stripe Tax-এর tax code বসাতে হবে (ADR-0012-এর বাকি কাজ)।
- **কত ঘন ঘন জমা:** license দেওয়ার সময় myPATH ঠিক করে দেয় (মাসিক, ত্রৈমাসিক বা ষাণ্মাসিক)। জমা দিতে হয় শূন্য বিক্রি হলেও।
- **অন্য state:** কোনো state-এ বিক্রি সেই state-এর সীমা (economic nexus) পার হলে সেখানেও license লাগে।
  Stripe Tax Dashboard এই সীমার দিকে নজর রাখে। কোথায় registration লাগবে, accountant-এর সাথে ঠিক করুন।
- License পাওয়ার পরে Stripe Dashboard → Tax → **Registrations**-এ Pennsylvania যোগ করুন, তারপর `TAX_PROVIDER=stripe`।

## ৪. Bank account আর Stripe

- **Bank:** LLC-এর কাগজ (Certificate of Organization) আর EIN নিয়ে ব্যবসার নামে আলাদা account খুলুন।
  ব্যক্তিগত আর দোকানের টাকা কখনো মেশাবেন না: এতে LLC-এর সুরক্ষা দুর্বল হয়।
- **Stripe:** Dashboard → *Activate payments*। দিতে হবে:
  - LLC-এর নাম, EIN আর ঠিকানা
  - আপনার পরিচয়
  - Bank account
  - দোকানের website আর নীতিমালার link
  
  এরপর live key পাবেন (`sk_live_`, `pk_live_`)। live key শুধু `prod` profile-এ চলে।

## ৫. নীতিমালা: engineering-এর অংশ (তৈরি)

দোকানে এখন চারটা পাতা আছে, প্রতিটা পাতার footer-এ link: **Privacy policy, Terms of service, Return and refund policy, Shipping policy**।
সাথে একটা cookie notice, আর account খোলা ও অর্ডার দেওয়ার জায়গায় শর্তের link।

- **পাতার সংখ্যা কোড থেকে আসে:** ফেরতের সময়সীমা (৩০ দিন), shipping-এর দাম ($5.99, $50 থেকে ফ্রি) দোকানের নিজের setting থেকে পড়া হয় (`GET /api/store/legal`)।
  তাই setting বদলালে নীতিমালাও সাথে সাথে বদলায়। নীতিমালা কখনো এমন কিছু বলবে না যা দোকান করে না।
- **এখন খসড়া:** ব্যবসার নাম আর ঠিকানার জায়গায় placeholder, আর প্রতিটা পাতায় হলুদ "Draft" লেখা।
- **lawyer-কে কী দেখাবেন:** দোকানের চারটা পাতা, আর এই কথাগুলো:
  - Pennsylvania-র LLC
  - শুধু US-এ পাঠানো হয়
  - কার্ড Stripe-এ যায়
  - গ্রাহকের তথ্য পায় Stripe, Shippo/carrier আর Amazon SES
  - বিজ্ঞাপন বা tracking cookie নেই
- **lawyer পরিবর্তন বললে:** লেখাগুলো `frontend/src/pages/Legal.jsx`-এ। বদলে দিতে আমাকে বলবেন।
- **সব ঠিক হলে `.env`-এ বসান:**
  ```bash
  STORE_LEGAL_NAME=RbcTcsWorld LLC
  STORE_STREET=...
  STORE_CITY=...
  STORE_STATE=PA
  STORE_ZIP=.....
  STORE_CONTACT_EMAIL=support@your-store.com
  STORE_POLICIES_EFFECTIVE=2026-12-01      # যেদিন থেকে নীতিমালা চালু
  STORE_POLICIES_REVIEWED=true             # শুধু lawyer দেখার পরে
  ```
  একটা তথ্যও খালি থাকলে `REVIEWED=true` দিলেও পাতায় "Draft" থাকে। ভুল করে অসম্পূর্ণ নীতিমালা "চূড়ান্ত" দেখানো যায় না।

## টেস্ট কোথায়

| স্তর | টেস্ট | কী প্রমাণ করে |
|---|---|---|
| Unit | `LegalInfoControllerTest` | খালি তথ্য মানে placeholder; কিছু বাকি থাকলে কখনো "reviewed" নয় |
| Integration | `LegalInfoIntegrationTest` | login ছাড়া পড়া যায়, সংখ্যা দোকানের setting-এর সাথে মেলে |
| Browser | `playwright/tests/legal.spec.ts` | footer থেকে চারটা পাতা, পাতার সংখ্যা = API-র সংখ্যা, cookie notice, শর্তের link, বাংলায় "ইংরেজিতে" নোট |
| Accessibility | `playwright/tests/a11y.spec.ts` | privacy আর return পাতায় কোনো গুরুতর WCAG ভুল নেই |
| Security | `AccessControlMatrixTest` | `/api/store/legal` সবার জন্য খোলা |

Requirement: REQ-LEG-01, REQ-LEG-02, REQ-LEG-03 ([traceability](../qa/TRACEABILITY_MATRIX.md))।
