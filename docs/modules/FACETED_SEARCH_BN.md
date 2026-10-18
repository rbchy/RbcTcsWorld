# Faceted search: বাঁ দিকের filter panel

## সমস্যা
Amazon বা Shein-এ ক্রেতা বাঁ দিকের টিক-বক্স দিয়ে পণ্য ছাঁটাই করেন, আর প্রতিটা option-এর পাশে লেখা থাকে সেটা বাছলে কয়টা পণ্য পাওয়া যাবে। আগে আমাদের catalog-এ শুধু একটা search বাক্স ছিল।

## কী যোগ হলো
- **Filter:** বিভাগ (category), দামের ধাপ (২৫-এর কম, ২৫-৫০, ৫০-১০০, ১০০-২০০, ২০০+), রেটিং (৪, ৩, ২, ১ তারা ও তার বেশি), শুধু স্টকে আছে, রং, সাইজ।
- **সংখ্যা:** প্রতিটা option-এর পাশে item-এর সংখ্যা। Item মানে catalog-এর একটা কার্ড। T-shirt-এর ৮টা variant গুনলে ৮ নয়, ১টা item।
- **Brand নেই:** আমাদের পণ্যে brand-এর ঘর নেই, তাই brand filter বানাইনি। ভবিষ্যতে ঘর যোগ করলে একই নকশায় যোগ করা যাবে।

## Disjunctive facet (Amazon-এর নিয়ম)
কোনো filter-গোষ্ঠীর সংখ্যা **নিজের বাছাই উপেক্ষা** করে, কিন্তু বাকি সব বাছাই ধরে। উদাহরণ: "books" টিক দিলেও "home"-এর পাশে লেখা থাকে কয়টা home পণ্য আছে, নাহলে ক্রেতা অন্য বিভাগে যাওয়ার আগে জানতেই পারতেন না। তবে "শুধু স্টকে আছে" টিক দিলে অন্য সব সংখ্যা সেই শর্ত মেনে কমে যায়।

## API
- `GET /api/products?category=a,b&minPrice=&maxPrice=&minRating=4&colors=Navy,Red&sizes=M,L&inStock=true&grouped=true`
- `GET /api/products/facets?(একই filter)` : `total`, `categories`, `prices`, `ratings`, `inStock`, `colors`, `sizes`। লগইন ছাড়াই চলে।
- ভুল filter-এ **400**: রেটিং ০ থেকে ৫-এর বাইরে, বা একটা filter-এ ২০টার বেশি মান।

## নকশার সিদ্ধান্ত
- Filter-এর যুক্তি একই `ProductFilter` class-এ। এটা একদিকে database-এর query (`JPA Specification`) বানায়, অন্যদিকে `matches(Product)` দিয়ে memory-তে একটা পণ্য পরীক্ষা করে। দুটোর ফল মেলে কি না integration test দিয়ে দেখা হয়েছে।
- Facet-এর সংখ্যা memory-তে গোনা হয়, search-এর সাথে মেলে এমন সর্বোচ্চ ৫০০০ পণ্য থেকে। এতে আলাদা তালিকার বদলে একটা query-ই যথেষ্ট। এই সীমার বেশি পণ্য হলে database-এর `GROUP BY` লাগবে (ADR-এ লেখা আছে)।
- Frontend-এ filter বাছলে একই সাথে পণ্যের তালিকা আর সংখ্যা নতুন করে আসে। পুরোনো উত্তর দেরিতে এলে ফেলে দেওয়া হয় (DEF-016-এর মতো)।
- Filter থাকলে welcome banner লুকানো থাকে, আর ফোনে panel কার্ডের ওপরে এক কলামে আসে।

## টেস্ট
| স্তর | ফাইল |
|---|---|
| Unit | `FacetSearchTest` (৯) |
| Integration | `FacetSearchIntegrationTest` (৫) |
| API | `FacetSearchApiTest` (৩) |
| BDD | `faceted_search.feature` (৩ scenario) |
| UI | `playwright/tests/faceted-search.spec.ts` (৩, ফোনসহ, axe-core) |

Traceability matrix-এ নতুন: **REQ-CAT-11, REQ-CAT-12**।
