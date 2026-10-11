# ধাপ ৭: CI/CD পূর্ণ করা (Docker, JaCoCo, Jenkins)

## ১. পুরো app একটা command-এ (Docker)

```bash
docker compose --profile app up -d --build     # database + backend + দোকান (storefront)
open http://localhost:5173                     # দোকান
docker compose --profile app down              # বন্ধ (-v দিলে database-ও মুছে যাবে)
```

প্রতিদিনের development আগের মতোই চলবে। `docker compose up -d` (`--profile app` ছাড়া) শুধু PostgreSQL চালু করে। তারপর backend চালান Eclipse বা `mvn spring-boot:run` দিয়ে, আর UI চালান `npm run dev` দিয়ে।

**কেন profile ব্যবহার করলাম:** পুরো app সবসময় চালু থাকলে Docker-এর backend আর আপনার Eclipse-এর backend দুটোই 8081 port চাইত, ফলে সংঘর্ষ হতো। Profile দিয়ে দুটো কাজের ধরন আলাদা থাকে।

| অংশ | যা করা হয়েছে | কেন |
|---|---|---|
| `backend/Dockerfile` | **Multi-stage:** Maven দিয়ে build হয়, কিন্তু চূড়ান্ত image-এ থাকে শুধু Java runtime (JRE 21) | Image ছোট হয়, আর build-এর টুল production-এ যায় না |
| | **non-root user** (uid 10001) | কেউ container ভাঙলেও root অধিকার পাবে না। CI এটা যাচাই করে |
| | **Health check** (`/actuator/health`) | Backend সত্যিই তৈরি হওয়ার পরেই অন্য অংশ চালু হয় |
| | `-XX:MaxRAMPercentage=75` | Java তার memory ঠিক করে container-এর সীমা দেখে, পুরো কম্পিউটারের memory দেখে নয় |
| | আগে `pom.xml` copy, তারপর কোড | কোড বদলালে লাইব্রেরি আবার নামাতে হয় না, তাই build দ্রুত হয় (layer cache) |
| `frontend/Dockerfile` + `nginx.conf` | Vite দিয়ে build, তারপর nginx দেয় | nginx `/api` request backend-এ পাঠায় (reverse proxy)। ফলে ব্রাউজার একটাই ঠিকানা দেখে, CORS লাগে না |
| | Security header আর `server_tokens off` | Clickjacking ঠেকানো যায়, আর nginx-এর version ফাঁস হয় না |
| `docker-compose.yml` | `depends_on: condition: service_healthy` | চালু হওয়ার ক্রম: database সুস্থ → backend সুস্থ → দোকান |

**CI job "Whole app in Docker":**
- **Image build:** প্রতিটা push-এ image build হয়, আর সব container সুস্থ (healthy) হওয়া পর্যন্ত অপেক্ষা করে। Image-এর আকার annotation-এ দেখায়: backend 391 MB, frontend 48 MB।
- **curl দিয়ে যাচাই:** দোকান খোলে কি না, nginx দিয়ে API-তে যাওয়া যায় কি না, security header আছে কি না, nginx-এর version লুকানো কি না, আর backend root হিসেবে চলছে না কি না।
- **Smoke টেস্ট:** তারপর container-গুলোর বিরুদ্ধেই **৪৪টা smoke টেস্ট** চলে: API, BDD আর UI। এতে প্রমাণ হয় যে **image-ও কাজ করে**, শুধু কোড নয়।

