# Shippo: আসল shipping label আর tracking (দোকানের Phase 3, দ্বিতীয় অংশ)

আগে "Ship" চাপলে একটা বানানো tracking নম্বর তৈরি হত (যেমন `UPS-482910384756`)। এখন Shippo চালু করলে carrier-এর
আসল label কেনা হয়, অর্ডারে আসল tracking নম্বর বসে, আর প্যাকেট পৌঁছালে অর্ডার নিজে থেকেই DELIVERED হয়।
কেন এই সিদ্ধান্ত: [ADR-0013](../adr/0013-shippo-labels-and-tracking.md)।

## কীভাবে কাজ করে

```mermaid
sequenceDiagram
  participant A as Admin (Orders পাতা)
  participant S as দোকানের backend
  participant H as Shippo
  A->>S: Ship (carrier: USPS / UPS / FEDEX)
  S->>S: অর্ডার PAID কি না? না হলে 409, Shippo-কে ডাকা হয় না
  S->>H: POST /shipments (দোকানের ঠিকানা → গ্রাহকের ঠিকানা, একটা বাক্স)
  H-->>S: সব carrier-এর rate
  S->>H: POST /transactions (বেছে নেওয়া carrier-এর সবচেয়ে সস্তা rate)
  H-->>S: tracking নম্বর + label PDF
  S-->>A: SHIPPED; "Label" → "Print label ($7.58)"
  H->>S: webhook track_updated ?token=গোপন
  S->>H: GET /tracks (আসল অবস্থা Shippo থেকে পড়া)
  Note over S: শুধু DELIVERED হলে অর্ডার DELIVERED, গ্রাহক ইমেইল পান
```

- **Default `simulated`।** আগের মতোই বানানো নম্বর, কোনো label নেই। সব পুরোনো টেস্ট আর demo একই থাকে।
- **গ্রাহকের shipping চার্জ বদলায় না** ($5.99, $50-এর উপরে ফ্রি)। Label-এর দাম দোকানের খরচ, অর্ডারে `label_cost` হিসেবে থাকে।
- **শুধু PAID অর্ডারে label।** Rate না পেলে বা ঠিকানা carrier না মানলে 400, Shippo না পেলে 503। তখন label কেনা হয় না, অর্ডার PAID-ই থাকে।
- **webhook-এর নিরাপত্তা:** Shippo webhook-এ সই দেয় না, তাই URL-এ একটা গোপন token থাকে। token না থাকলে বা ভুল হলে 400।
  ঠিক token থাকলেও body-র কথা বিশ্বাস করা হয় না: অবস্থা Shippo থেকে আবার পড়া হয়। তাই নকল call দিয়ে কেউ অর্ডার DELIVERED করতে পারে না।

## কী সেট করতে হয়

| Variable | কোথা থেকে | কাজ |
|---|---|---|
| `SHIPPING_PROVIDER` | `shippo` | Shippo চালু |
| `SHIPPO_TOKEN` | goshippo.com → Settings → API → **Test** token (`shippo_test_...`) | test label ফ্রি |
| `SHIPPO_WEBHOOK_TOKEN` | নিজে বানান, লম্বা এলোমেলো লেখা | webhook URL-এর গোপন অংশ |
| `SHIP_FROM_NAME`, `SHIP_FROM_STREET`, `SHIP_FROM_CITY`, `SHIP_FROM_STATE`, `SHIP_FROM_ZIP`, `SHIP_FROM_PHONE` | দোকানের ফেরত ঠিকানা | প্রতিটা label-এ ছাপা হয়। এখন placeholder (Shippo-র নিজের docs-এর উদাহরণ ঠিকানা) |

বাক্সের মাপ আর প্রতি জিনিসের ওজন `application.yml`-এর `app.shipping.parcel`-এ (default 10 × 8 × 4 inch, জিনিস প্রতি 16 oz)। পণ্যের নিজের ওজন-মাপ দিলে সেটাই আগে (নিচে দেখুন)।

## পণ্যের ওজন আর বাক্সের মাপ

Admin → Products → পণ্য → **Edit** → **Shipping weight and box**:

- **Weight (oz)**: প্যাক করা অবস্থায় ওজন, বাক্সসহ। রান্নাঘরের বা ডাকঘরের স্কেলে মাপুন। 1 lb = 16 oz। সর্বোচ্চ 2400 oz (150 lb), দশমিকের পরে ১ ঘর।
- **Length, Width, Height (in)**: যে বাক্সে পাঠাবেন তার বাইরের মাপ। তিনটাই দিন, নয়তো কোনোটাই না। প্রতিটা সর্বোচ্চ 108 inch।
- খালি রাখলে আগের মতো: জিনিস প্রতি 16 oz আর 10 × 8 × 4 বাক্স।

Label কেনার সময় দোকান হিসাব করে:

