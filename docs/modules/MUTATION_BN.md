# Mutation testing (PIT): টেস্টগুলো কতটা ভালো, তার পরীক্ষা

## ১. ধারণা
- **Coverage কী বলে:** কোডের কোন লাইন টেস্টের সময় **চলেছে**।
- **Coverage কী বলে না:** টেস্ট আসলে কিছু **যাচাই করেছে** কি না। কোনো assertion ছাড়াই একটা টেস্ট 100% coverage দিতে পারে।
- **PIT কী করে:** ব্যবসার কোডে ইচ্ছা করে ছোট ছোট "বাগ" ঢোকায়, যাদের বলে **mutant**। যেমন:
  - `>`-কে `>=` করে দেয়।
  - একটা `if`-কে সবসময় মিথ্যা বানায়।
  - কোনো method call মুছে দেয়।
  - return value-কে `null` বানায়।
- প্রতিটা mutant-এর জন্য PIT unit টেস্টগুলো আবার চালায়। ফল তিন রকম হতে পারে:
  - **Killed:** কোনো টেস্ট fail করেছে। ভালো, টেস্ট বাগটা ধরেছে।
  - **Survived:** সব টেস্ট সবুজ থেকেছে। অর্থাৎ কোড চলেছে, কিন্তু কোনো টেস্ট সেটা যাচাই করে না।
  - **No coverage:** কোনো unit টেস্ট ওই কোডে পৌঁছায়ই না। এখানে সেগুলো integration টেস্ট দিয়ে চলে।
- **দুটো মাপ:**
  - **Mutation score** = killed ÷ মোট mutant।
  - **Test strength** = killed ÷ যেসব mutant টেস্টের আওতায় আছে।

## ২. কীভাবে চালাবেন
```bash
mvn -f backend/pom.xml -Pmutation -Djacoco.skip=true test-compile org.pitest:pitest-maven:mutationCoverage
open backend/target/pit-reports/index.html
```
- **কোন কোডে চলে:** শুধু ব্যবসার কোডে। সব `*Service`, order state machine (`OrderStatus`, `CustomerOrder`), `ReturnPolicy`, `Coupon` আর `MockPaymentGateway`। Controller, DTO বা repository-তে চলে না।
- **কোন টেস্ট দিয়ে চলে:** শুধু দ্রুত unit টেস্ট। Spring integration টেস্ট দিলে প্রতিটা mutant-এর জন্য Spring চালু করতে হতো, তাতে অনেক বেশি সময় লাগত।
- **কোথায় চলে:**
  - **GitHub CI:** job "Mutation testing (PIT)", প্রতিটা push-এ। Score আর survived mutant-এর তালিকা run-এর পেজে দেখায়।
  - **অনলাইন রিপোর্ট:** https://rbchy.github.io/RbcTcsWorld/mutation/
  - **Jenkins:** stage "Mutation testing (PIT)", parameter `RUN_MUTATION` দিয়ে চালু বা বন্ধ। ফল "QA Reports" পাতায় দেখায়।
- **Gate:** mutation score ≥ **84%** (`pitest.mutationThreshold`)। Coverage-এর মতোই ratchet নিয়ম: বাড়ানো যাবে, কমানো যাবে না।

## ৩. ফলাফল: আগে আর পরে

| মাপ | প্রথম run | টেস্ট যোগ করার পর |
|---|---|---|
| Mutation score | **65.5%** (৩৭১টার মধ্যে ২৪৩টা killed) | **84.9%** |
| Test strength | 82.9% | **97.5%** |
| Survived mutant | ৫০টা | **৮টা** (সবগুলো এক এক করে পর্যালোচনা করা) |
| Backend টেস্ট | ২৪৯টা | **২৭৯টা** |
| Branch coverage (পার্শ্বফল) | 78.8% | **86.6%** |

এরপর gate-গুলো বাড়ানো হয়েছে (ratchet): JaCoCo লাইন 95→96%, branch 77→85%, আর নতুন mutation gate 84%।

## ৪. সবচেয়ে জরুরি আবিষ্কার: এমন একটা টেস্ট যা কখনো fail করতে পারত না (DEF-013)
- **কী survive করল:** `OrderService.pay()`-এর প্রথম লাইন `order.assertCanMoveTo(PAID)` মুছে ফেলার mutant।
  - এই লাইনটা নিশ্চিত করে যে ভুল অবস্থার order-এ টাকা কাটার **আগেই** 409 আসবে।
  - লাইনটা না থাকলে কার্ড থেকে টাকা আগে কেটে যায়, তারপর error আসে।
