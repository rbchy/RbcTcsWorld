# Storefront পেজ + Selenium UI টেস্ট

## কী বানানো হলো
React (Vite) দিয়ে পুরো কেনাকাটার UI। প্রতিটা পেজ backend-এর আসল API ব্যবহার করে:

| পেজ (route) | কী করা যায় |
|---|---|
| `#/` Home | পণ্য খোঁজা, পরিমাণ বেছে কার্টে যোগ করা |
| `#/login` | লগইন বা নতুন অ্যাকাউন্ট খোলা |
| `#/cart` | পরিমাণ বদলানো, পণ্য সরানো, subtotal দেখা |
| `#/checkout` | দামের হিসাব (subtotal, discount, shipping, tax, total), কুপন লাগানো, অর্ডার দেওয়া |
| `#/orders` | আমার সব অর্ডার আর তাদের status |
| `#/orders/{id}` | কার্ড দিয়ে পেমেন্ট, cancel, tracking timeline, পেমেন্টের ইতিহাস, return request |

`#/cart`, `#/checkout` আর `#/orders` খুলতে লগইন লাগে। লগইন না থাকলে login ফর্ম আসে, আর লগইনের পর ব্যবহারকারী যে পেজে যেতে চেয়েছিলেন সেখানেই ফিরে যান।

## টেস্টের জন্য `data-testid`
টেস্ট কখনো CSS class বা লেখার উপর নির্ভর করে না। প্রতিটা দরকারি element-এ `data-testid` আছে, যেমন `price-total`, `pay-button`, `order-status`। ডিজাইন বা লেখা বদলালেও টেস্ট ভাঙে না।

## Page Object Model
`automation/.../qa/pages/`:
- **BasePage:** শুধু explicit wait ব্যবহার করে (কোনো `Thread.sleep` নেই)। এখানে `testId()`, `visible()`, `click()`, `type()` আর `loginWithToken()` আছে।
- **LoginPage, HomePage, CartPage, CheckoutPage, OrdersPage, OrderPage:** প্রতিটা method ব্যবসার ভাষায় লেখা, যেমন `applyCoupon("WELCOME10")`, `pay(card)`, `requestReturn("DAMAGED")`। এগুলো পরের পেজের object ফেরত দেয়, তাই টেস্ট গল্পের মতো পড়া যায়।
- **OrderPage.pay():** পেজের একটা "snapshot" (status, পেমেন্টের সংখ্যা, notice আর error) নেয়, তারপর সেটা বদলানো পর্যন্ত অপেক্ষা করে। আগের চেষ্টার error স্ক্রিনে থেকে গেলেও তাই wait ভুল হয় না। flaky টেস্ট এড়ানোর এটা একটা বাস্তব কৌশল।

## UI টেস্টগুলো (`@Tag("ui")`)
| টেস্ট | কী যাচাই করে |
|---|---|
| `UiShoppingJourneyTest.endToEndPurchase` | রেজিস্টার → $30-এর পণ্য ২টা কার্টে → subtotal $60.00 → total $63.60 (shipping FREE, tax $3.60) → WELCOME10 → discount -$6.00, total $57.24 → অর্ডার → declined কার্ডে error আর status PLACED থাকে → 4242 কার্ডে PAID → timeline PLACED, PAID → FAILED পেমেন্টের রেকর্ডও রাখা থাকে |
| `invalidCoupon` | মেয়াদোত্তীর্ণ কুপনে error আসে, total বদলায় না |
| `cancelOrder` | অর্ডার cancel হয় → CANCELLED, আর Pay ও Cancel বোতাম চলে যায় |
| `UiAuthTest.wrongPassword` | ভুল পাসওয়ার্ডে error দেখায় |
| `protectedRouteRedirect` | লগইন ছাড়া `#/orders` খুললে login আসে, আর লগইনের পর My orders-এ ফেরে |
| `UiOrderAfterSalesTest.trackingAndReturn` | **Hybrid:** paid → shipped → delivered পর্যন্ত API দিয়ে তৈরি করা হয়, শুধু গ্রাহকের অংশটা UI দিয়ে চলে। এটা DELIVERED, `UPS-` tracking নম্বর আর পুরো timeline যাচাই করে, তারপর return request দিয়ে RETURN_REQUESTED দেখে |
| `HomePageUiTest` | হোম পেজ লোড হয় আর search কাজ করে |

**কেন hybrid:** UI টেস্ট ধীর আর ভঙ্গুর, তাই precondition (shipping, delivery) API দিয়ে তৈরি করা হয়। আর `loginWithToken` লগইন পেজ এড়িয়ে localStorage-এ JWT বসিয়ে দেয়। শুধু লগইন পরীক্ষা করার টেস্টই আসল login ফর্ম ব্যবহার করে।

**কেন নতুন পণ্য:** প্রতিটা টেস্ট নিজের পণ্য বানায় (`Fixtures.namedProduct("30.00", 10)`), যার দাম আর স্টক জানা। তাই অন্য টেস্টে স্টক বদলালেও total নির্দিষ্ট থাকে, আর টেস্টগুলো একে অপরের উপর নির্ভর করে না।

