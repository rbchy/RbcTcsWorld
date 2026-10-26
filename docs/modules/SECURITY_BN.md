# ধাপ ৫: Security টেস্ট

তিন স্তরে নিরাপত্তা যাচাই করা হয়েছে:
1. **নিজের লেখা security টেস্ট:** JUnit আর REST Assured দিয়ে, চালু থাকা backend-এর বিরুদ্ধে।
2. **OWASP ZAP:** চালু অ্যাপে স্বয়ংক্রিয় আক্রমণ চালিয়ে দুর্বলতা খোঁজে (DAST)।
3. **OSV-Scanner:** ব্যবহার করা লাইব্রেরিগুলোতে জানা দুর্বলতা (CVE) খোঁজে (SCA)।

প্রথমে কোড পড়ে ঝুঁকি খোঁজা হয়েছে, তারপর প্রতিটা ঝুঁকির জন্য টেস্ট লেখা হয়েছে, আর শেষে কোড ঠিক করা হয়েছে।

## যা পাওয়া গেল আর যা ঠিক করা হলো
| # | সমস্যা (finding) | ঝুঁকি | সমাধান | টেস্ট |
|---|---|---|---|---|
| 1 | Login-এ চেষ্টার কোনো সীমা ছিল না | পাসওয়ার্ড অনুমান করে ভাঙা (brute force) | একই ইমেইলে ১৫ মিনিটে ৫ বার ভুল হলে ১৫ মিনিটের জন্য lock। তখন **429** আর `Retry-After` header যায়। সঠিক পাসওয়ার্ডেও lock থাকে | `LoginAttemptServiceTest`, `SecurityIntegrationTest`, `AuthenticationAttackTest.bruteForceLockout` |
| 2 | **সময় দেখে account খোঁজা যেত (timing):** অজানা ইমেইলের উত্তর আসত প্রায় ১ ms-এ, আসল ইমেইলের প্রায় ৯০ ms-এ (bcrypt) | বাইরে থেকে সময় মেপে বোঝা যেত কোন ইমেইলে account আছে | অজানা ইমেইলেও একটা নকল (dummy) bcrypt যাচাই চলে, তাই দুটোতে সমান সময় লাগে | `AuthServiceTest`, `AuthenticationAttackTest.noTimingEnumeration` (median তুলনা) |
| 3 | JWT secret-এর default মান public GitHub-এ আছে | Production-এ ওই মানটাই থেকে গেলে যে কেউ admin token বানাতে পারত | `prod` profile-এ ওই secret থাকলে অ্যাপ **চালুই হবে না**। secret ৩২ byte-এর কম হলেও চালু হবে না। token-এর `issuer` যাচাই হয় | `JwtServiceTest` |
| 4 | CSP, Referrer-Policy, Permissions-Policy আর CORP header ছিল না | Clickjacking, তথ্য ফাঁস | সব response-এ header যোগ করা হয়েছে। CORP header-টার অভাব **ZAP ধরেছিল** | `securityHeaders` (সফল আর error, দুই ধরনের উত্তরেই) |
| 5 | **Spring Boot 3.5.6-এ ২১টা ঝুঁকিপূর্ণ লাইব্রেরি, ৮৫টা advisory** (Tomcat CVSS 9.8, spring-security-web 9.1) | জানা CVE দিয়ে আক্রমণ | Spring Boot 3.5.16-এ upgrade। Tomcat 10.1.60, Jackson 2.21.7, PostgreSQL driver 42.7.13, Log4j 2.25.5 আর commons-lang3 3.18.0 pin করা হয়েছে। এখন **০টা** ঝুঁকিপূর্ণ লাইব্রেরি | CI-তে OSV-Scanner |

**ভালো খবর (যা আগে থেকেই ঠিক ছিল, টেস্ট দিয়ে প্রমাণ করা হয়েছে):**
- Role আসে database থেকে, token থেকে নয়। তাই token-এ "ADMIN" লিখে দিলেও কিছু হয় না।
- `alg:none` token প্রত্যাখ্যাত হয়।
- সব query parameterized, তাই SQL injection কাজ করে না।
- Error-এর উত্তরে stack trace থাকে না।
- Actuator-এর শুধু health দেখা যায়।
- কোনো CORS অনুমতি দেওয়া নেই।

