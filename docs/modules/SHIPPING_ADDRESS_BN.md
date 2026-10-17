# ডেলিভারির ঠিকানা (Checkout)

এখন প্রতিটা অর্ডারে একটা US ডেলিভারির ঠিকানা লাগে। ঠিকানা না দিলে অর্ডার হয় না। ঠিকানাটা অর্ডারের সাথেই কপি করে রাখা হয়, যাতে পরে গ্রাহক ঠিকানা বদলালেও যে ঠিকানায় পাঠানো হয়েছিল সেটা না বদলায়।

## কী বদলেছে

| অংশ | পরিবর্তন |
|---|---|
| Database | `V12__order_shipping_address.sql`: `orders` টেবিলে `ship_name`, `ship_line1`, `ship_line2`, `ship_city`, `ship_state`, `ship_zip`, `ship_phone`। পুরনো অর্ডারে এগুলো খালি (NULL)। |
| API | `POST /api/orders` এখন `shippingAddress` নেয়। না দিলে **400 "Shipping address is required"**, ভুল ঘর থাকলে **400 "Validation failed"** আর `fieldErrors`-এ ঘরের নাম (যেমন `shippingAddress.zip`)। |
| উত্তর | অর্ডারের JSON-এ `shippingAddress` (পুরনো অর্ডারে `null`)। Admin-এর অর্ডার তালিকাতেও আছে। |
| গোপনীয়তা | Public tracking পাতা (`/api/tracking/{নম্বর}`) কখনো ঠিকানা দেখায় না, কারণ tracking নম্বর পার্সেলের গায়ে লেখা থাকে, যে কেউ দেখতে পায়। অন্য গ্রাহক অর্ডার খুললে 404। |
| ইমেইল | "Order received" ইমেইলে `Ships to: ...` লাইন। ফোন নম্বর ইমেইলে যায় না। |
| Storefront | Checkout পাতায় ঠিকানার form (৬ ভাষায় label), রাজ্যের তালিকা, ঘরের নিচে ভুলের বার্তা। পরের বার আগের অর্ডারের ঠিকানা নিজে থেকে বসে যায়। অর্ডারের পাতায় "Ships to"। |

## ঠিকানার নিয়ম

| ঘর | নিয়ম |
|---|---|
| `fullName` | আবশ্যক, ১–৮০ অক্ষর |
| `line1` | আবশ্যক, ১–১০০ |
| `line2` | ঐচ্ছিক, ১০০ পর্যন্ত (অ্যাপার্টমেন্ট, স্যুট) |
| `city` | আবশ্যক, ১–৬০ |
| `state` | আবশ্যক, ৫০টা রাজ্য বা DC-এর ২ অক্ষরের কোড। ছোট হাতের `pa` চলে, সংরক্ষণ হয় `PA` হিসেবে |
| `zip` | আবশ্যক, `12345` বা `12345-6789` |
| `phone` | ঐচ্ছিক, ৭–২০টা অঙ্ক বা `+ ( ) - .` |

সব ঘর trim হয়ে জমা হয়। ফাঁকা `line2` বা `phone` জমা হয় "দেওয়া হয়নি" (null) হিসেবে।

> **ডিজাইনের সিদ্ধান্ত:** OpenAPI-তে `shippingAddress` ঐচ্ছিক রাখা হয়েছে, কারণ একই body দিয়ে `POST /api/checkout/quote`-ও চলে, আর সেখানে ঠিকানা লাগে না। "অর্ডারে ঠিকানা আবশ্যক" নিয়মটা service-এ। এর মানে, contract gate (openapi-diff) শুধু আকার দেখে, নিয়ম দেখে না। নিয়মের পরিবর্তন তাই টেস্ট আর এই doc দিয়ে ধরা হয়েছে। Interview-এ বলার মতো একটা ভালো পয়েন্ট।

## টেস্ট (RTM: REQ-ORD-07 থেকে REQ-ORD-10)

| স্তর | টেস্ট |
|---|---|
| Unit | `ShippingAddressTest`: প্রতিটা নিয়ম, সীমার ঠিক আগে আর পরে (৮০/৮১ অক্ষর), trim, `oneLine()`। `OrderServiceTest`: ঠিকানা ছাড়া কিছুই save হয় না। `NotificationServiceTest`: ইমেইলে ঠিকানা আছে, ফোন নেই |
| Integration | `OrderIntegrationTest`: 400 আর `fieldErrors`, database-এ `PA 15213-0001`, অন্য গ্রাহক 404, admin দেখতে পায়। `FulfillmentReturnIntegrationTest`: public tracking-এ ঠিকানা নেই |
| API | `ShippingAddressApiTest`: ১০টা ভুল ঘরের parameterized টেস্ট, গ্রহণযোগ্য সীমার ক্ষেত্র, HTML শুধু লেখা হিসেবে ফেরে, কে ঠিকানা দেখে |
| Database | `OrderDatabaseTest`: `ship_*` কলামে ঠিক মান |
| Contract | `order.json`, `order-list.json`: `shippingAddress` কঠোর schema (অজানা ঘর নিষেধ) |
| UI (Selenium) | `UiCheckoutAddressTest`: খালি form-এ প্রতিটা আবশ্যক ঘরের বার্তা, server-এর ZIP বার্তা ঘরের নিচে, অর্ডার পাতায় "Ships to", পরের checkout-এ আগের ঠিকানা |
| Playwright | `checkout.spec.ts`: ঘরগুলো খোঁজে তাদের **label** দিয়ে (screen reader যেভাবে পড়ে), খালি form-এ কোনো request যায় না, API-র উত্তরে trim করা ঠিকানা। ফোনেও চলে (`@mobile`) |
| k6 / Appium | সব অর্ডারে ঠিকানা যায়। ফোনে রাজ্য বেছে নেওয়া হয় JavaScript দিয়ে, কারণ ফোনের নিজের picker চাকা automation-এ নির্ভরযোগ্য না |

## নিজে চেষ্টা করে দেখুন

```bash
TOKEN=...   # login-এর token
curl -s -X POST localhost:8081/api/orders -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
     -d '{"shippingAddress":{"fullName":"Ada","line1":"12 Main St","city":"Pittsburgh","state":"ZZ","zip":"ABC"}}'
# 400  {"message":"Validation failed","fieldErrors":{"shippingAddress.state":"must be a US state code such as PA",
#       "shippingAddress.zip":"must be a US ZIP code: 12345 or 12345-6789"}}
```