| অর্ডারে | বাক্স | ওজন |
|---|---|---|
| ১টা জিনিস, নিজের বাক্সের মাপ দেওয়া | ওই বাক্সই | ওই জিনিসের ওজন |
| কয়েকটা জিনিস | default বাক্স, প্রতিটা দিক বড় করা হয় যাতে সবচেয়ে বড় জিনিসটা ঢোকে | সব জিনিসের ওজন যোগ (ওজন না দেওয়া থাকলে 16 oz করে) |

উদাহরণ: জুতা 28 oz, বাক্স 13 × 8 × 5। এক জোড়া অর্ডার হলে label 13 × 8 × 5, 28 oz। দুই জোড়া হলে 13 × 8 × 5, 56 oz।

একসাথে অনেক জিনিস হলে বাক্সের মাপ আন্দাজ (সব জিনিসের আয়তন যোগ হয় না)। বড় অর্ডারে carrier পরে মাপ ঠিক করে বাড়তি টাকা নিতে পারে, তাই এমন অর্ডারে label কেনার আগে আসল বাক্স মেপে দেখুন।

## Test mode-এ চালানো

1. goshippo.com-এ ফ্রি account খুলুন। Settings → API থেকে **test** token কপি করুন।
2. `.env`:
   ```bash
   SHIPPING_PROVIDER=shippo
   SHIPPO_TOKEN=shippo_test_...
   SHIPPO_WEBHOOK_TOKEN=একটা-লম্বা-গোপন-লেখা
   ```
3. `docker compose up --build`। একটা অর্ডার দিন আর pay করুন। তারপর Admin → Orders → carrier বেছে **Ship** চাপুন।
   অর্ডারে carrier-এর আসল ধরনের tracking নম্বর আসবে। **Label** চাপলে "Print label ($...)" link আসবে, সেটা Shippo-র sample label PDF।
4. Webhook (দোকান যখন ইন্টারনেটে, যেমন demo server): Shippo → Settings → Webhooks → Add,
   event `track_updated`, URL `https://<আপনার-domain>/api/shipping/shippo/webhook?token=<SHIPPO_WEBHOOK_TOKEN>`।
   নিজের কম্পিউটারে পরীক্ষা করতে `ngrok http 8081` দিয়ে একটা সাময়িক public URL নিন।

## এখনো বাকি

- কয়েকটা জিনিসের অর্ডারে বাক্সের মাপ আন্দাজ; ওজন-মাপ না দেওয়া পণ্যে default চলে। ভুল ওজনে carrier পরে বাড়তি টাকা কাটতে পারে।
- ফেরত ঠিকানা এখনো placeholder। কয়েকটা warehouse থেকে ভাগ হওয়া অর্ডারও এক প্যাকেট হিসেবে ওই ঠিকানা থেকে যায়।
- Label কেনার ঠিক পরে database বিফল হলে অর্ডার PAID থাকে আর label অব্যবহৃত পড়ে থাকে। Shippo অব্যবহৃত label-এর টাকা ফেরত দেয়; log-এ অর্ডার নম্বর থাকে।
- গ্রাহকের return-এর জন্য ফেরত label এখনো কেনা হয় না।

## টেস্ট কোথায়

| স্তর | টেস্ট | কী প্রমাণ করে |
|---|---|---|
| Unit | `ShippoClientTest` | Shippo-তে ঠিক কী JSON যায়, প্রতিটা উত্তর (rate, label, ভুল, অচেনা উত্তর) পড়া |
| Unit | `ShippoShippingProviderTest` | বেছে নেওয়া carrier-এর সবচেয়ে সস্তা rate, ওজন আর বাক্সের হিসাব, 400/503 |
| Unit | `ProductParcelTest` | পণ্যের ওজন-মাপ রাখা, audit, তিন দিক একসাথে |
| Unit | `FulfillmentServiceTest` | PAID ছাড়া label নয়, ব্যর্থ হলে অর্ডার PAID থাকে, শুধু Shippo DELIVERED বললে DELIVERED |
| Unit | `ShippoWebhookControllerTest`, `ShippingConfigTest` | token-এর নিয়ম, চালু হওয়ার নিয়ম |
| Integration | `ShippoShippingIntegrationTest`, `ShippingSimulatedIntegrationTest` | API আর database দিয়ে পুরো পথ |
| Security | `AccessControlMatrixTest` | label শুধু admin দেখে; token ছাড়া webhook 400 |
| Browser | `playwright/tests/shipping-label.spec.ts` | Admin-এর "Label" → "Print label ($7.58)" |
| Browser | `playwright/tests/shipping-box.spec.ts` | পণ্যের form-এ ওজন আর বাক্স, ভুল মাপে বার্তা |

Requirement: REQ-FUL-04, REQ-FUL-05, REQ-FUL-06 ([traceability](../qa/TRACEABILITY_MATRIX.md))।