## ২. JaCoCo: code coverage
- **কী:** `mvn verify` চালালে টেস্টের সময় একটা agent নজর রাখে, কোডের কোন লাইন আর কোন শাখা (if/else) আসলে চলেছে। শেষে HTML, XML আর CSV রিপোর্ট তৈরি হয়।
- **মাপা ফলাফল:** **লাইন 97.5%**, **শাখা (branch) 88.3%** (Spring Boot 4-এ ওঠার পর)। Mutation testing-এর টেস্ট যোগ হওয়ার আগে ছিল 96.3% আর 78.8%।
- **Gate:** লাইন ≥ 97% আর শাখা ≥ 88%। মাপা মানের ঠিক নিচে রাখা হয়েছে। আগে ছিল 95% আর 77%।
  - **নিয়ম:** নতুন টেস্ট যোগ হলে gate বাড়ানো যাবে, কিন্তু কখনো কমানো যাবে না (ratchet)।
  - coverage এর নিচে নামলে `mvn verify` fail করে, Mac-এ চালালেও।
- **অনলাইন রিপোর্ট:** https://rbchy.github.io/RbcTcsWorld_ECommerceSuite/coverage/
- **সবচেয়ে কম coverage কোথায়:** run-এর পেজে "Least covered packages" নামে দেখায়, যেমন `exception` 85% আর `admin` 88%। পরের টেস্ট কোথায় লিখতে হবে, এটা সেটা দেখিয়ে দেয়।

**Mutation testing (PIT):** টেস্ট আসলে ভুল ধরে কি না, সেটা মাপে। দেখুন [MUTATION_BN.md](MUTATION_BN.md)। Score 65.5% থেকে বেড়ে 84.9% হয়েছে, gate 84%।

**Interview-এর জন্য জরুরি:** 96% coverage মানে কোডে 96% বাগ নেই, তা **নয়**। এর মানে শুধু কোডের 96% লাইন অন্তত একবার চলেছে। Coverage দেখায় কোথায় টেস্ট **নেই**। কিন্তু টেস্ট কতটা ভালো, সেটা বোঝায় assertion-এর মান আর mutation testing।

## ৩. Jenkinsfile: GitHub Actions-এর সমান gate
| Stage | কী করে |
|---|---|
| Traceability matrix | `check_rtm.py` চালায় |
| Backend | unit আর integration টেস্ট, সাথে JaCoCo gate; JUnit রিপোর্ট আর coverage archive করে |
| Mutation testing (PIT) | parameter `RUN_MUTATION`; ব্যবসার কোডে mutant তৈরি করে, score ≥ 84% |
| Start the whole app | `docker compose --profile app up --wait` |
| Automation | API, DB, BDD আর UI টেস্ট container-গুলোর বিরুদ্ধে; Allure আর Cucumber রিপোর্ট archive করে |
| Performance | k6 smoke, flash-sale আর catalog। মেশিনে k6 থাকলে সেটা, না থাকলে Docker image |
| Security | OSV-Scanner আর OWASP ZAP, দুটোই compose network-এর ভেতরে চলে (`backend:8081`), ফলে Mac আর Linux দুটোতেই কাজ করে |
| Extra k6 (parameter) | load, stress, spike বা soak বেছে নেওয়া যায় |
| post | ব্যর্থ হলে container-এর log দেখায়; সবশেষে `down -v` দিয়ে সব পরিষ্কার করে |

**Parameter:** `RUN_UI`, `RUN_MUTATION`, `RUN_SECURITY` আর `K6_EXTRA` দিয়ে কোন অংশ চলবে তা ঠিক করা যায়।
**Options:** প্রতিটা লাইনে timestamp, ৬০ মিনিটের timeout, শেষ ২০টা build রাখা, আর একসাথে দুটো build না চালানো, কারণ port নির্দিষ্ট।

**Jenkinsfile আসলেই ঠিক কি না, কীভাবে জানলাম:**
- CI-তে "Jenkinsfile lint" job একটা আসল Jenkins (2.580.1) চালু করে, pipeline plugin-সহ। তারপর Jenkins-এর নিজস্ব declarative validator দিয়ে ফাইলটা যাচাই করে।
- **উল্টো প্রমাণও নিয়েছি:** একটা temporary branch-এ ইচ্ছা করে `stages`-কে `stagez` লিখেছিলাম। Job fail করেছে এই বার্তা দিয়ে: "Undefined section "stagez" … Missing required section "stages"।
- অর্থাৎ gate-টা শুধু দেখানোর জন্য নয়, সত্যিই ভুল ধরে।

