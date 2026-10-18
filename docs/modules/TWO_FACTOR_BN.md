# Two-factor login (OTP) — REQ-AUTH-16

ঐচ্ছিক (opt-in) দুই-ধাপের সাইন-ইন: পাসওয়ার্ডের পরে ই-মেইলে ৬ সংখ্যার কোড আসে।

## কীভাবে চলে
1. ক্রেতা **Security** পাতায় (`#/security`) পাসওয়ার্ড দিয়ে 2FA চালু করে। বন্ধ করতেও পাসওয়ার্ড লাগে। Guest অ্যাকাউন্টে পাসওয়ার্ড নেই, তাই চালু করা যায় না।
2. চালু থাকলে `POST /api/auth/login` ঠিক পাসওয়ার্ডেও **টোকেন দেয় না**; উত্তর `200 {twoFactorRequired:true, challengeToken}`।
3. ই-মেইলে কোড যায়। `POST /api/auth/login/verify {challengeToken, code}` ঠিক হলে সাধারণ লগইনের মতোই `token`/`refreshToken` আসে।

## নিরাপত্তা নিয়ম
- কোড একবারই, ১০ মিনিট, সর্বোচ্চ ৫টি ভুল চেষ্টা (গণনা ডাটাবেসে, তাই সমান্তরাল চেষ্টায়ও সীমা ভাঙে না)।
- ডাটাবেসে কোড বা challenge id থাকে না — শুধু দুটো একসাথে SHA-256 hash; ডেলিভারির পর ই-মেইলের কোড মুছে `[sign-in code removed after delivery]` হয়।
- ঘণ্টায় সর্বোচ্চ ৫টি কোড (এরপর 429)। নতুন কোড পুরনোটা বাতিল করে।
- অজানা/ব্যবহৃত/মেয়াদোত্তীর্ণ/ভুল সব ক্ষেত্রে একই 400 বার্তা।
- ভুল পাসওয়ার্ডের lockout আগের মতোই কাজ করে; 2FA বন্ধ থাকলে লগইন আগের মতোই।
- দুই-ধাপের সাইন-ইন চালু/বন্ধ হলে ই-মেইলে জানানো হয়।

## ডাটাবেস
`V21__two_factor_login.sql` — `users.two_factor_enabled` + `login_challenges` টেবিল।

## টেস্ট
`TwoFactorServiceTest` (unit), `TwoFactorApiTest` (Mailpit দিয়ে পুরো পথ), `two-factor.spec.ts` (Playwright)।
