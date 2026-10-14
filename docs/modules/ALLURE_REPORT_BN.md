# Allure রিপোর্ট

## কী পাওয়া যায়
- **Overview:** মোট টেস্ট, pass/fail-এর হার, কতক্ষণ লাগল, আর পরিবেশ (Backend URL, Java, OS)
- **Behaviors:** টেস্টগুলো ব্যবসার ভাষায় সাজানো (`@Epic` → `@Feature`)
  - Identity & Access, Catalog, Shopping, Orders, Checkout, Fulfillment, Data integrity, Storefront UI
- **প্রতিটা API call-এর বিস্তারিত:** `AllureRestAssured` filter থাকায় প্রতিটা টেস্টে request (method, URL, header, body) আর response (status, body) দেখা যায়। কোনো টেস্ট fail করলে log ঘাঁটতে হয় না।
- **Cucumber scenario:** Given/When/Then প্রতিটা step আলাদাভাবে দেখায়, আর কোন step-এ ভেঙেছে সেটাও।
- **UI টেস্ট fail হলে:** screenshot আর URL নিজে থেকে যুক্ত হয় (`ScreenshotOnFailure`)।
- **Categories:** fail-গুলো নিজে থেকে ভাগ হয়ে যায়, যাতে product বাগ আর পরিবেশের সমস্যা আলাদা করা যায়:
  - "Environment: backend not running" (যেমন `Connection refused`)
  - "Product defect: wrong HTTP status"
  - "Product defect: wrong response data"
  - "Test defect: broken test code"
- **Trend (CI-তে):** আগের রানগুলোর ইতিহাস রাখা হয়, তাই সময়ের সাথে pass হার আর সময়ের গ্রাফ দেখা যায়।

## নিজের Mac-এ দেখার নিয়ম
```bash
cd ~/Eclipse-Workspace-QA/RbcTcsWorld_ECommerceSuite
mvn -f automation/pom.xml clean test -DexcludedGroups=ui,mobile   # backend চালু থাকতে হবে
mvn -f automation/pom.xml allure:serve                     # ব্রাউজারে রিপোর্ট খুলবে
```
`clean` দেওয়া জরুরি। নাহলে আগের রানের ফলাফল মিশে গিয়ে একই টেস্ট একাধিকবার দেখাবে।

## অনলাইনে (CI)
`main`-এ প্রতিটা push-এর পর রিপোর্ট আপডেট হয়: https://rbchy.github.io/RbcTcsWorld_ECommerceSuite/
এছাড়া প্রতিটা CI রানের "Artifacts" অংশে `allure-report` zip হিসেবেও থাকে।

## ইন্টারভিউতে যা বলবেন
> "প্রতিটা CI রানের পর Allure রিপোর্ট GitHub Pages-এ প্রকাশ হয়, আগের রানের trend সহ। প্রতিটা API টেস্টে request আর response সংযুক্ত থাকে। Fail হলে categories দিয়ে নিজে থেকে বোঝা যায় এটা product বাগ, নাকি পরিবেশের সমস্যা (যেমন backend বন্ধ)। ফলে triage-এর সময় অনেক কমে যায়।"