### আপনার Mac-এর Jenkins-এ চালানো (8080)
1. Jenkins চালু করুন (`brew services start jenkins-lts`), তারপর http://localhost:8080 খুলুন। Suggested plugins install করুন।
2. **New Item → Pipeline** বেছে নিন। Definition-এ দিন **Pipeline script from SCM**, তারপর SCM: Git, URL: `https://github.com/rbchy/RbcTcsWorld_ECommerceSuite.git`, Branch: `*/main`, Script Path: `Jenkinsfile`।
3. Docker Desktop চালু থাকতে হবে।
4. Eclipse-এর backend আর `npm run dev` বন্ধ রাখুন, কারণ Jenkins container-এ নিজের backend চালাবে, আর দুটো একই port চাইবে।
5. **Build with Parameters** চাপুন। প্রথমবার Docker image build হতে কয়েক মিনিট লাগবে।

### সব রিপোর্ট এক পাতায়: "QA Reports"
- **কী:** প্রতিটা build শেষে `ci/qa_dashboard.py` একটা পাতা বানায়।
  - পাতায় থাকে সব gate-এর মূল সংখ্যা: টেস্ট, coverage, mutation score, প্রতিটা k6 টেস্টের p95, ZAP আর OSV। সবুজ মানে পাস, লাল মানে ব্যর্থ, ধূসর মানে ওই stage চলেনি।
  - প্রতিটা বিস্তারিত রিপোর্টের link থাকে: Allure, Cucumber, JaCoCo, PIT, k6 আর ZAP।
- **কোথায় দেখবেন:** build পাতার বাম দিকে **QA Reports** link। একই পাতা `qa-reports/` নামে artifact হিসেবেও থাকে।
- **একবার করতে হবে:**
  1. **HTML Publisher plugin install করুন:** Manage Jenkins → Plugins → Available plugins → `HTML Publisher` → Install। Plugin না থাকলেও build ভাঙবে না, শুধু link আসবে না।
  2. **Allure আর Cucumber-এর জন্য JavaScript চালু করুন (স্থায়ী, restart-এর পরেও থাকে):**
     - এ দুটো রিপোর্টের JavaScript লাগে, কিন্তু Jenkins-এর CSP সেটা আটকায়, তাই পাতা সাদা দেখায়।
     - Jenkins প্রতিবার চালু হওয়ার সময় যে script চালায়, সেখানে নিয়মটা রাখুন (Terminal-এ একবার):
       ```bash
       mkdir -p ~/.jenkins/init.groovy.d
       echo 'System.setProperty("hudson.model.DirectoryBrowserSupport.CSP", "")' > ~/.jenkins/init.groovy.d/report-csp.groovy
       ```
     - এখনই কাজে লাগাতে: Manage Jenkins → Script Console-এ একই লাইন চালান, অথবা `http://localhost:8080/safeRestart`।
     - ⚠️ শুধু নিজের ব্যক্তিগত Jenkins-এ। শেয়ার করা Jenkins-এ Allure Jenkins plugin বা ঠিকমতো সেট করা Resource Root URL ব্যবহার করুন।
     - "Resource Root URL" ঘরে কিছু লেখা থাকলে মুছে দিন: Jenkins URL ঠিকমতো সেট না থাকলে HTML Publisher-এর পাতা "Not Found" দেখায়।
- **Dashboard পাতাটা নিজে CSP বদলানো ছাড়াই দেখা যায়:** এতে কোনো JavaScript বা inline CSS নেই।

