# Storefront UI: মেনু, অ্যাডমিন প্যানেল, ছয় ভাষা আর ডেস্কটপ সংস্করণ

এই ধাপে storefront-কে পুরো একটা অ্যাপের চেহারা দেওয়া হয়েছে। আছে topbar, sidebar মেনু, ব্যানার, লগইন আর রেজিস্ট্রেশন (field validation-সহ), একটা পুরো store-admin প্যানেল, ছয় ভাষায় help center, আর desktop থেকে চালানোর ব্যবস্থা। Backend-এ কোনো পরিবর্তন লাগেনি, কারণ সব admin API আগে থেকেই ছিল আর টেস্ট দিয়ে যাচাই করা।

## ১. কাঠামো

| অংশ | কী আছে |
|---|---|
| **Topbar** | লোগো, ভাষা বাছাই, "Install app" (desktop), Help, লগইন বা রেজিস্টার, অথবা ইমেইল + Admin badge + লগ আউট |
| **Sidebar (মেনু)** | Shop: Catalog, Wishlist, Cart, Orders · Account: Log in, Create account · **Store admin** (শুধু ADMIN-এর জন্য): Dashboard, Products, Coupons, All orders, Returns, Reviews · Support: Help |
| **ব্যানার** | পার্সেলের লেবেলের মতো দেখতে, ভেতরে স্বাগতম কুপন **WELCOME10**। পুরো ডিজাইনে এটাই একমাত্র "জোরালো" জিনিস, বাকি সব শান্ত। |
| **মোবাইল** | ৮৬০px-এর নিচে মেনু "Menu" বোতামের পেছনে চলে যায়। Esc চাপলে বা বাইরে ক্লিক করলে বন্ধ হয়। |

রঙ: harbor teal (`#12313f`) দিয়ে ফ্রেম, copper (`#b45309`) দিয়ে বোতাম। সব লেখার contrast WCAG AA মেনে (৪.৫:১)।

## ২. Store admin প্যানেল (`#/admin`)
- **Dashboard:** মোট অর্ডার, শিপের অপেক্ষায় থাকা অর্ডার, আয়, কম স্টকের পণ্য, সিদ্ধান্তের অপেক্ষায় থাকা রিটার্ন।
- **Products:** **Add, Edit, Update, Delete, Search (browse + পাতা ভাগ করে দেখা), Stock history।**
  - Delete করলে পণ্য পুরো মুছে যায় না (soft delete)। গ্রাহকরা আর দেখেন না, কিন্তু অর্ডারের ইতিহাসে থেকে যায়।
  - মোছার আগে লাইনের ভেতরেই নিশ্চিত করতে বলে। Browser-এর popup ব্যবহার হয় না, কারণ সেটা Selenium আটকে দেয়।
- **Coupons:** নতুন কুপন তৈরি আর তালিকা।
- **All orders:** অবস্থা দিয়ে ফিল্টার, অর্ডার নম্বর দিয়ে খোঁজা, UPS/FedEx/USPS দিয়ে **Ship**, তারপর **Mark delivered**।
- **Returns:** নোটসহ Approve বা Reject।
- **Reviews:** Hide বা Publish।

Customer হিসেবে `#/admin`-এ গেলে শুধু "এই অংশ অ্যাডমিনের জন্য" বার্তা দেখায়। আসল সুরক্ষা অবশ্য backend-এ (403); UI শুধু ভদ্রভাবে জানিয়ে দেয়।

**অ্যাডমিন লগইন (development):** `admin@rbctcsworld.com` / `Admin@12345`

## ৩. Field validation
Browser-এর নিয়ম আর backend-এর নিয়ম **হুবহু এক**। Backend তবু শেষ বিচারক, কারণ UI এড়িয়ে সরাসরি API-তে যাওয়া যায়।

| ফর্ম | নিয়ম |
|---|---|
| Registration / Login | ইমেইলের ফরম্যাট, পাসওয়ার্ড ৮–৭২ অক্ষর, দুবার লেখা পাসওয়ার্ড মিলতে হবে, "পাসওয়ার্ড দেখান" |
| Product | নাম আর SKU লাগবেই, দাম > 0 আর সর্বোচ্চ ২ দশমিক, স্টক ০ বা তার বেশি পূর্ণ সংখ্যা |
| Coupon | কোডে শুধু অক্ষর, সংখ্যা, `_` আর `-`; মান > 0; শতাংশ ≤ ১০০; সর্বোচ্চ ব্যবহার ≥ ১ |

- ভুল ঘর লাল হয়, আর তার ঠিক নিচে বার্তা দেখায় (`aria-invalid` + `aria-describedby`), তাই screen reader-ও পড়ে শোনায়।
- ঠিক করার সাথে সাথে বার্তা চলে যায়।
- Server-এর ভুল (যেমন duplicate SKU 409) উপরে দেখায়, আর ফর্মে লেখা ডেটা হারায় না।

