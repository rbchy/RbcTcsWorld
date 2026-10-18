# Order invoice: PDF ডাউনলোড

## সমস্যা
অর্ডারের পর ক্রেতা, অফিস বা হিসাবরক্ষকের একটা কাগজ লাগে যাতে কী কেনা হলো, কত দাম, কর কত, কোথায় যাবে সব লেখা থাকে। Amazon বা Alibaba-তে এই "Invoice" PDF থাকে।

## কী যোগ হলো
- `GET /api/orders/{id}/invoice`: অর্ডারের invoice **PDF ফাইল** হিসেবে আসে। ফাইলের নাম `invoice-ORD-….pdf`।
- Invoice-এ আছে: দোকানের নাম, অর্ডার নম্বর, তারিখ, অবস্থা (**paid** অথবা **payment due**), ঠিকানা, প্রতিটা লাইন (SKU, নাম, সংখ্যা, দাম), তারপর Subtotal, Discount (কুপনসহ), Shipping, Tax আর Total।
- অর্ডার ছোট হলে ১ পাতা। লাইন বেশি হলে পরের পাতায় চলে, প্রতি পাতার নিচে "page 2 of 3"।
- Order page-এ "Download invoice (PDF)" বাটন। ৬টা ভাষাতেই লেখা আছে।

## নিরাপত্তা
- শুধু অর্ডারের মালিক পান। অন্যের অর্ডারের id দিলে **404** (403 নয়, যাতে id আন্দাজ করে বের করা না যায়)। login ছাড়া **401**।
- Invoice কখনো cache হয় না (`Cache-Control: no-store`)।
- বাটন টিপলে browser আলাদা tab খোলে না। Login token দিয়ে ফাইল এনে সরাসরি ডাউনলোড করে।

## নকশার সিদ্ধান্ত: PDF library ছাড়া
- PDF ফাইল আসলে একটা টেক্সট ফাইল: কয়েকটা object, একটা cross-reference তালিকা (প্রতিটা object ফাইলের কত byte-এ আছে) আর শেষে `%%EOF`। Invoice-এ শুধু লেখা, একটা রঙিন ব্যান্ড আর সরু দাগ, তাই বাইরের library লাগেনি। এতে নতুন dependency, তার CVE আর license ঝামেলা নেই।
- দুটো standard font (Helvetica আর Helvetica-Bold) সব PDF viewer-এ থাকে, তাই font ফাইল ঢোকাতে হয়নি।
- Latin-1-এর বাইরের অক্ষর (যেমন চীনা নাম) `?` হিসেবে ছাপা হয় এবং `( ) \` চিহ্ন escape হয়, যাতে ক্রেতার নাম দিয়ে ফাইল ভাঙা না যায়।
- ঠিকঠাক ফাইল হয়েছে কি না যাচাই: unit test নিজেই ফাইলের কাঠামো পরীক্ষা করে। প্রতিটা cross-reference সংখ্যা সত্যিই `N 0 obj`-এর জায়গায় যায় কি না, প্রতিটা stream-এর `/Length` আসল দৈর্ঘ্যের সমান কি না। এছাড়া লেখার সময় আমি আসল ফাইল বানিয়ে `pypdf`, `pdftotext` আর `qpdf --check` দিয়ে দেখেছি।
- সীমা: শুধু ইংরেজি (Latin) অক্ষর, ছবি বা লোগো নেই। বাংলা বা চীনা invoice লাগলে font ঢোকানো PDF library লাগবে, সেটা ভবিষ্যতের কাজ।

## টেস্ট
| স্তর | ফাইল |
|---|---|
| Unit | `InvoicePdfTest` (৮) |
| Integration | `InvoiceIntegrationTest` (২) |
| API | `InvoiceApiTest` (৩) |
| BDD | `invoice.feature` (২ scenario) |
| UI | `playwright/tests/invoice.spec.ts` (ফাইল সত্যিই নামে, ফোনসহ) |

Traceability matrix-এ নতুন: **REQ-ORD-11**।