## ৪. একটা আসল ঘটনা: নতুন critical CVE (ধাপ ৭ চলাকালীন)
- **কী হলো:** OSV-Scanner হঠাৎ build থামিয়ে দিল। `spring-webmvc 6.2.19`-এ **CVE-2026-47884 (CVSS 9.8)** প্রকাশিত হয়েছে, আর 6.2 লাইনে এখনো কোনো fix নেই।
- **বিশ্লেষণ:**
  - এই দুর্বলতা শুধু তখনই ব্যবহার করা যায়, যখন app **XsltView** দিয়ে view render করে।
  - আমাদের backend পুরোটাই JSON API: ১৩টা controller-এর সবগুলো `@RestController`। কোনো view, ViewResolver বা XSLT নেই। কোড খুঁজে এটা নিশ্চিত হয়েছি।
- **সিদ্ধান্ত:** ঝুঁকিটা লিখিতভাবে মেনে নেওয়া (`backend/osv-scanner.toml`), তিনটা শর্তসহ:
  1. **কারণ** লেখা থাকবে।
  2. **Guard test** থাকবে: `SecurityIntegrationTest#noXsltViewRenderingIsConfigured_CVE_2026_47884`। কেউ কখনো XsltView বা view দেখানো controller যোগ করলে build fail করবে, অর্থাৎ ঝুঁকি মেনে নেওয়ার ভিত্তিটাই পরীক্ষা হতে থাকে।
  3. **মেয়াদ থাকবে, ২০২৬-১১-০৫ পর্যন্ত।** তারপর finding নিজে থেকে আবার ফিরে আসবে। তখন হয় fix-যুক্ত version-এ upgrade, নয়তো আবার মূল্যায়ন।
- **Gate কঠোর করা:**
  - আগে শুধু fix থাকা critical দুর্বলতায় build থামত।
  - এখন **যেকোনো** critical থামায়, fix থাকুক বা না থাকুক, যদি না সেটা কারণ, টেস্ট আর মেয়াদ দিয়ে লিখিতভাবে মেনে নেওয়া থাকে।
  - মেনে নেওয়া ঝুঁকিগুলো প্রতিটা run-এ "Accepted risk" নামে দেখায়, যাতে কেউ ভুলে না যায়।

**দ্বিতীয় ঘটনা (৭ অক্টোবর):** আবার একটা নতুন CVSS 9.8 advisory এল, GHSA-j9f9-w8pj-32f8। এটাও spring-webmvc 6.2.19-এ, এবার Server-Sent Events (SSE) নিয়ে, আর 6.2 লাইনে কোনো open-source fix নেই।
- **Gate কাজ করেছে:** build সঙ্গে সঙ্গে লাল হয়েছে।
- **বিশ্লেষণ:** দুর্বলতাটা শুধু তখনই কাজে লাগানো যায়, যখন app SSE stream পাঠায়। আমাদের app কোনো SSE পাঠায় না।
- **সিদ্ধান্ত:** একই পদ্ধতিতে লিখিতভাবে মেনে নেওয়া হয়েছে।
- **নতুন guard test:** `SecurityIntegrationTest#noServerSentEventsOrFunctionalEndpoints_GHSA_j9f9_w8pj_32f8`। কেউ SSE বা WebMvc.fn endpoint যোগ করলে build fail করবে।
- **আরেকটা উন্নতি:** এখন run-এর পেজে প্রতিটা critical advisory-র CVE id আর শিরোনামও দেখায়।

**শেষ ফল (৭ অক্টোবর):** Spring Boot 4.1-এ upgrade করায় দুটো CVE-ই আসলে ঠিক হয়ে গেছে (Spring Framework 7.0.9)। `osv-scanner.toml`-এ এখন কোনো exception নেই। বিস্তারিত দেখুন [SPRING_BOOT_4_BN.md](SPRING_BOOT_4_BN.md)।

**Interview-এ:**
> "একটা CVSS 9.8 CVE এসেছিল যার কোনো fix ছিল না। আমি অন্ধভাবে suppress করিনি, আবার আতঙ্কিতও হইনি। কোথায় দুর্বলতাটা কাজ করে বিশ্লেষণ করে দেখলাম আমাদের app-এ সেটা ব্যবহারযোগ্য নয়। তারপর কারণ, guard test আর মেয়াদসহ ঝুঁকিটা লিখিতভাবে মেনে নিয়েছি। Guard test নিশ্চিত করে যে সেই ভিত্তি কখনো চুপচাপ বদলাতে পারবে না।"

