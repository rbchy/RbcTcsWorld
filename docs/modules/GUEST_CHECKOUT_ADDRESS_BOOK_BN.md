# Guest checkout আর Address book

## ১. Guest checkout: অ্যাকাউন্ট ছাড়াই কেনা

**সমস্যা:** নতুন ক্রেতাকে আগে অ্যাকাউন্ট খুলতে বাধ্য করলে অনেকে কেনাকাটা মাঝপথে ছেড়ে দেন। Amazon, Shein, Temu সবখানেই "Continue as guest" থাকে।

**নকশা:** Guest হলো একটা সাধারণ customer অ্যাকাউন্ট, শুধু তার পাসওয়ার্ডটা র‍্যান্ডম আর কেউ জানে না। `users` টেবিলে নতুন কলাম `guest` (`V19`)।

- `POST /api/auth/guest {email}` পাঠালে **201** আসে, সাথে একটা স্বাভাবিক session (JWT আর refresh token) এবং `"guest": true`।
- Cart, অর্ডার, পেমেন্ট আর অর্ডার দেখা, সব আগের কোড দিয়েই চলে। নতুন কিছু লিখতে হয়নি।
- পাসওয়ার্ড বানানো হয় ৩২ byte `SecureRandom` থেকে, তারপর bcrypt hash করা হয়। দুজন guest কখনো একই গোপন মান পায় না, আর লগইনে **401** আসে।
- **নিরাপত্তা:** যে ই-মেইলে আগে থেকে অ্যাকাউন্ট আছে, guest হিসেবে সেটা দিলে **409** আসে।
  - আসল অ্যাকাউন্ট থাকলে বার্তা: "Please log in"।
  - আগের guest হলে বার্তা: "Forgot password ব্যবহার করুন"।
  - না হলে যে কেউ অন্যের ই-মেইল টাইপ করেই তার অর্ডার দেখে ফেলতে পারত।
- **অ্যাকাউন্টে বদলানো:** guest "Forgot password" দিয়ে পাসওয়ার্ড বানালে `PasswordResetService` তাকে `claim()` করে। তখন সে পুরো customer হয়ে যায়, আর তার আগের অর্ডারগুলো থেকে যায়।
- **Frontend:**
  - Login পাতায় "New here? Check out as a guest" ঘর, ই-মেইলের validation-সহ।
  - Top bar-এ নীল "Guest" ব্যাজ।
  - Checkout-এ অর্ডার রাখার পরামর্শ, সাথে Forgot password লিংক।

## ২. Address book: ঠিকানা সেভ করে রাখা

- নতুন টেবিল `customer_addresses`। প্রতি customer সর্বোচ্চ **১০টা** ঠিকানা রাখতে পারেন, প্রতিটার ঐচ্ছিক নাম থাকে (Home, Office)।
- API: `GET/POST /api/addresses`, `PUT/DELETE /api/addresses/{id}`, `POST /api/addresses/{id}/default`।
- নিয়ম (`AddressService`):
  1. প্রথম ঠিকানাটাই default হয়। সবসময় **ঠিক একটা** default থাকে।
  2. Default ঠিকানা মুছলে বাকিগুলোর মধ্যে সবচেয়ে নতুনটা default হয়।
  3. একই ঠিকানা দুবার রাখা যায় না, দিলে **409**। তুলনার সময় ছোট-বড় হাতের অক্ষর, বাড়তি space, ফোন নম্বর আর নাম ধরা হয় না।
  4. অন্যের ঠিকানার id দিলে **404**, যাতে id আন্দাজ করে খুঁজে বের করা না যায়।
  5. Validation অর্ডারের ঠিকানার মতোই (`ShippingAddress` record আবার ব্যবহার করা হয়েছে)। ভুলের নাম আসে `address.zip` আকারে।
  6. অর্ডারে ঠিকানার আলাদা কপি থাকে। সেভ করা ঠিকানা পরে বদলালেও পুরোনো অর্ডারের ঠিকানা বদলায় না (integration test দিয়ে প্রমাণ করা)।
- **Frontend:**
  - Sidebar-এ "Addresses" পাতা: কার্ডে ঠিকানাগুলো দেখায়, default ঠিকানা কমলা দাগে চিহ্নিত। Add, Edit, Make default আর Delete (নিশ্চিত করার প্রশ্নসহ) করা যায়।
  - Browser-এ সার্ভারের নিয়মগুলোই আগে যাচাই হয়: ZIP, ফোন, দৈর্ঘ্য আর নাম।
  - Checkout-এ "Deliver to" অংশে সেভ করা ঠিকানাগুলো radio কার্ড হিসেবে আসে, default আগে বাছাই করা থাকে।
  - "Use a new address" বাছলে খালি ফর্ম আসে। সাথে "Save this address to my address book" টিক দেওয়ার ঘর থাকে।
  - ঠিকানার ঘরগুলো এখন একটা component-এ (`AddressFields.jsx`), checkout আর address book দুজায়গাতেই এটা ব্যবহার হয়। আগের সব `addr-*` test id একই আছে।

## টেস্ট

| স্তর | ফাইল |
|---|---|
| Unit | `GuestCheckoutTest` (৬), `AddressServiceTest` (৯; in-memory repository দিয়ে আসল নিয়ম যাচাই), `PasswordResetServiceTest#aGuestWhoSetsAPasswordNowHasAFullAccount` |
| Integration | `CheckoutExtrasIntegrationTest` (৪) |
| API | `CheckoutExtrasApiTest` (৪; একটা আসল Mailpit-এর reset link দিয়ে guest-কে অ্যাকাউন্টে বদলায়) |
| BDD | `checkout_extras.feature` (৪টা scenario) |
| UI | `playwright/tests/checkout-extras.spec.ts` (৩টা টেস্ট, ফোনসহ) |
| Contract | `auth.json`-এ `guest` ঘর |

Traceability matrix-এ নতুন requirement: **REQ-AUTH-15** আর **REQ-ADR-01 থেকে ০৩**। এখন মোট ১২২টা requirement, সবগুলোর টেস্ট আছে।