## নিজের লেখা security টেস্ট (`qa/tests/security`, `@Tag("security")`)
- **`AccessControlMatrixTest`:** ১৯টা endpoint, প্রতিটা তিনভাবে ডাকা হয়: anonymous, customer আর admin। পুরো authorization design একটা টেবিলে দেখা যায় (OWASP A01)।
- **`AuthenticationAttackTest`:**
  - **JWT আক্রমণ:** `alg:none`, payload বদলানো, অনুমান করা secret দিয়ে sign করা। এই token-গুলো Base64 আর HMAC দিয়ে হাতে বানানো, যেমনটা একজন আক্রমণকারী করবে।
  - **Header আর registration:** ভুল ধরনের Authorization header দেওয়া, mass assignment (`"role":"ADMIN"` পাঠানো), দুর্বল পাসওয়ার্ড, আর bcrypt-এর ৭২ byte সীমা।
  - **Brute force:** lockout ঠিকমতো কাজ করে কি না, আর timing দেখে account খোঁজা যায় কি না।
- **`InputAndExposureTest`:**
  - **SQL injection:** search আর login-এ।
  - **অদ্ভুত path:** path traversal, `;`, null byte।
  - **ক্ষতিকর ইনপুট:** ১,০০,০০০ অক্ষরের ইনপুট, ভুল content type।
  - **Header আর CORS।**
  - **তথ্য ফাঁস:** stack trace, actuator, password hash।

## CI-তে স্বয়ংক্রিয় scan (job: Security scan)
- **OWASP ZAP API scan:**
  - backend `/v3/api-docs`-এ নিজের OpenAPI বিবরণ দেয় (springdoc)। ZAP সেটা পড়ে প্রতিটা endpoint-এ আক্রমণ চালায়।
  - ZAP customer হিসেবে লগইন করে scan করে।
  - কোন নিয়ম ভাঙলে build fail হবে, সেটা `security/zap/zap-rules.tsv`-এ লেখা। যেমন SQL injection, XSS, path traversal, stack trace দেখানো, বা security header না থাকা।
  - **ফলাফল:** medium বা high কিছু পাওয়া যায়নি। Low-এর একটা ছিল (CORP header), সেটা ঠিক করা হয়েছে।
- **OSV-Scanner:**
  - Maven-এর তৈরি CycloneDX SBOM (backend আর automation, test scope সহ) আর `package-lock.json` পড়ে লাইব্রেরিগুলোকে OSV database-এর সাথে মেলায়।
  - নিজেকেও যাচাই করে: SBOM অসম্পূর্ণ হলে, বা জানা ঝুঁকিপূর্ণ নমুনা (log4j-core 2.14.1) ধরতে না পারলে job লাল (DEF-021, বিস্তারিত [CICD_BN.md](CICD_BN.md))।
  - প্রতিটা ঝুঁকিপূর্ণ লাইব্রেরির জন্য run-এর পেজে annotation দেখায়: **"fixed in X"**, মানে কোন version-এ upgrade করলে সমস্যা মিটবে।
  - CVSS ≥ 9 (critical) কোনো দুর্বলতার fix থাকলে **build fail হয়**।
- **Newest versions:** pin করা লাইব্রেরিগুলোর নতুন patch Maven Central-এ এসেছে কি না, সেটাও দেখায়।
- **নিরাপত্তার জন্য action pin করা:** third-party action (osv-scanner) commit SHA দিয়ে pin করা। কেউ পরে tag সরিয়ে দিলেও আমাদের CI-তে ক্ষতিকর কোড চলবে না।