## ৫. CI-র রক্ষণাবেক্ষণ: runner pin, canary আর flaky টেস্ট (অক্টোবর ২০২৬)

GitHub ঘোষণা দিয়েছিল, ১৯ অক্টোবর থেকে `ubuntu-latest` মানে হবে Ubuntu 26। নতুন image-এ browser, sandbox-এর নিয়ম আর Docker-এর version বদলায়। DEF-019-এ দেখেছি, এমন বদলেই Edge হঠাৎ চালু হওয়া বন্ধ করে দিয়েছিল। তাই:

| কী | কেন |
|---|---|
| **Runner pin:** সব job `ubuntu-24.04`-এ | রাতারাতি image বদলালে `main` লাল হবে না। বদলটা আমরা নিজেরা ঠিক করি, কখন নেব। |
| **Runner canary** (`.github/workflows/runner-canary.yml`) | প্রতি সোমবার পুরো pipeline পরের image-এ (`ubuntu-26.04`) চলে। `ci.yml`-কে reusable workflow হিসেবে ডাকে, তাই কোনো কোড দুবার লেখা নেই। Canary কখনো report publish করে না, আর `main`-কে আটকায় না। |
| **Node 24 actions** | checkout v5, setup-java v5, setup-node v6, upload-artifact v6, download-artifact v7, pages v5। Node 20 আর সমর্থিত না। Storefront এখন Node 22 দিয়ে build হয় (CI আর Dockerfile দুটোতেই)। |
| **Flaky টেস্ট দৃশ্যমান** | Browser টেস্ট fail করলে একবার আবার চলে (`-Dsurefire.rerunFailingTestsCount=1`)। দ্বিতীয়বার পাস করলে run-এর পেজে **FLAKY** warning আর গণনা দেখায়। দুবার fail করলে build লাল। API টেস্টের কোনো rerun নেই। |

**Flaky reporting প্রমাণ করা:** একটা অস্থায়ী টেস্ট লিখেছিলাম, যেটা ইচ্ছা করে প্রথমবার fail করে আর দ্বিতীয়বার পাস করে। CI-তে সেটা `FLAKY ui-edge: ... failed 1x, passed on rerun` হিসেবে দেখা গেল, গণনায় `flaky 1`। তারপর টেস্টটা মুছে দিয়েছি।

### Canary প্রথম run-এই একটা আসল সমস্যা ধরল (DEF-021)
- **কী দেখা গেল:** Ubuntu 26-এর canary-তে OSV ৫টা ঝুঁকিপূর্ণ লাইব্রেরি পেল, যার একটা CVSS 9.1। অথচ একই commit-এ সাধারণ run বলল ০টা। **একই commit-এ একটা gate দুরকম উত্তর দিলে gate-টাই ত্রুটিপূর্ণ।**
- **কারণ Ubuntu না:** OSV নিজে Maven Central থেকে transitive dependency খোঁজে। Central "429 Too Many Requests" দিলে সে চুপচাপ শুধু সরাসরি dependency দেখে (automation-এ ১৩০টার জায়গায় ১৪টা), তারপরও "সফল" দেখায়। কোন run-এ rate limit লাগবে, সেটা ভাগ্যের ব্যাপার।
- **সমাধান (`security/osv-scan.sh`, GitHub আর Jenkins দুটোতেই):**
  1. Maven নিজে পুরো dependency tree বের করে প্রতিটা module-এর **CycloneDX SBOM** বানায়, test scope সহ। Download ব্যর্থ হলে Maven build fail করে, চুপ থাকে না।
  2. OSV শুধু SBOM আর `package-lock.json` scan করে। নিজে আর কিছু খোঁজে না।
  3. **Completeness check:** কোনো SBOM-এ ৫০টার কম component থাকলে job লাল।
  4. **Self-test:** log4j-core 2.14.1 (Log4Shell) থাকা একটা নমুনা SBOM-কে OSV-র অবশ্যই ধরতে হবে। না ধরলে database পৌঁছানো যায়নি, তাই "০টা" ফল বিশ্বাসযোগ্য না।
