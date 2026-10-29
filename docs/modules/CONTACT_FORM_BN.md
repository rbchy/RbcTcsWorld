# যোগাযোগ ফর্ম (Contact us) আর Admin → Messages

আসল দোকানে গ্রাহক অর্ডার, ফেরত বা পণ্য নিয়ে প্রশ্ন করবেন, আর মালিককে প্রতিটা প্রশ্ন দেখে উত্তর দিতে হবে।
এখন প্রতিটা পাতার নিচে (footer) আর মেনুতে **Contact us** আছে, আর admin-এর জন্য **Messages** পাতা।
কেন এই সিদ্ধান্ত: [ADR-0018](../adr/0018-contact-form.md)।

## গ্রাহক কী দেখেন

1. **Contact us** খুলে নাম, ইমেইল, বিষয় (অর্ডার, ফেরত, পণ্য, পেমেন্ট, অন্য কিছু), চাইলে অর্ডার নম্বর, আর বার্তা লেখেন।
2. পাঠালে একটা নম্বর পান, যেমন `MSG-7K3P9Q`, আর লেখা থাকে কত কর্মদিবসের মধ্যে উত্তর আসবে।
3. ইমেইলে একটা নিশ্চিতকরণ আসে। তাতে শুধু নম্বর থাকে, বার্তার লেখা থাকে না (কেন, নিচে দেখুন)।
4. লগইন করা থাকলে ইমেইল লিখতে হয় না: account-এর ঠিকানাতেই উত্তর যায়।

## মালিক কী করেন

1. নতুন বার্তা এলে ইমেইল পান (`STORE_CONTACT_EMAIL`, না দিলে admin-এর ঠিকানায়)।
2. Admin → **Messages**-এ সব বার্তা: নতুনগুলো আগে, অবস্থা অনুযায়ী ছাঁকা যায় (New, Answered, Closed)।
3. **Answer** চাপলে উত্তর লিখে **Send answer**। উত্তরটা গ্রাহককে ইমেইলে যায়, নিচে তাঁর নিজের বার্তা উদ্ধৃত থাকে।
   কে কখন উত্তর দিলেন সেটা রাখা হয়। প্রতিটা বার্তায় একটাই উত্তর; এরপর কথা চললে ইমেইলেই চলে।
4. কাজ শেষ হলে **Close**।

## Setting

| Setting | Default | মানে |
|---|---|---|
| `STORE_CONTACT_EMAIL` | খালি | নতুন বার্তার খবর এই ঠিকানায় যায়; খালি থাকলে `ADMIN_EMAIL`-এ। নীতিমালার পাতায় আর contact পাতায়ও দেখায় |
| `STORE_REPLY_DAYS` | 2 | ফর্মে আর নিশ্চিতকরণ ইমেইলে "N কর্মদিবসের মধ্যে উত্তর" |

## Spam ঠেকানো (CAPTCHA ছাড়া)

| ব্যবস্থা | কীভাবে কাজ করে |
|---|---|
| Honeypot | ফর্মে একটা লুকানো ঘর আছে, মানুষ দেখেন না, screen reader-ও পড়ে না। bot সব ঘর ভরে। ভরা থাকলে উত্তরে "পাঠানো হয়েছে" দেখায়, কিন্তু কিছুই সেভ বা পাঠানো হয় না, তাই bot বুঝতেও পারে না |
| এক ঠিকানা থেকে ঘণ্টায় ৩টা | চতুর্থটায় 429, "এক ঘণ্টা পরে লিখুন" |
| পুরো দোকানে ঘণ্টায় ২০০টা | হঠাৎ বন্যা এলে মালিকের inbox আর ইমেইলের সুনাম বাঁচে |
| নিশ্চিতকরণে বার্তার লেখা নেই | না হলে যে কেউ অন্যের ঠিকানা দিয়ে দোকানের domain থেকে নিজের লেখা পাঠাতে পারত (spam relay) |
| লগইন থাকলে account-এর ঠিকানা | অন্যের নামে লেখা যায় না |

## API

```bash
curl -X POST https://<domain>/api/contact -H 'Content-Type: application/json' \
  -d '{"name":"Ada","email":"ada@example.com","topic":"RETURN","orderNumber":"ORD-12","message":"Can I return the mug?"}'
# 201 {"ticket":"MSG-7K3P9Q","replyDays":2}

curl https://<domain>/api/admin/messages?status=NEW -H "Authorization: Bearer $ADMIN"
curl -X POST https://<domain>/api/admin/messages/5/reply -H "Authorization: Bearer $ADMIN" \
  -H 'Content-Type: application/json' -d '{"text":"Yes, within 30 days."}'
curl -X POST https://<domain>/api/admin/messages/5/close -H "Authorization: Bearer $ADMIN"
```

| উত্তর | মানে |
|---|---|
| 400 | নাম নেই, ভুল ইমেইল, অজানা বিষয়, ১০ অক্ষরের কম বার্তা, অর্ডার নম্বরে অন্য চিহ্ন |
| 409 | আগেই উত্তর দেওয়া বা বন্ধ |
| 429 | এক ঠিকানা থেকে ঘণ্টায় ৩টার বেশি, বা পুরো দোকানে ২০০টার বেশি |

## টেস্ট (REQ-CS-01..04)

| স্তর | টেস্ট |
|---|---|
| Unit | `ContactServiceTest`: নম্বর, ঠিকানা, honeypot, সীমা, ইমেইলের লেখা, একবার উত্তর |
| Integration | `ContactIntegrationTest`: আসল API, database আর outbox |
| API | `AccessControlMatrixTest`: কে কী ডাকতে পারে |
| UI | `playwright/tests/contact.spec.ts`: footer থেকে ফর্ম, লগইন অবস্থায়, admin-এর উত্তর আর বন্ধ করা |

**Interview-এ:** "পাবলিক ফর্মকে আমি spam relay হওয়া থেকে আটকেছি: নিশ্চিতকরণ ইমেইলে লেখা নেই, লগইন থাকলে ঠিকানা account থেকে, honeypot আর সীমা। CAPTCHA রাখিনি, কারণ সেটা প্রতিটা ভিজিটরকে একটা তৃতীয় কোম্পানির কাছে পাঠায় আর ফর্ম কঠিন করে।"
