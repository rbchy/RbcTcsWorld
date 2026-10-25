# Module 5: Reviews, Ratings আর Wishlist

## ব্যবসার নিয়ম

### Review
| নিয়ম | ভাঙলে |
|---|---|
| শুধু সেই গ্রাহক review দিতে পারবেন যিনি পণ্যটা **হাতে পেয়েছেন**, মানে অর্ডার DELIVERED, RETURN_REQUESTED বা RETURNED অবস্থায় আছে। শুধু PAID বা SHIPPED হলে চলবে না। | 403 |
| একজন গ্রাহক একটা পণ্যে একটাই review দিতে পারবেন। চাইলে নিজের review edit বা delete করতে পারবেন। | 409 |
| Rating ১ থেকে ৫, title সর্বোচ্চ ১০০ অক্ষর, body সর্বোচ্চ ২০০০ অক্ষর। ফাঁকা title বা body null হিসেবে রাখা হয়। | 400 |
| অন্য গ্রাহকের review edit বা delete করতে চাইলে "পাওয়া যায়নি" দেখায় (IDOR থেকে সুরক্ষা)। | 404 |
| Public তালিকায় reviewer-এর নাম mask করা থাকে ("qa***")। ইমেইল বা userId কখনো দেখানো হয় না। | নিরাপত্তা |
| Admin কোনো review hide করলে সেটা তালিকা আর average দুই জায়গা থেকেই বাদ যায়। লেখক নিজে "mine"-এ সেটা HIDDEN হিসেবে দেখতে পান। আবার publish করলে review ফিরে আসে। | moderation |
| Sort-এর মান হতে হবে `newest`, `highest` বা `lowest`। অন্য কিছু দিলে error। | 400 |