- **লাইব্রেরি ঠিক করা:** freemarker 2.3.35, rhino 1.7.15.1। WebDriverManager বাদ দিয়েছি, কারণ আমরা সেটা ব্যবহারই করি না (driver দেয় Selenium Manager)।
- **ফল:** backend ১৪৪, automation ১১৬ component scan হয়, ০টা ঝুঁকিপূর্ণ। দুই image-এই সব সবুজ।

**Interview-এ:**
> "CI-কে runner image-এ pin করে রেখেছি, আর পরের image-এ একটা canary চালাই। প্রথম run-এই canary দেখাল, আমাদের dependency scanner rate limit পেলে চুপচাপ অর্ধেক dependency বাদ দেয়, আর একটা CVSS 9.1 লাইব্রেরি ধরা পড়ছিল না। আমি scanner-কে Maven-এর তৈরি SBOM দিই, আর gate-টা নিজেই প্রমাণ করে যে সে fail করতে পারে: একটা জানা ঝুঁকিপূর্ণ নমুনা প্রতিবার ধরতে হয়। Security tool-কেও আমি টেস্টের মতো যাচাই করি।"

### Jenkins build ৪৬ মিনিট আটকে ছিল (build #19)
- **কী হয়েছিল:** build প্রথম stage "Tools on the agent"-এ `docker version`-এ আটকে ছিল। Docker Desktop সাড়া দিচ্ছিল না, আর এই command তখন fail না করে অনির্দিষ্টকাল অপেক্ষা করে। পুরো build-এর সময়সীমা ছিল একটাই, ৬০ মিনিট, তাই কোন stage দায়ী সেটা সঙ্গে সঙ্গে বোঝা যেত না।
- **সমাধান:**
  - Docker-কে ৬০ সেকেন্ড সময় দেওয়া হয়। উত্তর না এলে build থামে এই বার্তা দিয়ে: "DOCKER DOES NOT ANSWER … restart Docker Desktop"।
  - **প্রতিটা stage-এর নিজের সময়সীমা** আছে। যেমন Tools ৩ মিনিট, PIT ২০ মিনিট, Automation ৩০ মিনিট, ZAP-সহ Security ৩০ মিনিট, Extra k6 ৬০ মিনিট (soak ৩০ মিনিট চলে)। আটকালে ঠিক সেই stage লাল হয়।
  - পুরো build-এর সীমা এখন ১২০ মিনিট, শুধু শেষ নিরাপত্তা জাল হিসেবে। এতে soak বাছলেও ভালো build "aborted" হয় না।
- **হাতে কী করবেন:** Docker Desktop → Quit → আবার খুলুন → Terminal-এ `docker version` দিয়ে দেখে নিন, তারপর build চালান।

### QA Reports পাতা ব্যর্থ build-কে "পাস" দেখিয়েছিল (DEF-022)
- **কী হয়েছিল:** build #30 প্রথম stage-এই থেমে যায় (JDK ভুল সেট করা ছিল), কোনো টেস্টই চলেনি। তবু QA Reports পাতায় লেখা ছিল "all gates passed"। পাতাটা আগের সফল build-এর পড়ে থাকা ফাইল পড়ছিল।
- **সমাধান:**
  - প্রতিটা build শুরুতে আগের রিপোর্ট ফাইল মুছে দেয়।
  - Jenkins-এর আসল ফল (`currentBuild.currentResult`) এখন dashboard-এ যায়। Build ব্যর্থ হলে পাতা লাল দেখায়, লেখা থাকে "pipeline failure - see Console Output"। কোনো gate না চললে লেখা থাকে "no gate ran"।