## ৪. ছয় ভাষা
English, বাংলা, Español, Français, العربية, 中文। Topbar থেকে বাছাই করা যায়, আর এই ডিভাইসে পছন্দটা মনে থাকে।
- `<html lang>` বদলায়, তাই screen reader সঠিক উচ্চারণে পড়ে।
- **আরবিতে পুরো পাতা ডান থেকে বামে (RTL)** ঘুরে যায়। CSS-এ logical properties (`margin-inline`, `inset-inline`) ব্যবহার করায় আলাদা করে RTL-এর CSS লিখতে হয়নি।
- পণ্যের নাম, দাম আর অর্ডারের অবস্থা backend থেকে আসে, তাই অনুবাদ হয় না।
- **Default ইংরেজি,** কারণ সব UI টেস্ট ইংরেজি লেখা পড়ে। ভাষা বদলানোর টেস্ট শেষে আবার ইংরেজিতে ফিরিয়ে দেয়।

**Help center (`#/help`):** ৯টা বিষয়, প্রতিটা ছয় ভাষায়। বিষয়গুলো: শুরু করা, পণ্য খোঁজা, চেকআউট + টেস্ট কার্ড, অর্ডার, রিটার্ন, রিভিউ, নিরাপত্তা, অ্যাডমিন গাইড, ডেস্কটপ + কীবোর্ড। সব তথ্য backend-এর আসল নিয়মের সাথে মিলিয়ে লেখা: $50-এ ফ্রি শিপিং, ৬% কর, ৫ বার ভুল পাসওয়ার্ডে ১৫ মিনিট লক, ৩০ দিনের রিটার্ন। Help-এর ভেতরে খোঁজাও যায়।

## ৫. Desktop থেকে চালানো
দুটো অংশ একসাথে কাজ করে:

1. **এক ক্লিকে চালু (`desktop/` ফোল্ডার):**
   - Mac: `RbcTcsWorld-Mac.command`-এ ডাবল-ক্লিক করুন।
     - Docker বন্ধ থাকলে নিজেই চালু করে।
     - তারপর পুরো app (PostgreSQL, backend, storefront) চালু করে browser খুলে দেয়।
     - বন্ধ করতে: `RbcTcsWorld-Stop-Mac.command`।
   - Windows: `RbcTcsWorld-Windows.bat` আর `RbcTcsWorld-Stop-Windows.bat`।
   - Linux: `rbctcsworld-linux.sh`।
   - প্রথমবার image তৈরি হতে কয়েক মিনিট লাগে, পরের বার কয়েক সেকেন্ড।
   - Mac-এ প্রথমবার "unidentified developer" বললে: ফাইলে right-click → **Open**।
2. **Installable app (PWA):**
   - Chrome বা Edge-এ topbar-এর **Install app** চাপলে দোকান নিজের window-এ খোলে, Dock বা Start menu-তে আইকনসহ।
   - Server বন্ধ থাকলে একটা সাহায্যকারী পাতা দেখায় ("দোকান চালু নেই, launcher চালান")।
   - Service worker ইচ্ছা করেই ছোট রাখা হয়েছে। দাম, স্টক আর অর্ডার কখনো cache হয় না, সবসময় সরাসরি server থেকে আসে।

## ৬. টেস্ট
| টেস্ট | কী প্রমাণ করে |
|---|---|
| `UiAdminProductsTest#productLifecycle` | Add (আগে খালি ফর্ম আর ঋণাত্মক দাম আটকায়) → Update → Delete। প্রতিটা ধাপ **API দিয়েও** যাচাই করা হয়, আর মোছার পর catalog থেকে উধাও |
| `UiAdminProductsTest#duplicateSku` | Server-এর 409 দেখায়, ফর্ম খোলা থাকে |
| `UiAdminProductsTest#customerHasNoAdminArea` | Customer অ্যাডমিন মেনু বা পাতা পায় না |
| `UiShellTest#registrationValidation` | খালি ঘর, ছোট পাসওয়ার্ড, না-মেলা পাসওয়ার্ড, ভুল ইমেইল, তারপর সফল রেজিস্ট্রেশন |
| `UiShellTest#languages` | বাংলা, আরবি (`dir=rtl`), চীনা; reload-এর পরেও পছন্দ থাকে |
| `UiShellTest#helpCenter` | সব বিষয় আছে; "4242" খুঁজলে শুধু checkout বিষয়টা আসে; ফরাসিতেও একই বিষয় |
| `AccessibilityTest#wcag21aa` | এখন **১৩টা পাতা**: আগের ৮টা, সাথে Registration, Help, Admin dashboard, Products আর Orders |

- সব নতুন UI টেস্ট Chrome, Firefox আর Edge তিনটাতেই চলে।
- Page object (`LoginPage`) এখন শিরোনামের লেখা না পড়ে `data-mode` attribute পড়ে। তাই ভাষা বদলালেও টেস্ট ভাঙে না।

## ৭. Interview-এ
> "আমি storefront-কে একটা পূর্ণ অ্যাপ বানিয়েছি: sidebar মেনু, store-admin প্যানেলে পণ্যের CRUD, ছয় ভাষা (আরবির জন্য RTL-সহ), আর installable desktop সংস্করণ। Field validation browser আর backend-এ হুবহু একই নিয়মে চলে। প্রতিটা admin কাজ UI-তে করার পর API দিয়েও যাচাই করি, কারণ UI-তে দেখানোটা প্রমাণ না। নতুন পাঁচটা পাতাসহ ১৩টা পাতাই WCAG 2.1 AA পাস করে, তিনটা browser-এই।"