### Rating-এর হিসাব
- **Average:** এক দশমিক পর্যন্ত, **HALF_UP** rounding। যেমন 4, 4, 5 → 4.333 → **4.3**, আর 1, 1, 1, 2 → 1.25 → **1.3** (banker's rounding হলে 1.2 হতো)।
- Average আর count `products` টেবিলেই রাখা হয় (denormalized), যাতে product তালিকা দ্রুত লোড হয়।
- **Race condition:** দুজন একই মুহূর্তে review দিলে দুটো transaction-ই অন্যটার review বাদ দিয়ে average হিসাব করত, ফলে ভুল সংখ্যা জমা হতো (lost update)। সমাধান হলো product row-এ `SELECT ... FOR UPDATE` lock নিয়ে তারপর হিসাব করা। `ReviewConcurrencyTest` ৮ জনকে একসাথে review দিতে দিয়ে এটা প্রমাণ করে।

### Wishlist
| নিয়ম | আচরণ |
|---|---|
| একই পণ্য দুবার যোগ করলে নতুন লাইন হয় না (idempotent)। | প্রথমবার 201, পরেরবার 200 |
| সর্বোচ্চ ৫০টা পণ্য রাখা যায়। | ৫১তম পণ্যে 400 |
| যোগ করার সময়ের দাম রেখে দেওয়া হয়। | দাম কমলে `priceDrop` দেখায় |
| Move to cart করলে পণ্য কার্টে ১টা হিসেবে যায় আর wishlist থেকে সরে যায়, পুরোটা এক transaction-এ। | স্টক না থাকলে 409, আর পণ্য **wishlist-এই থেকে যায়** (rollback) |
| বন্ধ (inactive) পণ্য যোগ করা যায় না। আগে থেকে wishlist-এ থাকলে "unavailable" দেখায়। | 404 |

## API
```
GET    /api/products/{id}/reviews?sort=newest|highest|lowest   (public)
POST   /api/reviews                {productId, rating, title?, body?}
PUT    /api/reviews/{id}           {rating, title?, body?}
DELETE /api/reviews/{id}
GET    /api/reviews/mine
GET    /api/admin/reviews?status=PUBLISHED|HIDDEN
POST   /api/admin/reviews/{id}/hide | /publish
GET    /api/wishlist
POST   /api/wishlist               {productId}
DELETE /api/wishlist/{productId}
POST   /api/wishlist/{productId}/move-to-cart
```
Database migration `V7__reviews_wishlist.sql` যোগ করে:
- `reviews` টেবিল, যেখানে `UNIQUE (user_id, product_id)` আর `CHECK rating 1..5` আছে।
- `products` টেবিলে `rating_average` আর `rating_count` কলাম।
- `wishlist_items` টেবিল, যেখানে `price_when_added` থাকে।

## Frontend
- **Product card:** star rating দেখায় (`product-rating`), আর ♡ বোতাম দিয়ে wishlist-এ রাখা যায়।
- **`#/products/{id}`:** গড় rating, ৫ থেকে ১ star পর্যন্ত distribution bar, sort, review তালিকা আর review লেখার ফর্ম আছে। Review-এ `<script>` লিখলেও সেটা সাধারণ লেখা হিসেবে দেখায়, চলে না (React escape করে)।
- **`#/wishlist`:** দাম কমলে "↓ $8.50 cheaper" দেখায়। স্টক না থাকলে "unavailable" দেখায় আর Move বোতাম বন্ধ থাকে। Move to cart আর Remove বোতামও আছে।

## টেস্ট
| স্তর | কী আছে |
|---|---|
| Unit (`ReviewServiceTest`, `WishlistServiceTest`) | Rounding-এর ৭টা কেস, name masking, ৪০৩/৪০৯/৪০৪ নিয়ম, hidden review edit করলেও hidden থাকে, শেষ review মুছলে rating 0.0 হয়, price drop, cart রাজি না হলে পণ্য wishlist-এ থেকে যায় |
| Integration (H2, `ReviewWishlistIntegrationTest`) | পুরো API, তার সাথে database-এর `rating_average` |
| API (`ReviewApiTest`, `WishlistApiTest`) | **Verified-purchase matrix** (NONE, PLACED, PAID, SHIPPED → 403, DELIVERED → 201), boundary value (rating 0/1/5/6, title 100/101, body 2000/2001), sorting, masking, XSS payload, IDOR, moderation, ৫০টার সীমা |
| Concurrency (`ReviewConcurrencyTest`) | ৮টা review একসাথে দিলে count 8 আর average 3.5 হয় |
| DB (`ReviewDatabaseTest`) | **প্রতিটা** পণ্যের `rating_count` আর `rating_average` review টেবিল থেকে নতুন করে হিসাব করা মানের সাথে মেলে কি না (drift query)। প্রতিটা review verified purchase কি না। Schema constraint। |
| BDD (`reviews_wishlist.feature`) | ৬টা scenario, যার মধ্যে একটা rating Scenario Outline |
| UI (`UiReviewWishlistTest`) | Verified buyer review দেয়, তারপর summary, distribution, sort আর card rating যাচাই হয়। যিনি পণ্য পাননি তিনি error দেখেন। Wishlist: price drop, sold-out অবস্থা, move to cart, আর cart-এর দাম |

**Flaky টেস্ট ঠিক করা:** CI-তে একবার "stale element" error এসেছিল। কারণ, React পেজ নতুন করে আঁকার সময় Selenium-এর হাতে থাকা পুরোনো element হারিয়ে যাচ্ছিল। সমাধানগুলো:
- সব explicit wait এখন `StaleElementReferenceException` উপেক্ষা করে।
- `text()` element খোঁজা আর লেখা পড়া একই retry ধাপে করে।
- তালিকার attribute একবারে পড়া হয়।

ইন্টারভিউতে বলার মতো: "flaky টেস্ট retry দিয়ে ঢাকিনি, root cause ঠিক করেছি।"

## নিজের Mac-এ চালানো
```bash
cd ~/Eclipse-Workspace-QA/RbcTcsWorld
git pull
docker compose up -d
mvn -f backend/pom.xml spring-boot:run             # Flyway নিজে V7 চালাবে
mvn -f automation/pom.xml clean test -Dgroups="reviews | wishlist"
mvn -f automation/pom.xml clean test -Dgroups=ui -Dheadless=false   # frontend চালু রাখুন (npm run dev)
```

## ইন্টারভিউতে যা বলবেন
> "Review module-এ তিনটা ঝুঁকির দিকে নজর দিয়েছি।
> - **Trust:** শুধু যে পণ্য হাতে পেয়েছে সেই review দিতে পারে, তাই PLACED থেকে DELIVERED পর্যন্ত প্রতিটা অবস্থা নিয়ে matrix টেস্ট আছে।
> - **Data integrity:** average একটা denormalized মান। Concurrency টেস্টে lost update ধরা পড়ে, আর DB drift query প্রতিটা পণ্যের জন্য average নতুন করে হিসাব করে মিলিয়ে দেখে।
> - **Privacy:** public response-এ ইমেইল থাকে না, আর XSS payload শুধু data হিসেবে ফেরত যায়।
>
> Wishlist-এ move-to-cart transaction-এর rollback আলাদাভাবে যাচাই করেছি: cart রাজি না হলে পণ্য wishlist থেকে হারায় না।"
