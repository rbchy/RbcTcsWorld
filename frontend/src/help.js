/**
 * Help center content in the six interface languages. Facts match the backend rules: 8-72 character
 * passwords, lockout after 5 failed logins for 15 minutes, free shipping from $50 (else $5.99), 6% tax,
 * returns within 30 days of delivery, refunds by reason (see backend ReturnPolicy), verified-purchase reviews.
 */
export const HELP = [
  {
    id: 'start', admin: false,
    en: { title: 'Getting started', items: [
      'Create an account with your email and a password of 8 to 72 characters.',
      'Pick your language in the top bar; the choice is remembered on this device.',
      'The menu on the left takes you to the catalog, your wishlist, cart and orders.',
      'On a phone, open the menu with the Menu button.'] },
    bn: { title: 'শুরু করা', items: [
      'ইমেইল আর ৮ থেকে ৭২ অক্ষরের পাসওয়ার্ড দিয়ে অ্যাকাউন্ট খুলুন।',
      'উপরের বার থেকে ভাষা বেছে নিন; এই ডিভাইসে পছন্দটা মনে থাকবে।',
      'বাম পাশের মেনু থেকে ক্যাটালগ, পছন্দের তালিকা, কার্ট আর অর্ডারে যান।',
      'ফোনে "মেনু" বোতাম চেপে মেনু খুলুন।'] },
    es: { title: 'Primeros pasos', items: [
      'Crea una cuenta con tu correo y una contraseña de 8 a 72 caracteres.',
      'Elige tu idioma en la barra superior; se recuerda en este dispositivo.',
      'El menú de la izquierda lleva al catálogo, favoritos, carrito y pedidos.',
      'En el teléfono, abre el menú con el botón Menú.'] },
    fr: { title: 'Bien démarrer', items: [
      'Créez un compte avec votre e-mail et un mot de passe de 8 à 72 caractères.',
      'Choisissez votre langue dans la barre du haut ; elle est mémorisée sur cet appareil.',
      'Le menu de gauche mène au catalogue, à la liste d’envies, au panier et aux commandes.',
      'Sur téléphone, ouvrez le menu avec le bouton Menu.'] },
    ar: { title: 'البدء', items: [
      'أنشئ حسابًا ببريدك الإلكتروني وكلمة مرور من 8 إلى 72 حرفًا.',
      'اختر لغتك من الشريط العلوي، ويُحفظ اختيارك على هذا الجهاز.',
      'تنقلك القائمة الجانبية إلى الكتالوج والمفضلة والسلة والطلبات.',
      'على الهاتف، افتح القائمة بزر القائمة.'] },
    zh: { title: '快速入门', items: [
      '使用电子邮箱和 8 到 72 个字符的密码创建账户。',
      '在顶部栏选择语言，本设备会记住您的选择。',
      '左侧菜单可进入商品目录、心愿单、购物车和订单。',
      '在手机上，点击“菜单”按钮打开菜单。'] },
  },
  {
    id: 'shopping', admin: false,
    en: { title: 'Finding and saving products', items: [
      'Type in the search box; results update while you type.',
      'Choose a quantity from 1 to 10 and press Add to cart.',
      'Press ♡ to save a product to your wishlist. The wishlist shows when a saved product gets cheaper.',
      'Open a product name to see its details, rating and reviews.'] },
    bn: { title: 'পণ্য খোঁজা আর রাখা', items: [
      'খোঁজার ঘরে লিখুন; লেখার সাথে সাথে ফল বদলায়।',
      '১ থেকে ১০-এর মধ্যে পরিমাণ বেছে "কার্টে যোগ করুন" চাপুন।',
      '♡ চাপলে পণ্য পছন্দের তালিকায় যায়। দাম কমলে তালিকাতেই দেখা যায়।',
      'পণ্যের নামে চাপলে বিবরণ, রেটিং আর রিভিউ দেখা যায়।'] },
    es: { title: 'Buscar y guardar productos', items: [
      'Escribe en el buscador; los resultados cambian mientras escribes.',
      'Elige una cantidad de 1 a 10 y pulsa Añadir al carrito.',
      'Pulsa ♡ para guardar en favoritos. Allí verás si un producto baja de precio.',
      'Abre el nombre de un producto para ver detalles, valoración y reseñas.'] },
    fr: { title: 'Trouver et garder des produits', items: [
      'Tapez dans la recherche ; les résultats changent pendant la saisie.',
      'Choisissez une quantité de 1 à 10 puis Ajouter au panier.',
      '♡ ajoute un produit à la liste d’envies, qui signale les baisses de prix.',
      'Ouvrez le nom d’un produit pour voir détails, note et avis.'] },
    ar: { title: 'البحث عن المنتجات وحفظها', items: [
      'اكتب في مربع البحث، وتتغير النتائج أثناء الكتابة.',
      'اختر كمية من 1 إلى 10 ثم اضغط أضف إلى السلة.',
      'اضغط ♡ لحفظ المنتج في المفضلة، وستظهر فيها انخفاضات الأسعار.',
      'افتح اسم المنتج لرؤية التفاصيل والتقييم والمراجعات.'] },
    zh: { title: '查找和收藏商品', items: [
      '在搜索框输入，结果会随输入实时更新。',
      '选择 1 到 10 的数量，然后点击“加入购物车”。',
      '点击 ♡ 加入心愿单；商品降价时心愿单会提示。',
      '点击商品名称可查看详情、评分和评价。'] },
  },
  {
    id: 'checkout', admin: false,
    en: { title: 'Checkout, coupons and payment', items: [
      'Shipping is free when the merchandise total is $50 or more, otherwise $5.99. Sales tax is 6%.',
      'Coupons: WELCOME10 takes 10% off once per customer; SAVE5 takes $5 off orders from $25.',
      'This store uses a test payment gateway. Card 4242 4242 4242 4242 is approved, 4000 0000 0000 0002 is declined.',
      'A declined payment keeps the order open, so you can pay again with another card.'] },
    bn: { title: 'চেকআউট, কুপন আর পেমেন্ট', items: [
      'পণ্যের মোট দাম $50 বা বেশি হলে শিপিং ফ্রি, না হলে $5.99। বিক্রয় কর ৬%।',
      'কুপন: WELCOME10 প্রতি গ্রাহক একবার ১০% ছাড় দেয়; SAVE5 $25-এর বেশি অর্ডারে $5 ছাড় দেয়।',
      'এই দোকানে টেস্ট পেমেন্ট ব্যবস্থা। কার্ড 4242 4242 4242 4242 অনুমোদিত, 4000 0000 0000 0002 প্রত্যাখ্যাত।',
      'পেমেন্ট প্রত্যাখ্যাত হলেও অর্ডার খোলা থাকে, অন্য কার্ড দিয়ে আবার দিতে পারবেন।'] },
    es: { title: 'Pago, cupones y tarjeta', items: [
      'El envío es gratis desde $50 en productos; si no, $5.99. El impuesto es del 6%.',
      'Cupones: WELCOME10 da 10% una vez por cliente; SAVE5 descuenta $5 en pedidos desde $25.',
      'La tienda usa una pasarela de prueba: 4242 4242 4242 4242 se aprueba, 4000 0000 0000 0002 se rechaza.',
      'Si se rechaza el pago, el pedido sigue abierto y puedes pagar con otra tarjeta.'] },
    fr: { title: 'Paiement et coupons', items: [
      'Livraison offerte dès 50 $ d’articles, sinon 5,99 $. La taxe est de 6 %.',
      'Coupons : WELCOME10 donne 10 % une fois par client ; SAVE5 retire 5 $ dès 25 $.',
      'Passerelle de test : 4242 4242 4242 4242 est acceptée, 4000 0000 0000 0002 est refusée.',
      'Après un refus, la commande reste ouverte : payez avec une autre carte.'] },
    ar: { title: 'الدفع والقسائم', items: [
      'الشحن مجاني عندما يبلغ مجموع المنتجات 50 دولارًا أو أكثر، وإلا فرسومه 5.99 دولار. الضريبة 6%.',
      'القسائم: WELCOME10 خصم 10% مرة واحدة لكل عميل، وSAVE5 خصم 5 دولارات للطلبات من 25 دولارًا.',
      'يستخدم المتجر بوابة دفع تجريبية: البطاقة 4242 4242 4242 4242 مقبولة و4000 0000 0000 0002 مرفوضة.',
      'عند رفض الدفع يبقى الطلب مفتوحًا ويمكنك الدفع ببطاقة أخرى.'] },
    zh: { title: '结账、优惠券和付款', items: [
      '商品金额满 $50 免运费，否则运费 $5.99。销售税为 6%。',
      '优惠券：WELCOME10 每位顾客可享一次 9 折；SAVE5 满 $25 减 $5。',
      '本店使用测试支付网关：卡号 4242 4242 4242 4242 会成功，4000 0000 0000 0002 会被拒绝。',
      '付款被拒后订单仍保留，可换卡重新付款。'] },
  },
  {
    id: 'orders', admin: false,
    en: { title: 'Orders and tracking', items: [
      'An order goes from PLACED to PAID, SHIPPED and DELIVERED. Each step appears in the order timeline.',
      'You can cancel an order until it ships; the items go back into stock.',
      'Shipped orders show the carrier and tracking number.',
      'Anyone with the tracking number can follow the parcel without logging in.'] },
    bn: { title: 'অর্ডার আর ট্র্যাকিং', items: [
      'অর্ডার PLACED থেকে PAID, SHIPPED, তারপর DELIVERED হয়। প্রতিটা ধাপ অর্ডারের টাইমলাইনে দেখা যায়।',
      'শিপ হওয়ার আগ পর্যন্ত অর্ডার বাতিল করা যায়; পণ্য স্টকে ফিরে যায়।',
      'শিপ হওয়া অর্ডারে ক্যারিয়ার আর ট্র্যাকিং নম্বর দেখায়।',
      'ট্র্যাকিং নম্বর দিয়ে লগ ইন ছাড়াই পার্সেল দেখা যায়।'] },
    es: { title: 'Pedidos y seguimiento', items: [
      'Un pedido pasa de PLACED a PAID, SHIPPED y DELIVERED; cada paso aparece en la línea de tiempo.',
      'Puedes cancelar hasta que se envíe; los artículos vuelven al stock.',
      'Los pedidos enviados muestran transportista y número de seguimiento.',
      'Con el número de seguimiento cualquiera puede seguir el paquete sin iniciar sesión.'] },
    fr: { title: 'Commandes et suivi', items: [
      'Une commande passe de PLACED à PAID, SHIPPED puis DELIVERED ; chaque étape figure dans l’historique.',
      'Annulation possible jusqu’à l’expédition ; les articles reviennent en stock.',
      'Les commandes expédiées indiquent le transporteur et le numéro de suivi.',
      'Le numéro de suivi permet de suivre le colis sans se connecter.'] },
    ar: { title: 'الطلبات والتتبع', items: [
      'ينتقل الطلب من PLACED إلى PAID ثم SHIPPED ثم DELIVERED، وتظهر كل خطوة في سجل الطلب.',
      'يمكنك إلغاء الطلب قبل شحنه، وتعود المنتجات إلى المخزون.',
      'تُظهر الطلبات المشحونة شركة الشحن ورقم التتبع.',
      'يمكن لأي شخص لديه رقم التتبع متابعة الشحنة دون تسجيل الدخول.'] },
    zh: { title: '订单与物流', items: [
      '订单依次为 PLACED、PAID、SHIPPED、DELIVERED，每一步都会显示在订单时间线中。',
      '发货前可以取消订单，商品会退回库存。',
      '已发货订单会显示承运商和运单号。',
      '持有运单号的人无需登录即可查询包裹。'] },
  },
  {
    id: 'returns', admin: false,
    en: { title: 'Returns and refunds', items: [
      'Request a return on a delivered order within 30 days.',
      'Damaged, wrong or not-as-described items: the full order total is refunded.',
      'No longer needed: the total minus the shipping fee is refunded.',
      'A store admin approves or rejects the request; the refund goes back to the card.'] },
    bn: { title: 'রিটার্ন আর রিফান্ড', items: [
      'ডেলিভারির ৩০ দিনের মধ্যে রিটার্নের অনুরোধ করুন।',
      'ভাঙা, ভুল পণ্য বা বর্ণনার সাথে না মিললে পুরো টাকা ফেরত।',
      'আর দরকার নেই এমন কারণে: শিপিং খরচ বাদে বাকি টাকা ফেরত।',
      'স্টোর অ্যাডমিন অনুমোদন বা প্রত্যাখ্যান করেন; টাকা কার্ডে ফেরত যায়।'] },
    es: { title: 'Devoluciones y reembolsos', items: [
      'Solicita la devolución de un pedido entregado en un plazo de 30 días.',
      'Artículo dañado, equivocado o distinto a la descripción: reembolso total.',
      'Ya no lo necesitas: se reembolsa el total menos el envío.',
      'Un administrador aprueba o rechaza; el reembolso vuelve a la tarjeta.'] },
    fr: { title: 'Retours et remboursements', items: [
      'Demandez un retour dans les 30 jours suivant la livraison.',
      'Article endommagé, erroné ou non conforme : remboursement total.',
      'Article plus nécessaire : total moins les frais de livraison.',
      'Un administrateur accepte ou refuse ; le remboursement revient sur la carte.'] },
    ar: { title: 'الإرجاع والاسترداد', items: [
      'اطلب الإرجاع خلال 30 يومًا من التسليم.',
      'المنتج التالف أو الخاطئ أو المخالف للوصف: يُسترد المبلغ كاملًا.',
      'لم تعد بحاجة إليه: يُسترد المبلغ ناقص رسوم الشحن.',
      'يوافق مسؤول المتجر على الطلب أو يرفضه، ويعود المبلغ إلى البطاقة.'] },
    zh: { title: '退货与退款', items: [
      '订单送达后 30 天内可申请退货。',
      '商品损坏、发错或与描述不符：全额退款。',
      '不再需要：退还总额减去运费。',
      '由商店管理员批准或拒绝，退款退回原卡。'] },
  },
  {
    id: 'reviews', admin: false,
    en: { title: 'Ratings and reviews', items: [
      'Only customers who received the product can review it, once per product.',
      'You can edit or delete your own review.',
      'Reviewer names are shortened (for example "ja***") to protect privacy.'] },
    bn: { title: 'রেটিং আর রিভিউ', items: [
      'শুধু যারা পণ্যটা হাতে পেয়েছেন তাঁরাই রিভিউ দিতে পারেন, প্রতি পণ্যে একবার।',
      'নিজের রিভিউ সম্পাদনা বা মুছতে পারবেন।',
      'গোপনীয়তার জন্য রিভিউদাতার নাম ছোট করে দেখানো হয় (যেমন "ja***")।'] },
    es: { title: 'Valoraciones y reseñas', items: [
      'Solo quien recibió el producto puede reseñarlo, una vez por producto.',
      'Puedes editar o eliminar tu propia reseña.',
      'Los nombres se acortan (por ejemplo "ja***") para proteger la privacidad.'] },
    fr: { title: 'Notes et avis', items: [
      'Seuls les clients ayant reçu le produit peuvent donner un avis, une fois par produit.',
      'Vous pouvez modifier ou supprimer votre avis.',
      'Les noms sont abrégés (par exemple « ja*** ») pour protéger la vie privée.'] },
    ar: { title: 'التقييمات والمراجعات', items: [
      'يمكن فقط لمن استلم المنتج كتابة مراجعة، مرة واحدة لكل منتج.',
      'يمكنك تعديل مراجعتك أو حذفها.',
      'تُختصر أسماء المراجعين (مثل "ja***") حفاظًا على الخصوصية.'] },
    zh: { title: '评分与评价', items: [
      '只有收到商品的顾客才能评价，每件商品一次。',
      '您可以编辑或删除自己的评价。',
      '为保护隐私，评价者姓名会缩写（例如“ja***”）。'] },
  },
  {
    id: 'account', admin: false,
    en: { title: 'Account and security', items: [
      'After 5 wrong passwords the account is locked for 15 minutes.',
      'Log out on shared computers; your session is kept only in this browser.',
      'Card numbers are never stored, only the last four digits.'] },
    bn: { title: 'অ্যাকাউন্ট আর নিরাপত্তা', items: [
      'পরপর ৫ বার ভুল পাসওয়ার্ড দিলে অ্যাকাউন্ট ১৫ মিনিটের জন্য লক হয়।',
      'শেয়ার করা কম্পিউটারে লগ আউট করুন; সেশন শুধু এই ব্রাউজারে থাকে।',
      'কার্ড নম্বর কখনো সংরক্ষণ করা হয় না, শুধু শেষ চারটা সংখ্যা।'] },
    es: { title: 'Cuenta y seguridad', items: [
      'Tras 5 contraseñas incorrectas la cuenta se bloquea 15 minutos.',
      'Cierra sesión en equipos compartidos; la sesión solo vive en este navegador.',
      'Nunca se guarda el número de tarjeta, solo los últimos cuatro dígitos.'] },
    fr: { title: 'Compte et sécurité', items: [
      'Après 5 mots de passe erronés, le compte est bloqué 15 minutes.',
      'Déconnectez-vous sur un ordinateur partagé ; la session reste dans ce navigateur.',
      'Le numéro de carte n’est jamais conservé, seulement les quatre derniers chiffres.'] },
    ar: { title: 'الحساب والأمان', items: [
      'بعد 5 محاولات خاطئة يُقفل الحساب لمدة 15 دقيقة.',
      'سجّل الخروج على الأجهزة المشتركة، فالجلسة محفوظة في هذا المتصفح فقط.',
      'لا يُحفظ رقم البطاقة أبدًا، بل آخر أربعة أرقام فقط.'] },
    zh: { title: '账户与安全', items: [
      '连续输错 5 次密码，账户将锁定 15 分钟。',
      '在公用电脑上请退出登录；会话仅保存在此浏览器中。',
      '我们从不保存卡号，只保存最后四位。'] },
  },
  {
    id: 'admin', admin: true,
    en: { title: 'Running the store (admins)', items: [
      'Products: add, edit, update and delete. Delete hides the product from customers but keeps it in order history.',
      'Coupons: percent coupons can be at most 100; codes use letters, digits, _ and -.',
      'All orders: ship paid orders with UPS, FedEx or USPS, then mark them delivered.',
      'Returns and reviews: approve or reject return requests; hide or publish reviews.'] },
    bn: { title: 'স্টোর চালানো (অ্যাডমিন)', items: [
      'পণ্য: যোগ, সম্পাদনা, আপডেট আর মোছা। মুছলে গ্রাহকরা দেখেন না, কিন্তু অর্ডারের ইতিহাসে থাকে।',
      'কুপন: শতাংশ কুপন ১০০-এর বেশি হতে পারে না; কোডে অক্ষর, সংখ্যা, _ আর - চলে।',
      'সব অর্ডার: পেমেন্ট হওয়া অর্ডার UPS, FedEx বা USPS দিয়ে শিপ করুন, তারপর ডেলিভারি চিহ্নিত করুন।',
      'রিটার্ন আর রিভিউ: রিটার্ন অনুমোদন বা প্রত্যাখ্যান; রিভিউ লুকান বা প্রকাশ করুন।'] },
    es: { title: 'Gestionar la tienda (administradores)', items: [
      'Productos: añadir, editar, actualizar y eliminar. Eliminar lo oculta a los clientes pero lo deja en el historial.',
      'Cupones: un porcentaje no puede pasar de 100; los códigos usan letras, números, _ y -.',
      'Todos los pedidos: envía los pagados con UPS, FedEx o USPS y márcalos como entregados.',
      'Devoluciones y reseñas: aprueba o rechaza devoluciones; oculta o publica reseñas.'] },
    fr: { title: 'Gérer la boutique (admins)', items: [
      'Produits : ajouter, modifier, mettre à jour et supprimer. Supprimer masque le produit mais le garde dans l’historique.',
      'Coupons : un pourcentage ne dépasse pas 100 ; codes en lettres, chiffres, _ et -.',
      'Commandes : expédiez les commandes payées avec UPS, FedEx ou USPS, puis marquez-les livrées.',
      'Retours et avis : acceptez ou refusez les retours ; masquez ou publiez les avis.'] },
    ar: { title: 'إدارة المتجر (للمسؤولين)', items: [
      'المنتجات: إضافة وتعديل وتحديث وحذف. الحذف يخفي المنتج عن العملاء ويبقيه في سجل الطلبات.',
      'القسائم: لا تتجاوز النسبة 100، والرموز أحرف وأرقام و _ و - فقط.',
      'كل الطلبات: اشحن الطلبات المدفوعة عبر UPS أو FedEx أو USPS ثم علّمها كمُسلَّمة.',
      'المرتجعات والمراجعات: وافق على طلبات الإرجاع أو ارفضها، وأخفِ المراجعات أو انشرها.'] },
    zh: { title: '店铺管理（管理员）', items: [
      '商品：添加、编辑、更新和删除。删除后顾客看不到，但订单记录会保留。',
      '优惠券：百分比不超过 100；代码只能用字母、数字、_ 和 -。',
      '全部订单：用 UPS、FedEx 或 USPS 发出已付款订单，然后标记为已送达。',
      '退货与评价：批准或拒绝退货申请；隐藏或发布评价。'] },
  },
  {
    id: 'desktop', admin: false,
    en: { title: 'Desktop app and keyboard use', items: [
      'In Chrome or Edge, press Install app in the top bar to open the store in its own window.',
      'On the computer that runs the store, double-click the launcher in the desktop folder to start everything.',
      'Every control works with the keyboard: Tab moves, Enter or Space activates, Esc closes the menu.'] },
    bn: { title: 'ডেস্কটপ অ্যাপ আর কীবোর্ড', items: [
      'Chrome বা Edge-এ উপরের "অ্যাপ ইনস্টল করুন" চাপলে দোকান আলাদা উইন্ডোতে খোলে।',
      'যে কম্পিউটারে দোকান চলে, সেখানে desktop ফোল্ডারের launcher-এ ডাবল-ক্লিক করলে সব চালু হয়।',
      'সব কিছু কীবোর্ডে চলে: Tab দিয়ে সরুন, Enter বা Space দিয়ে চাপুন, Esc দিয়ে মেনু বন্ধ করুন।'] },
    es: { title: 'App de escritorio y teclado', items: [
      'En Chrome o Edge, pulsa Instalar app para abrir la tienda en su propia ventana.',
      'En el equipo que ejecuta la tienda, haz doble clic en el lanzador de la carpeta desktop.',
      'Todo funciona con teclado: Tab avanza, Intro o Espacio activa, Esc cierra el menú.'] },
    fr: { title: 'Application et clavier', items: [
      'Dans Chrome ou Edge, Installer l’app ouvre la boutique dans sa propre fenêtre.',
      'Sur l’ordinateur qui héberge la boutique, double-cliquez sur le lanceur du dossier desktop.',
      'Tout fonctionne au clavier : Tab pour avancer, Entrée ou Espace pour activer, Échap pour fermer le menu.'] },
    ar: { title: 'تطبيق سطح المكتب ولوحة المفاتيح', items: [
      'في Chrome أو Edge اضغط تثبيت التطبيق لفتح المتجر في نافذة مستقلة.',
      'على الجهاز الذي يشغّل المتجر، انقر نقرًا مزدوجًا على المشغّل في مجلد desktop.',
      'كل العناصر تعمل بلوحة المفاتيح: Tab للتنقل، Enter أو المسافة للتفعيل، Esc لإغلاق القائمة.'] },
    zh: { title: '桌面应用与键盘操作', items: [
      '在 Chrome 或 Edge 中点击顶部的“安装应用”，商店会在独立窗口中打开。',
      '在运行商店的电脑上，双击 desktop 文件夹中的启动器即可启动全部服务。',
      '所有操作都支持键盘：Tab 移动，Enter 或空格确认，Esc 关闭菜单。'] },
  },
];