## তিনটা শিক্ষণীয় ঘটনা
1. **Advisory অনুযায়ী Tomcat-এর fix ছিল 10.1.58, কিন্তু Maven Central-এ ওই version পাওয়া গেল না** (build fail হয়েছিল)। শিক্ষা: advisory-তে লেখা version অন্ধভাবে বসানো যায় না, আগে দেখতে হয় সেটা আসলে প্রকাশিত হয়েছে কি না। এজন্য CI-তে "newest versions" ধাপ যোগ করা হয়েছে, আর সেখান থেকে 10.1.60 বসানো হয়েছে।
2. **`Retry-After` কখনো 899 আসছিল, 900 নয়।** কারণ lock হওয়া আর যাচাইয়ের মাঝে কয়েক মিলিসেকেন্ড চলে যায়, আর সেকেন্ড নিচের দিকে round হচ্ছিল। এখন উপরের দিকে round হয়, যাতে client ঠিক ততক্ষণ অপেক্ষা করলেই আবার চেষ্টা করতে পারে।
3. **Scanner নিজেই অন্ধ হয়ে গিয়েছিল (DEF-021)।** Maven Central rate limit দিলে OSV চুপচাপ শুধু সরাসরি dependency দেখত, আর একটা CVSS 9.1 লাইব্রেরি "০টা ঝুঁকি" রিপোর্টের আড়ালে ছিল। এখন scan চলে Maven-এর তৈরি SBOM-এর উপর, completeness check আর self-test সহ।

## জেনেশুনে মেনে নেওয়া ঝুঁকি (documented trade-offs)
- **Lockout:** কেউ অন্যের ইমেইল জানলে ১৫ মিনিটের জন্য সেই account lock করে দিতে পারে। স্থায়ী lock না রেখে অস্থায়ী রাখায় ক্ষতি কম।
- **Lock-এর তথ্য এখন Redis-এ** (`LOGIN_STORE=redis`), সব backend একই হিসাব দেখে। Redis বন্ধ থাকলে login চলতে থাকে, প্রতিটা backend নিজের memory-তে গোনে। বিস্তারিত: [AUTH_SESSIONS_BN](AUTH_SESSIONS_BN.md)।
- **Logout:** refresh token সাথে সাথে server-এ বাতিল হয়। কিন্তু access token তার মেয়াদ পর্যন্ত (সর্বোচ্চ ১৫ মিনিট) বৈধ থাকে। প্রতিটা request-এ database দেখতে হবে বলে access token-এর denylist রাখা হয়নি।
- **Spring Boot 3.5-এর open-source সাপোর্ট জুন ২০২৬-এ শেষ।** দীর্ঘমেয়াদি সমাধান Spring Boot 4-এ যাওয়া। ততদিন OSV আর "newest versions" ধাপ নজর রাখবে।
- **Register-এ "Email already registered" বার্তা দেখে বোঝা যায় ইমেইলটা আছে।** বেশিরভাগ e-commerce সাইট ব্যবহার সহজ রাখতে এটা মেনে নেয়।

## চালানো (Mac)
```bash
cd ~/Eclipse-Workspace-QA/RbcTcsWorld
git pull
mvn -f backend/pom.xml spring-boot:run                       # নতুন Spring Boot নামাবে, তাই প্রথমবার একটু সময় লাগবে
mvn -f automation/pom.xml clean test -Dgroups=security
mvn -f automation/pom.xml allure:serve                       # Epic "Security"
curl -s http://localhost:8081/v3/api-docs | head -c 300      # OpenAPI বিবরণ
```
নিজে ৫ বার ভুল পাসওয়ার্ড দিয়ে lock হয়ে গেলে ১৫ মিনিট অপেক্ষা করুন। Lock এখন Redis-এ থাকে, তাই backend restart করলে আর খোলে না। Development-এ তাড়াতাড়ি খুলতে: `docker compose exec redis redis-cli FLUSHALL` (শুধু নিজের কম্পিউটারে; এই Redis-এ lockout ছাড়া আর কিছু নেই)।

## ইন্টারভিউতে যা বলবেন
> "Security-তে তিন স্তর ব্যবহার করেছি: নিজের লেখা আক্রমণ-টেস্ট, ZAP দিয়ে DAST, আর OSV দিয়ে SCA। কোড review করে দুটো সূক্ষ্ম সমস্যা পেয়েছি: login-এ brute-force-এর কোনো সীমা ছিল না, আর response-এর সময় দেখে account আছে কি না বোঝা যেত। দুটোই টেস্ট লিখে প্রমাণ করেছি, তারপর ঠিক করেছি। Dependency scan-এ Tomcat-এর CVSS 9.8 দুর্বলতা ধরা পড়েছিল। upgrade করে ২১টা ঝুঁকিপূর্ণ লাইব্রেরি থেকে ০-তে নামিয়েছি, আর এখন CI কোনো fix-যোগ্য critical CVE থাকলে merge আটকে দেয়।"
