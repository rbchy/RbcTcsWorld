# ধাপ ৪: Performance টেস্ট (k6)

## কেন k6
- টেস্ট লেখা হয় JavaScript-এ, তাই code review আর git-এ সহজে রাখা যায়।
- একটাই binary, CI-তে চালানো সহজ।
- Threshold (pass/fail-এর সীমা) কোডের ভেতরেই লেখা থাকে। Fail হলে exit code 99 আসে, আর CI লাল হয়ে যায়।
- JMeter-এর মতো GUI লাগে না।

## ছয়টা টেস্ট, প্রতিটা একটা আলাদা প্রশ্নের উত্তর দেয়
| টেস্ট | প্রশ্ন | আকার |
|---|---|---|
| **Smoke** | স্ক্রিপ্ট আর সিস্টেম আদৌ ঠিকমতো চলছে কি? | ১ জন browser + ১ জন buyer, ৩০ সেকেন্ড, **প্রতিটা CI build-এ** |
| **Load** | স্বাভাবিক ব্যস্ত সময়ে আমাদের লক্ষ্য (SLO) পূরণ হয় কি? | ৪০ জন browse করছে + ১০ জন কিনছে; ২ মিনিট ramp-up, ৫ মিনিট hold |
| **Stress** | কোথায় গিয়ে সিস্টেম ভেঙে পড়ে, আর পরে স্বাভাবিক হয় কি? | ধাপে ধাপে ২০০ + ৫০ জন; error ১০% ছাড়ালে নিজেই থেমে যায় |
| **Spike** | হঠাৎ ভিড় এলে টেকে কি? | ১০ সেকেন্ডে ৫ জন থেকে ২৫০ জন, তারপর recovery |
| **Soak** | দীর্ঘ সময় চললে ধীর হয়ে যায় কি? (memory leak, connection pool, token-এর মেয়াদ) | ২০ + ৫ জন, ৩০ মিনিট থেকে ১ ঘণ্টা |
| **Flash sale** | একসাথে অনেক অর্ডারেও হিসাব **সঠিক** থাকে কি? | ১০০ জন একই মুহূর্তে ২০টা পণ্যের জন্য অর্ডার দেয়, **প্রতিটা CI build-এ** |

## বাস্তব ব্যবহারকারীর মতো আচরণ
- **Browse (৮০%):** লগইন ছাড়া ক্যাটালগ দেখা, search, পণ্যের পেজ, review।
- **Buy (২০%):**
  - প্রতিটা virtual user প্রথমবার নিজে রেজিস্টার করে। ফলে signup-এর bcrypt খরচও load-এর অংশ হয়।
  - তারপর কার্ট → quote → অর্ডার → 4242 কার্ডে পেমেন্ট।
- **Think time:** প্রতিটা কাজের মাঝে ১-৩ সেকেন্ড বিরতি থাকে, যাতে মানুষের মতো আচরণ হয়, যন্ত্রের মতো একটানা loop না।

## শুধু গতি নয়, সঠিকতাও
- প্রতিটা উত্তরে **check** চলে, যেমন:
  - search-এর ফলাফলে খোঁজা শব্দটা আছে।
  - reviewCount = review তালিকার দৈর্ঘ্য।
  - অর্ডারের status PAID।
- **`business_errors`:** অর্ডারের total আর quote-এর total না মিললে এটা গোনা হয়। এর সীমা < 1%, stress টেস্টেও।
- **Flash sale-এর নিয়ম (invariant):**
  - ঠিক STOCK সংখ্যক অর্ডার 201 পাবে, বাকিরা 409।
  - 500 বা timeout একটাও হবে না।
  - শেষে stock **ঠিক 0**, কখনো ঋণাত্মক না।
  - এখানে 409 হলো **সঠিক উত্তর**, তাই `responseCallback` দিয়ে এটাকে failed request হিসেবে ধরা হয় না।

