# গ্রাহক নিজেই account মুছতে পারেন

আগে account মুছতে গ্রাহককে ইমেইল করতে হতো, আর মালিককে হাতে মুছতে হতো। এখন গ্রাহক নিজেই পারেন।
কেন এভাবে: [ADR-0022](../adr/0022-self-service-account-deletion.md)।

> আমি আইনি পরামর্শদাতা নই। কতদিন অর্ডারের রেকর্ড রাখতে হবে, সেটা accountant বা lawyer-এর কাছে নিশ্চিত হয়ে নিন।

## গ্রাহক কী করেন

1. Sign in → **Security** → **Delete my account**।
2. বর্তমান password আর `DELETE` শব্দটা লেখেন → **Delete my account for good**।
3. Sign out হয়ে বিদায়ের পাতায় যান: "Your account was deleted."। পুরোনো ইমেইলে একটা শেষ নিশ্চিতকরণ ইমেইল যায়।

## কী মোছে, কী থাকে

| মুছে যায় | থাকে (আইনের জন্য) |
|---|---|
| Cart, wishlist, ঠিকানার তালিকা | অর্ডার আর তার ডেলিভারি ঠিকানা (চালান) |
| সংরক্ষিত কার্ড | পেমেন্ট আর refund |
| Review (পণ্যের rating আবার হিসাব হয়) | Return-এর রেকর্ড |
| সব session, password reset link, sign-in code | কোন coupon ব্যবহার হয়েছে |
| Contact form-এর বার্তা, পাঠানোর অপেক্ষায় থাকা ইমেইল | Bounce বা spam তালিকার ঠিকানা |

Account-এর ইমেইল বদলে `deleted-<id>@deleted.invalid` হয়ে যায়। এই ঠিকানায় কখনো মেইল যায় না। একই ইমেইল দিয়ে পরে নতুন account খোলা যায়।

## কখন মোছা যায় না

- কোনো অর্ডার PLACED, PAID, SHIPPED অবস্থায় থাকলে, বা return-এর সিদ্ধান্ত বাকি থাকলে (409)। আগে deliver বা cancel হতে হবে। নইলে পার্সেল বা refund-এর খবর দেওয়ার ঠিকানা থাকবে না।
- ভুল password, guest checkout, বা admin account (400)।

## মালিকের দিক থেকে

- Admin → Customers-এ মোছা account `deleted-…@deleted.invalid` নামে, বন্ধ (disabled) অবস্থায় দেখায়। পুরোনো অর্ডার আগের মতোই Admin → Orders-এ থাকে।
- Audit trail-এ `CUSTOMER_DELETED_ACCOUNT` লেখা থাকে, ইমেইল ছাড়া।
- কেউ ইমেইলে মুছতে বললে: তাঁকে Security পাতার পথ দেখিয়ে দিন। তিনি sign in করতে না পারলে আগের মতো হাতে করবেন।

## টেস্ট (REQ-AUTH-17)

| স্তর | টেস্ট |
|---|---|
| Unit | `ReviewServiceTest` (review মোছা, rating আবার হিসাব), `NotificationServiceTest` (মোছা account-এ আর ইমেইল নয়) |
| Integration | `AccountDeletionIntegrationTest`: পুরো পথ, খোলা অর্ডারে 409, ভুল password, admin, login ছাড়া |
| Security | `AccessControlMatrixTest` |
| Browser | `playwright/tests/account-deletion.spec.ts` |