- **শিক্ষা:** রিপোর্টও একটা টেস্টের ফল। এটা কখনো build-এর চেয়ে বেশি সবুজ হতে পারবে না।

### Jenkins-এ JDK 21 স্থির করা
- **সমস্যা:** Jenkins `PATH`-এ প্রথমে যে `java` পেত সেটাই ব্যবহার করত। একটা build-এ Java 26, পরেরটায় Java 23। GitHub Actions চলে Temurin 21-এ, তাই দুই CI একই Java-তে চলছিল না।
- **এখন:** Jenkinsfile-এ `tools { jdk 'jdk-21' }`। এটা `JAVA_HOME` ঠিক করে দেয়, আর Maven সেটাই ব্যবহার করে (PATH-এর প্রথম `java` নয়)। "Tools on the agent" stage `$JAVA_HOME/bin/java` আর `mvn -v` দিয়ে দেখে নেয় version সত্যিই 21 কি না। না হলে build থামে, setup-এর নির্দেশনাসহ।
- **একবারের setup (Mac):**
  1. JDK 21 install করুন, না থাকলে: `brew install --cask temurin@21`
  2. Path বের করুন: `/usr/libexec/java_home -v 21`
  3. **Manage Jenkins → Tools → JDK installations → Add JDK**। Name দিন `jdk-21`, "Install automatically" টিক তুলে দিন, আর JAVA_HOME-এ ২ নম্বর ধাপের path দিন → **Save**।
- **GitHub-এর lint Jenkins-এও** একই নামের JDK রাখা হয়েছে (`.github/jenkins/init.groovy.d/jdk-21.groovy`), তাই Jenkinsfile যাচাই দুই জায়গায় একই নিয়মে হয়।

### Browser আগেই যাচাই (build #21)
- **কী হয়েছিল:** `BROWSER=safari` দিয়ে build চলেছিল, কিন্তু Safari-তে "Allow remote automation" চালু ছিল না। ২০টা UI টেস্ট প্রতিটা ~৪০ সেকেন্ড অপেক্ষা করে error দিয়েছিল, প্রায় ১২ মিনিট নষ্ট।
- **এখন "Tools on the agent" stage আগেই যাচাই করে:**
  - বাছাই করা browser Mac-এ install না থাকলে শুধু একটা NOTE দেখায়, build থামায় না। Chrome, Firefox আর Edge না থাকলে Selenium Manager নিজেই browser নামিয়ে নেয় (`~/.cache/selenium`)। Build #22 Firefox install ছাড়াই পাস করেছিল, অথচ প্রথম সংস্করণের পরীক্ষা build #32-কে ভুল করে থামিয়ে দিয়েছিল।
  - Safari হলে: আসল একটা WebDriver session খুলে বন্ধ করে দেখে। না পারলে কয়েক সেকেন্ডে build থামে, ঠিক কী চালু করতে হবে সেই বার্তাসহ।

## এখন CI-র gate-গুলো (প্রতিটা push-এ)
1. Backend টেস্ট (২৮৪টা) আর JaCoCo coverage gate
2. Traceability matrix check
3. API, DB, BDD আর UI টেস্ট (২৮৭টা), আর k6 smoke, flash-sale ও catalog
4. Mutation testing (PIT): score ≥ 84%
5. Firefox আর Edge-এ ২০টা UI টেস্ট (flaky হলে আলাদা করে দেখায়)
6. পুরো app Docker-এ চালিয়ে তার বিরুদ্ধে ৪৪টা smoke টেস্ট
7. Jenkinsfile lint
8. OWASP ZAP, OpenAPI breaking-change gate, আর SBOM-ভিত্তিক OSV scan (completeness check + self-test)
9. সব ঠিক থাকলে Allure, coverage আর mutation রিপোর্ট GitHub Pages-এ প্রকাশ
10. প্রতি সোমবার: একই pipeline পরের runner image-এ (canary)