## নিজের Mac-এ চালানো
```bash
cd ~/Eclipse-Workspace-QA/RbcTcsWorld
git pull
docker compose up -d
mvn -f backend/pom.xml spring-boot:run          # টার্মিনাল ১ (8081)
cd frontend && npm install && npm run dev       # টার্মিনাল ২ (5173)
# টার্মিনাল ৩:
mvn -f automation/pom.xml clean test -Dgroups=ui -Dheadless=false   # ব্রাউজার চলতে দেখা যাবে
mvn -f automation/pom.xml allure:serve
```
Eclipse থেকে চালাতে চাইলে যেকোনো `Ui*Test` ক্লাসে right-click → Run As → JUnit Test। VM argument হিসেবে `-Dheadless=false` দিলে ব্রাউজার চলতে দেখা যাবে।

## CI
GitHub Actions-এর ধাপগুলো:
1. Node 20-এ frontend build হয়।
2. `vite preview` frontend চালু করে (5173), আর `/api` অনুরোধগুলো 8081-এর backend-এ পাঠায়।
3. Runner-এ আগে থেকে থাকা Chrome headless মোডে সব টেস্ট চালায়: API, DB, BDD আর UI।

UI টেস্ট fail করলে Allure রিপোর্টে screenshot আর URL থাকে।

## Cross-browser: Chrome, Firefox আর Edge
- **কোথায় কোন browser:**
  - **Chrome:** মূল automation job-এ চলে।
  - **Firefox আর Edge:** আলাদা CI job (`ui-cross-browser`, matrix) প্রতিটা push-এ চলে। দুটোতেই ১২টা UI journey আর ৮টা accessibility পাতা, মোট ২০টা টেস্ট।
- **`fail-fast: false`:** একটা browser fail করলেও অন্যটার ফল দেখা যায়।
- **একই viewport:** সব browser-এ 1366×900, ফলে layout আর screenshot তুলনা করা যায়।
- **Jenkins:** নতুন parameter **BROWSER** (chrome, firefox, edge, safari)। Mac-এ Safari চালাতে একবার `sudo safaridriver --enable` চালাতে হয়, আর Safari-র headless মোড নেই।
- **Local-এ:** `mvn -f automation/pom.xml test -Dgroups=ui -Dbrowser=firefox`

**DEF-019:** প্রথম run-এ Firefox-এ ২০টাই পাস করেছিল, কিন্তু Edge-এ ২০টাই "Chrome instance exited" error দিয়ে চালুই হয়নি।
- **কারণ:** Ubuntu 24.04 CI runner-এ Edge-এর sandbox চালু হতে পারছিল না। Chrome-এর জন্য AppArmor profile আছে, Edge-এর নেই।
- **সমাধান:** শুধু CI-তে (`CI` environment variable থাকলে) Edge `--no-sandbox` দিয়ে চলে। Developer-এর মেশিনে sandbox যেমন ছিল তেমনই থাকে।
- **ফল:** Edge-এও ২০/২০ পাস।
- **পরে আরেকটা flaky সমস্যা (merge-এর আগেই ধরা):**
  - পরের একটা run-এ Edge-এর একটা টেস্ট window-এর আকার বদলাতে গিয়ে timeout হয়েছিল। এই resize-টা এই branch-এই যোগ করা হয়েছিল।
  - **সমাধান:** এখন browser চালু করার সময়েই argument দিয়ে আকার ঠিক করা হয় (`--window-size`)। চালুর পরে resize শুধু Safari-তে হয়।

**DEF-020 (Jenkins, Mac):** GitHub CI সবুজ থাকলেও Jenkins-এ `BROWSER=edge` আর `firefox`-এ ২০টা UI টেস্টই driver না পেয়ে ভেঙেছিল।
- **Edge:** Microsoft পুরনো driver download সাইট (`msedgedriver.azureedge.net`) বন্ধ করে দিয়েছে। Selenium 4.25-এর Selenium Manager তখনও সেখানেই খুঁজছিল।
- **Firefox:** Mac-এ Firefox install করা ছিল না। Selenium Manager নিজে browser নামাতে গিয়ে installer খুলতে পারেনি।
- **কেন GitHub-এ ধরা পড়েনি:** GitHub runner-এ browser আর driver আগে থেকেই install থাকে, তাই কিছু নামাতে হয় না।
- **সমাধান:** Selenium 4.49-এ upgrade (নতুন download ঠিকানা), আর Jenkins Mac-এ `brew install --cask firefox`।

## ইন্টারভিউতে যা বলবেন
> "UI layer-এ Page Object Model আর শুধু explicit wait ব্যবহার করেছি। Locator সব `data-testid`, তাই ডিজাইন বদলালেও টেস্ট টেকে। ধীর precondition আমি API দিয়ে তৈরি করি, আর UI দিয়ে শুধু সেই আচরণ যাচাই করি যেটা গ্রাহক দেখে। এতে UI suite দ্রুত আর স্থিতিশীল থাকে। পুরো কেনাকাটার পথ, মানে কুপন, declined কার্ড, পেমেন্ট, cancel আর return, প্রতিটা push-এ CI-তে headless Chrome, Firefox আর Edge তিনটাতেই চলে।"