## SLO (threshold)
| মাপ | সীমা |
|---|---|
| Read request-এর p95 (ক্যাটালগ, search, পণ্য, review) | < 500 ms |
| Write request-এর p95 (কার্ট, অর্ডার, পেমেন্ট) | < 1000 ms |
| পুরো কেনাকাটার p95 (think time বাদে) | < 3000 ms |
| Failed request | < 1% |
| Check pass | > 99% |

**p95 কেন, গড় (average) কেন নয়:** গড় ধীর request-গুলোকে লুকিয়ে ফেলে। p95 মানে ১০০ জনের মধ্যে ৯৫ জন এর চেয়ে দ্রুত উত্তর পেয়েছেন।

## কিছু কৌশল (interview-তে জিজ্ঞেস করে)
- **URL grouping:** `/api/orders/123/pay`-এর মতো id-ওয়ালা URL-গুলোকে `name` tag দিয়ে একটা series-এ রাখা হয় (`/api/orders/{id}/pay`)। নাহলে প্রতিটা id আলাদা metric হয়ে যেত (high cardinality)।
- **`kind` tag (read/write):** read আর write-এর জন্য আলাদা সীমা রাখা যায়।
- **নিজস্ব HTML রিপোর্ট:** internet ছাড়াই তৈরি হয়। থাকে threshold-এর PASS/FAIL, endpoint অনুযায়ী p50/p90/p95/p99, আর business metric।
- **আলাদা পণ্য:** load টেস্ট নিজের পণ্য বানায়, যার stock ১০ লাখ। ফলে টেস্ট stock শেষ হওয়ার কারণে fail হয় না, শুধু গতি মাপে।

## চালানো (Mac)
```bash
brew install k6
cd ~/Eclipse-Workspace-QA/RbcTcsWorld
# backend চালু থাকতে হবে (8081)
k6 run performance/tests/smoke.js
k6 run performance/tests/flash-sale.js
k6 run performance/tests/load.js              # প্রায় ৮ মিনিট
open performance/reports/load-report.html
```
**GitHub-এ:**
- Smoke আর flash-sale প্রতিটা push-এ নিজে থেকে চলে। রিপোর্ট পাওয়া যায় `k6-reports` artifact-এ।
- Load, stress, spike আর soak চালাতে: **Actions → Performance (manual) → Run workflow**, তারপর টেস্ট বেছে নিন।
- টেস্টের পরে database-ও যাচাই হয়: অর্ডারের সংখ্যা, আর কোনো পণ্যের stock ঋণাত্মক হয়েছে কি না।

## ফলাফল কীভাবে পড়বেন
1. প্রথমে **threshold-এর তালিকা** দেখুন। FAIL থাকলে সেটাই মূল খবর।
2. তারপর **endpoint-এর তালিকা**, যেখানে সবচেয়ে ধীর p95 উপরে থাকে। সাধারণত register (bcrypt) আর order (row lock) সবচেয়ে ধীর হয়, আর সেটা প্রত্যাশিত।
3. **Stress:** কোন ধাপে p95 লাফিয়ে বেড়েছে, সেটাই সিস্টেমের সীমা।
4. **Soak:** শুরুর আর শেষের latency তুলনা করুন। ধীরে ধীরে বাড়তে থাকলে কোথাও leak আছে।
5. GitHub runner-এ মাত্র ২টা CPU থাকে। তাই সংখ্যাগুলো একটা রানের সাথে আরেকটা রানের তুলনার জন্য, production-এর সাথে তুলনার জন্য নয়।

## ইন্টারভিউতে যা বলবেন
> "আমি performance-কে শুধু গতি হিসেবে দেখি না। প্রতিটা k6 টেস্টে functional check আর business metric আছে, তাই load-এর মধ্যে total ভুল হলে টেস্ট fail করে। Flash-sale টেস্ট প্রমাণ করে চাপের মধ্যেও overselling হয় না। SLO-গুলো threshold হিসেবে কোডে লেখা, smoke আর flash-sale প্রতিটা CI build-এ চলে, আর ভারী টেস্টগুলো দরকারমতো হাতে চালানো যায়।"