- **যে টেস্টের এটা ধরার কথা:** `paidOrderCannotBePaidAgain`, যেখানে লেখা ছিল `verify(payments, never()).charge(anyLong(), any(), any())`।
- **কেন ধরতে পারেনি:**
  - Mockito 5-এ `anyLong()` **`null` মেলায় না**।
  - টেস্টের order-এর কোনো id ছিল না, তাই `charge(null, …)` call-টা matcher-এর সাথে মেলেনি।
  - ফলে "never" সবসময় সত্য ছিল। টেস্টটা কখনোই fail করতে পারত না।
- **সমাধান:** `any()` ব্যবহার করা, যেটা null-ও মেলায়। Backend-এর সব `verify(..., never())`-এ একই নিয়ম লাগানো হয়েছে।
- **প্রমাণ:** লাইনটা মুছে টেস্ট চালিয়ে দেখা হয়েছে। সংশোধিত টেস্ট এখন fail করে। লাইন ফিরিয়ে দিলে আবার পাস করে।

**Interview-এ:**
> "আমাদের coverage ছিল 96%, কিন্তু mutation testing দেখাল একটা টেস্ট আসলে কিছুই পরীক্ষা করছিল না। 'never called' assertion-এ `anyLong()` ছিল, যেটা null মেলায় না, তাই টেস্টটা সবসময় পাস করত। এটা ঠিক করেছি, আর ৩০টা নির্দিষ্ট টেস্ট যোগ করেছি। তাতে mutation score 65% থেকে 85%-এ উঠেছে।"

## ৫. আর কী কী ধরা পড়ল (যোগ করা টেস্ট)

| কোথায় | Survived mutant কী দেখাল | নতুন টেস্ট |
|---|---|---|
| Cart | `updateItem`, `removeItem`, `clear`-এর কোনো unit টেস্ট ছিল না; 1..10 সীমা আর "available" flag যাচাই হতো না | সীমার দুই প্রান্ত, শেষ unit, বিক্রি বন্ধ product |
| Coupon | ব্যবহারের সীমা ঠিক `maxUses`-এ পৌঁছালে কী হয়, ন্যূনতম অঙ্ক ছাড়া coupon, `create()`-এর প্রায় সব নিয়ম | `usedCount = 1` আর `2` (সীমা 2), ঠিক 100% আর 100.01%, তারিখের ক্রম, একই code দুবার |
| Payment | শুধু খুব ছোট কার্ড নম্বর পরীক্ষা হতো; 13 আর 19 সংখ্যা বদলালেও কেউ টের পেত না | 12, 13, 19 আর 20 সংখ্যার Luhn-বৈধ নম্বর |
| JWT | 32-byte সীমা আর `null` secret | ঠিক 32 byte, 31 byte, আর `null` দিলে স্পষ্ট error (NullPointerException নয়) |
| Order | Coupon-সহ order, সফল payment, order আর item-এর সংযোগ | 10% coupon, যাতে free shipping-এর সীমার নিচে নেমে যায় (মোট 53.69); সফল payment-এ paidAt সেট হয় |
| Review | Delete-এর পর `flush()` মুছে দিলে গড় rating ভুল হয় | InOrder: delete → flush → নতুন গড় → save |
| Product | নিজের SKU রেখে update করা; page size 1 | দুটো নতুন টেস্ট |
| Wishlist, Return | "available" শুধু false হিসেবে পরীক্ষা হতো; null রিটার্ন ধরা পড়ত না | শেষ unit-এ available, response যাচাই, note ছাড়া প্রত্যাখ্যান |

## ৬. বাকি ৮টা survived mutant: কেন ঠিক আছে

| Mutant | ধরন | কারণ |
|---|---|---|
| `luhnValid`: `d > 9` থেকে `d >= 9` | Equivalent | দ্বিগুণ করা অঙ্ক সবসময় জোড়, তাই কখনো 9 হয় না |
| `luhnValid`: `sum += d` থেকে `sum -= d` | Equivalent | −sum 10 দিয়ে বিভাজ্য হলে sum-ও বিভাজ্য, ফল একই |
| `WishlistService.toLine`: `< 0` থেকে `<= 0` | Equivalent | শূন্যের জায়গায় শূন্যই বসে |
| `FulfillmentService.ship`: guard মুছে ফেলা | Equivalent | `order.ship()` নিজেই কিছু বদলানোর আগে একই নিয়ম পরীক্ষা করে |
| `AuthService.normalize`: null-চেক | রক্ষণাত্মক কোড | API-তে `@NotBlank` থাকায় null কখনো এখানে পৌঁছায় না |
| `LoginAttemptService` (৩টা) | Housekeeping আর টেস্টের helper | মেমরি পরিষ্কারের সীমা (১০,০০০ email), কোনো ব্যবসার নিয়ম নয় |

**Equivalent mutant:** এমন পরিবর্তন যা প্রোগ্রামের আচরণ আসলে বদলায় না, তাই কোনো টেস্টই একে ধরতে পারবে না। 100% mutation score তাই লক্ষ্য নয়। লক্ষ্য হলো প্রতিটা survived mutant-কে বোঝা।
