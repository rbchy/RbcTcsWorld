import { useEffect, useState } from 'react';

/**
 * Six interface languages. English is the default (and what the automated tests read); the choice is kept in
 * localStorage. Arabic switches the whole page to right-to-left. Product names, prices and order statuses come
 * from the backend and stay as they are.
 */
export const LANGUAGES = [
  { code: 'en', label: 'English', dir: 'ltr' },
  { code: 'bn', label: 'বাংলা', dir: 'ltr' },
  { code: 'es', label: 'Español', dir: 'ltr' },
  { code: 'fr', label: 'Français', dir: 'ltr' },
  { code: 'ar', label: 'العربية', dir: 'rtl' },
  { code: 'zh', label: '中文', dir: 'ltr' },
];
const ORDER = LANGUAGES.map(l => l.code);

// key: [en, bn, es, fr, ar, zh]
const T = {
  // ---- shell ----
  tagline: ['Everyday goods, delivered to your door', 'প্রতিদিনের জিনিস, আপনার দরজায়', 'Productos de cada día, hasta tu puerta', 'Le quotidien, livré à votre porte', 'احتياجاتك اليومية حتى باب منزلك', '日常好物，送货上门'],
  menu: ['Menu', 'মেনু', 'Menú', 'Menu', 'القائمة', '菜单'],
  closeMenu: ['Close menu', 'মেনু বন্ধ করুন', 'Cerrar menú', 'Fermer le menu', 'إغلاق القائمة', '关闭菜单'],
  language: ['Language', 'ভাষা', 'Idioma', 'Langue', 'اللغة', '语言'],
  secShop: ['Shop', 'কেনাকাটা', 'Tienda', 'Boutique', 'التسوق', '购物'],
  secAccount: ['Account', 'অ্যাকাউন্ট', 'Cuenta', 'Compte', 'الحساب', '账户'],
  secAdmin: ['Store admin', 'স্টোর অ্যাডমিন', 'Administración', 'Administration', 'إدارة المتجر', '商店管理'],
  secSupport: ['Support', 'সহায়তা', 'Ayuda', 'Assistance', 'الدعم', '支持'],
  navCatalog: ['Catalog', 'ক্যাটালগ', 'Catálogo', 'Catalogue', 'الكتالوج', '商品目录'],
  navWishlist: ['Wishlist', 'পছন্দের তালিকা', 'Favoritos', 'Liste d’envies', 'المفضلة', '心愿单'],
  navCart: ['Cart', 'কার্ট', 'Carrito', 'Panier', 'السلة', '购物车'],
  navOrders: ['Orders', 'অর্ডার', 'Pedidos', 'Commandes', 'الطلبات', '订单'],
  navLogin: ['Log in', 'লগ ইন', 'Iniciar sesión', 'Se connecter', 'تسجيل الدخول', '登录'],
  navRegister: ['Create account', 'অ্যাকাউন্ট খুলুন', 'Crear cuenta', 'Créer un compte', 'إنشاء حساب', '注册账户'],
  navLogout: ['Log out', 'লগ আউট', 'Cerrar sesión', 'Se déconnecter', 'تسجيل الخروج', '退出登录'],
  demoRibbon: ['Demo shop: nothing here is real. Pay with the test card 4242 4242 4242 4242. Everything is reset every night.', 'ডেমো শপ: এখানে কিছুই আসল না। টেস্ট কার্ড 4242 4242 4242 4242 দিয়ে পেমেন্ট করুন। প্রতি রাতে সব আগের অবস্থায় ফেরে।', 'Tienda de demostración: nada es real. Paga con la tarjeta de prueba 4242 4242 4242 4242. Todo se reinicia cada noche.', 'Boutique de démonstration : rien n’est réel. Payez avec la carte de test 4242 4242 4242 4242. Tout est réinitialisé chaque nuit.', 'متجر تجريبي: لا شيء هنا حقيقي. ادفع ببطاقة الاختبار 4242 4242 4242 4242. تتم إعادة ضبط كل شيء كل ليلة.', '演示商店：这里的一切都不是真的。请使用测试卡 4242 4242 4242 4242 付款。所有数据每晚重置。'],
  navExit: ['Exit', 'প্রস্থান', 'Salir', 'Quitter', 'خروج', '离开'],
  exitHint: ['Log out and close the shop window', 'লগ আউট করে শপের উইন্ডো বন্ধ করুন', 'Cerrar sesión y cerrar la ventana de la tienda', 'Se déconnecter et fermer la fenêtre de la boutique', 'تسجيل الخروج وإغلاق نافذة المتجر', '退出登录并关闭商店窗口'],
  exitTitle: ['You have left the shop', 'আপনি শপ থেকে বেরিয়ে এসেছেন', 'Has salido de la tienda', 'Vous avez quitté la boutique', 'لقد غادرت المتجر', '您已离开商店'],
  exitSignedOut: ['You are signed out on this device.', 'এই ডিভাইসে আপনি লগ আউট হয়েছেন।', 'Has cerrado sesión en este dispositivo.', 'Vous êtes déconnecté sur cet appareil.', 'تم تسجيل خروجك على هذا الجهاز.', '您已在此设备上退出登录。'],
  exitCloseTab: ['You can close this window now ({keys}).', 'এখন এই উইন্ডোটি বন্ধ করতে পারেন ({keys})।', 'Ya puedes cerrar esta ventana ({keys}).', 'Vous pouvez fermer cette fenêtre ({keys}).', 'يمكنك إغلاق هذه النافذة الآن ({keys}).', '现在可以关闭此窗口（{keys}）。'],
  exitCloseWindow: ['Close window', 'উইন্ডো বন্ধ করুন', 'Cerrar ventana', 'Fermer la fenêtre', 'إغلاق النافذة', '关闭窗口'],
  exitBrowserRefused: ['Your browser keeps tabs you opened yourself. Close it with the keys above or the × on the tab.', 'নিজে খোলা ট্যাব ব্রাউজার বন্ধ করতে দেয় না। ওপরের কী বা ট্যাবের × দিয়ে বন্ধ করুন।', 'El navegador no cierra pestañas que abriste tú. Ciérrala con las teclas de arriba o con la × de la pestaña.', 'Le navigateur ne ferme pas les onglets que vous avez ouverts. Fermez-le avec les touches ci-dessus ou le × de l’onglet.', 'لا يغلق المتصفح علامات التبويب التي فتحتها بنفسك. أغلقها بالمفاتيح أعلاه أو بعلامة × على التبويب.', '浏览器不会关闭您自己打开的标签页。请使用上面的快捷键或标签页上的 × 关闭。'],
  exitBack: ['Back to the shop', 'শপে ফিরে যান', 'Volver a la tienda', 'Retour à la boutique', 'العودة إلى المتجر', '返回商店'],
  exitStopServer: ['Running the shop on your own computer? Exit only closes this window. Stop the servers with the RbcTcsWorld-Stop launcher on your desktop.', 'নিজের কম্পিউটারে শপ চালাচ্ছেন? Exit শুধু এই উইন্ডো বন্ধ করে। সার্ভার বন্ধ করতে ডেস্কটপের RbcTcsWorld-Stop লঞ্চার চালান।', '¿Ejecutas la tienda en tu ordenador? Salir solo cierra esta ventana. Detén los servidores con el lanzador RbcTcsWorld-Stop del escritorio.', 'La boutique tourne sur votre ordinateur ? Quitter ferme seulement cette fenêtre. Arrêtez les serveurs avec le lanceur RbcTcsWorld-Stop du bureau.', 'هل تشغّل المتجر على جهازك؟ الخروج يغلق هذه النافذة فقط. أوقف الخوادم باستخدام مشغّل RbcTcsWorld-Stop على سطح المكتب.', '在自己的电脑上运行商店？“离开”只会关闭此窗口。请用桌面上的 RbcTcsWorld-Stop 启动器停止服务器。'],
  navHelp: ['Help', 'সাহায্য', 'Ayuda', 'Aide', 'المساعدة', '帮助'],
  navDashboard: ['Dashboard', 'ড্যাশবোর্ড', 'Panel', 'Tableau de bord', 'لوحة التحكم', '仪表板'],
  navProducts: ['Products', 'পণ্য', 'Productos', 'Produits', 'المنتجات', '商品'],
  navCoupons: ['Coupons', 'কুপন', 'Cupones', 'Coupons', 'القسائم', '优惠券'],
  navAllOrders: ['All orders', 'সব অর্ডার', 'Todos los pedidos', 'Toutes les commandes', 'كل الطلبات', '全部订单'],
  navReturns: ['Returns', 'রিটার্ন', 'Devoluciones', 'Retours', 'المرتجعات', '退货'],
  navReviews: ['Reviews', 'রিভিউ', 'Reseñas', 'Avis', 'المراجعات', '评价'],
  navNotifications: ['E-mails', 'ইমেইল', 'Correos', 'E-mails', 'رسائل البريد', '邮件'],
  signedInAs: ['Signed in as', 'লগ ইন করা আছে', 'Sesión de', 'Connecté en tant que', 'مسجل باسم', '当前用户'],
  adminBadge: ['Admin', 'অ্যাডমিন', 'Admin', 'Admin', 'مسؤول', '管理员'],
  installApp: ['Install app', 'অ্যাপ ইনস্টল করুন', 'Instalar app', 'Installer l’app', 'تثبيت التطبيق', '安装应用'],
  // ---- banner / catalog ----
  bannerTitle: ['10% off your first order', 'প্রথম অর্ডারে ১০% ছাড়', '10% de descuento en tu primer pedido', '10 % de remise sur votre première commande', 'خصم 10% على طلبك الأول', '首单立减 10%'],
  bannerText: ['Use the code at checkout. Shipping is free from $50.', 'চেকআউটে কোডটা দিন। $50-এর বেশি অর্ডারে শিপিং ফ্রি।', 'Usa el código al pagar. Envío gratis desde $50.', 'Saisissez le code au paiement. Livraison offerte dès 50 $.', 'أدخل الرمز عند الدفع. الشحن مجاني من 50 دولارًا.', '结账时输入此代码。满 $50 免运费。'],
  bannerCode: ['Code', 'কোড', 'Código', 'Code', 'الرمز', '代码'],
  bannerShop: ['Browse the catalog', 'ক্যাটালগ দেখুন', 'Ver el catálogo', 'Parcourir le catalogue', 'تصفح الكتالوج', '浏览商品'],
  searchProducts: ['Search products', 'পণ্য খুঁজুন', 'Buscar productos', 'Rechercher des produits', 'ابحث عن المنتجات', '搜索商品'],
  showing: ['Showing {n} of {total} products', '{total}টার মধ্যে {n}টা পণ্য দেখানো হচ্ছে', 'Mostrando {n} de {total} productos', '{n} produits affichés sur {total}', 'عرض {n} من {total} منتجًا', '显示 {total} 件中的 {n} 件'],
  loadMore: ['Load more', 'আরও দেখুন', 'Cargar más', 'Afficher plus', 'عرض المزيد', '加载更多'],
  addToCart: ['Add to cart', 'কার্টে যোগ করুন', 'Añadir al carrito', 'Ajouter au panier', 'أضف إلى السلة', '加入购物车'],
  outOfStock: ['Out of stock', 'স্টকে নেই', 'Agotado', 'Rupture de stock', 'غير متوفر', '缺货'],
  stock: ['Stock', 'স্টক', 'Existencias', 'Stock', 'المخزون', '库存'],
  saveWishlist: ['Save to wishlist', 'পছন্দের তালিকায় রাখুন', 'Guardar en favoritos', 'Ajouter à la liste d’envies', 'احفظ في المفضلة', '加入心愿单'],
  noProducts: ['No products match this search. Try a shorter word.', 'এই খোঁজে কোনো পণ্য নেই। ছোট শব্দ দিয়ে চেষ্টা করুন।', 'Ningún producto coincide. Prueba con una palabra más corta.', 'Aucun produit ne correspond. Essayez un mot plus court.', 'لا توجد منتجات مطابقة. جرّب كلمة أقصر.', '没有匹配的商品，请尝试更短的关键词。'],
  // ---- customer pages ----
  cartTitle: ['Your cart', 'আপনার কার্ট', 'Tu carrito', 'Votre panier', 'سلتك', '我的购物车'],
  checkoutTitle: ['Checkout', 'চেকআউট', 'Pago', 'Paiement', 'الدفع', '结账'],
  shipToTitle: ['Delivery address', 'ডেলিভারির ঠিকানা', 'Dirección de entrega', 'Adresse de livraison', 'عنوان التوصيل', '收货地址'],
  shipToLead: ['We ship inside the United States.', 'আমরা শুধু যুক্তরাষ্ট্রের ভেতরে পাঠাই।', 'Enviamos dentro de Estados Unidos.', 'Nous livrons aux États-Unis.', 'نشحن داخل الولايات المتحدة.', '我们仅在美国境内发货。'],
  addrFullName: ['Full name', 'পুরো নাম', 'Nombre completo', 'Nom complet', 'الاسم الكامل', '姓名'],
  addrLine1: ['Street address', 'রাস্তার ঠিকানা', 'Dirección', 'Adresse', 'عنوان الشارع', '街道地址'],
  addrLine2: ['Apartment, suite (optional)', 'অ্যাপার্টমেন্ট, স্যুট (ঐচ্ছিক)', 'Apartamento, piso (opcional)', 'Appartement, étage (facultatif)', 'الشقة، الجناح (اختياري)', '公寓、单元（可选）'],
  addrCity: ['City', 'শহর', 'Ciudad', 'Ville', 'المدينة', '城市'],
  addrState: ['State', 'রাজ্য', 'Estado', 'État', 'الولاية', '州'],
  addrZip: ['ZIP code', 'ZIP কোড', 'Código postal (ZIP)', 'Code postal (ZIP)', 'الرمز البريدي (ZIP)', '邮编 (ZIP)'],
  addrPhone: ['Phone (optional)', 'ফোন (ঐচ্ছিক)', 'Teléfono (opcional)', 'Téléphone (facultatif)', 'الهاتف (اختياري)', '电话（可选）'],
  addrChooseState: ['Choose a state', 'রাজ্য বেছে নিন', 'Elige un estado', 'Choisissez un État', 'اختر الولاية', '选择州'],
  addrMissing: ['Please fill in this field', 'এই ঘরটা পূরণ করুন', 'Completa este campo', 'Veuillez remplir ce champ', 'يرجى ملء هذا الحقل', '请填写此项'],
  shipsTo: ['Ships to', 'যেখানে যাবে', 'Se envía a', 'Livraison à', 'يُشحن إلى', '寄送至'],
  ordersTitle: ['Your orders', 'আপনার অর্ডার', 'Tus pedidos', 'Vos commandes', 'طلباتك', '我的订单'],
  wishlistTitle: ['Your wishlist', 'আপনার পছন্দের তালিকা', 'Tus favoritos', 'Votre liste d’envies', 'مفضلتك', '我的心愿单'],
  // ---- auth ----
  loginTitle: ['Log in', 'লগ ইন', 'Iniciar sesión', 'Se connecter', 'تسجيل الدخول', '登录'],
  registerTitle: ['Create account', 'অ্যাকাউন্ট খুলুন', 'Crear cuenta', 'Créer un compte', 'إنشاء حساب', '注册账户'],
  loginLead: ['Welcome back. Log in to see your cart and orders.', 'আবার স্বাগতম। কার্ট আর অর্ডার দেখতে লগ ইন করুন।', 'Hola de nuevo. Inicia sesión para ver tu carrito y pedidos.', 'Bon retour. Connectez-vous pour voir votre panier et vos commandes.', 'مرحبًا بعودتك. سجّل الدخول لرؤية سلتك وطلباتك.', '欢迎回来。登录后可查看购物车和订单。'],
  registerLead: ['One account for cart, wishlist, orders and returns.', 'কার্ট, পছন্দের তালিকা, অর্ডার আর রিটার্নের জন্য একটাই অ্যাকাউন্ট।', 'Una cuenta para carrito, favoritos, pedidos y devoluciones.', 'Un seul compte pour le panier, la liste d’envies, les commandes et les retours.', 'حساب واحد للسلة والمفضلة والطلبات والمرتجعات.', '一个账户即可管理购物车、心愿单、订单和退货。'],
  email: ['Email', 'ইমেইল', 'Correo electrónico', 'E-mail', 'البريد الإلكتروني', '电子邮箱'],
  password: ['Password', 'পাসওয়ার্ড', 'Contraseña', 'Mot de passe', 'كلمة المرور', '密码'],
  confirmPassword: ['Repeat password', 'পাসওয়ার্ড আবার লিখুন', 'Repite la contraseña', 'Répétez le mot de passe', 'أعد كتابة كلمة المرور', '再次输入密码'],
  showPassword: ['Show password', 'পাসওয়ার্ড দেখান', 'Mostrar contraseña', 'Afficher le mot de passe', 'إظهار كلمة المرور', '显示密码'],
  pwHint: ['8 to 72 characters', '৮ থেকে ৭২ অক্ষর', 'De 8 a 72 caracteres', '8 à 72 caractères', 'من 8 إلى 72 حرفًا', '8 到 72 个字符'],
  loginBtn: ['Log in', 'লগ ইন', 'Iniciar sesión', 'Se connecter', 'تسجيل الدخول', '登录'],
  registerBtn: ['Register', 'রেজিস্টার করুন', 'Registrarme', 'S’inscrire', 'تسجيل', '注册'],
  backToLogin: ['Back to log in', 'লগ ইন পাতায় ফিরুন', 'Volver a iniciar sesión', 'Retour à la connexion', 'العودة إلى تسجيل الدخول', '返回登录'],
  forgotLink: ['Forgot your password?', 'পাসওয়ার্ড ভুলে গেছেন?', '¿Olvidaste tu contraseña?', 'Mot de passe oublié ?', 'هل نسيت كلمة المرور؟', '忘记密码？'],
  forgotTitle: ['Reset your password', 'পাসওয়ার্ড রিসেট করুন', 'Restablece tu contraseña', 'Réinitialisez votre mot de passe', 'إعادة تعيين كلمة المرور', '重置密码'],
  forgotLead: ['Enter the e-mail of your account. We will send you a link to choose a new password.', 'আপনার অ্যাকাউন্টের ইমেইল দিন। নতুন পাসওয়ার্ড বাছার একটা লিংক পাঠাব।', 'Escribe el correo de tu cuenta. Te enviaremos un enlace para elegir una contraseña nueva.', 'Saisissez l’e-mail de votre compte. Nous vous enverrons un lien pour choisir un nouveau mot de passe.', 'أدخل بريد حسابك. سنرسل لك رابطًا لاختيار كلمة مرور جديدة.', '输入账户邮箱，我们会发送一个设置新密码的链接。'],
  forgotSend: ['Send the link', 'লিংক পাঠান', 'Enviar el enlace', 'Envoyer le lien', 'إرسال الرابط', '发送链接'],
  forgotSent: ['If an account exists for this e-mail, we have sent a link to reset the password. It works once, for 30 minutes.', 'এই ইমেইলে অ্যাকাউন্ট থাকলে পাসওয়ার্ড রিসেটের লিংক পাঠানো হয়েছে। লিংকটা একবার, ৩০ মিনিট কাজ করবে।', 'Si existe una cuenta con este correo, te hemos enviado un enlace. Funciona una vez, durante 30 minutos.', 'Si un compte existe pour cet e-mail, nous avons envoyé un lien. Il fonctionne une fois, pendant 30 minutes.', 'إذا كان هناك حساب بهذا البريد، فقد أرسلنا رابطًا. يعمل مرة واحدة لمدة 30 دقيقة.', '如果该邮箱有账户，我们已发送重置链接。链接仅可使用一次，30 分钟内有效。'],
  resetTitle: ['Choose a new password', 'নতুন পাসওয়ার্ড বেছে নিন', 'Elige una contraseña nueva', 'Choisissez un nouveau mot de passe', 'اختر كلمة مرور جديدة', '设置新密码'],
  newPassword: ['New password', 'নতুন পাসওয়ার্ড', 'Contraseña nueva', 'Nouveau mot de passe', 'كلمة المرور الجديدة', '新密码'],
  resetSave: ['Save the new password', 'নতুন পাসওয়ার্ড সংরক্ষণ করুন', 'Guardar la contraseña', 'Enregistrer le mot de passe', 'حفظ كلمة المرور', '保存新密码'],
  resetDone: ['Your password was changed and every device was signed out. Log in with the new password.', 'পাসওয়ার্ড বদলানো হয়েছে, সব ডিভাইস থেকে লগ আউট হয়েছে। নতুন পাসওয়ার্ড দিয়ে লগ ইন করুন।', 'Tu contraseña se cambió y se cerró la sesión en todos los dispositivos. Inicia sesión con la nueva.', 'Votre mot de passe a été changé et tous les appareils ont été déconnectés. Connectez-vous avec le nouveau.', 'تم تغيير كلمة المرور وتسجيل الخروج من كل الأجهزة. سجّل الدخول بكلمة المرور الجديدة.', '密码已更改，所有设备均已退出登录。请用新密码登录。'],
  resetNoToken: ['This page needs the link from the e-mail.', 'এই পাতার জন্য ইমেইলের লিংকটা লাগবে।', 'Esta página necesita el enlace del correo.', 'Cette page nécessite le lien de l’e-mail.', 'تحتاج هذه الصفحة إلى الرابط من البريد.', '此页面需要邮件中的链接。'],
  toRegister: ['New customer? Create an account', 'নতুন গ্রাহক? অ্যাকাউন্ট খুলুন', '¿Cliente nuevo? Crea una cuenta', 'Nouveau client ? Créez un compte', 'عميل جديد؟ أنشئ حسابًا', '新顾客？创建账户'],
  toLogin: ['Already registered? Log in', 'আগেই রেজিস্টার করেছেন? লগ ইন করুন', '¿Ya tienes cuenta? Inicia sesión', 'Déjà inscrit ? Connectez-vous', 'لديك حساب؟ سجّل الدخول', '已有账户？登录'],
  vEmailRequired: ['Enter your email address.', 'ইমেইল ঠিকানা লিখুন।', 'Escribe tu correo electrónico.', 'Saisissez votre adresse e-mail.', 'أدخل بريدك الإلكتروني.', '请输入电子邮箱。'],
  vEmailInvalid: ['Enter an email like name@example.com.', 'name@example.com-এর মতো ইমেইল লিখুন।', 'Escribe un correo como nombre@ejemplo.com.', 'Saisissez une adresse comme nom@exemple.com.', 'أدخل بريدًا مثل name@example.com.', '请输入类似 name@example.com 的邮箱。'],
  vPwShort: ['Use at least 8 characters.', 'কমপক্ষে ৮টা অক্ষর দিন।', 'Usa al menos 8 caracteres.', 'Utilisez au moins 8 caractères.', 'استخدم 8 أحرف على الأقل.', '至少 8 个字符。'],
  vPwLong: ['Use at most 72 characters.', 'সর্বোচ্চ ৭২টা অক্ষর দিন।', 'Usa como máximo 72 caracteres.', 'Utilisez au plus 72 caractères.', 'استخدم 72 حرفًا كحد أقصى.', '最多 72 个字符。'],
  vPwMatch: ['The two passwords are different.', 'দুটো পাসওয়ার্ড মিলছে না।', 'Las contraseñas no coinciden.', 'Les deux mots de passe sont différents.', 'كلمتا المرور غير متطابقتين.', '两次输入的密码不一致。'],
  vRequired: ['This field is required.', 'এই ঘর পূরণ করতে হবে।', 'Este campo es obligatorio.', 'Ce champ est obligatoire.', 'هذا الحقل مطلوب.', '此项为必填。'],
  vPositive: ['Enter a number greater than 0.', '০-এর চেয়ে বড় সংখ্যা দিন।', 'Escribe un número mayor que 0.', 'Saisissez un nombre supérieur à 0.', 'أدخل رقمًا أكبر من 0.', '请输入大于 0 的数字。'],
  vWhole: ['Enter a whole number, 0 or more.', '০ বা তার বেশি পূর্ণ সংখ্যা দিন।', 'Escribe un número entero, 0 o más.', 'Saisissez un nombre entier, 0 ou plus.', 'أدخل عددًا صحيحًا، 0 أو أكثر.', '请输入 0 或以上的整数。'],
  vMoney: ['Use at most 2 decimals, e.g. 19.99.', 'দশমিকের পরে সর্বোচ্চ ২ ঘর, যেমন 19.99।', 'Usa como máximo 2 decimales, p. ej. 19.99.', '2 décimales au plus, ex. 19.99.', 'منزلتان عشريتان كحد أقصى، مثل 19.99.', '最多两位小数，例如 19.99。'],
  vCode: ['Letters, digits, _ and - only.', 'শুধু অক্ষর, সংখ্যা, _ আর -।', 'Solo letras, números, _ y -.', 'Lettres, chiffres, _ et - uniquement.', 'أحرف وأرقام و _ و - فقط.', '仅限字母、数字、_ 和 -。'],
  vPercent: ['A percent discount must be 100 or less.', 'শতাংশ ছাড় ১০০ বা তার কম হতে হবে।', 'Un descuento porcentual debe ser 100 o menos.', 'Une remise en pourcentage doit être de 100 au plus.', 'يجب ألا يزيد الخصم بالنسبة عن 100.', '百分比折扣不能超过 100。'],
  // ---- admin ----
  adminOnly: ['This area is for store admins. Log in with an admin account.', 'এই অংশ শুধু স্টোর অ্যাডমিনের জন্য। অ্যাডমিন অ্যাকাউন্ট দিয়ে লগ ইন করুন।', 'Esta zona es para administradores. Inicia sesión con una cuenta de administrador.', 'Cet espace est réservé aux administrateurs. Connectez-vous avec un compte admin.', 'هذا القسم لمسؤولي المتجر. سجّل الدخول بحساب مسؤول.', '此区域仅限商店管理员，请使用管理员账户登录。'],
  dashTitle: ['Store dashboard', 'স্টোর ড্যাশবোর্ড', 'Panel de la tienda', 'Tableau de bord', 'لوحة المتجر', '商店仪表板'],
  statOrders: ['Orders', 'অর্ডার', 'Pedidos', 'Commandes', 'الطلبات', '订单'],
  statToShip: ['Paid, waiting to ship', 'পেমেন্ট হয়েছে, শিপের অপেক্ষায়', 'Pagados, por enviar', 'Payées, à expédier', 'مدفوعة بانتظار الشحن', '已付款待发货'],
  statRevenue: ['Revenue from paid orders', 'পেমেন্ট হওয়া অর্ডারের আয়', 'Ingresos de pedidos pagados', 'Chiffre des commandes payées', 'إيرادات الطلبات المدفوعة', '已付款订单收入'],
  statLowStock: ['Products with 5 or fewer in stock', '৫ বা কম স্টকের পণ্য', 'Productos con 5 o menos', 'Produits avec 5 ou moins en stock', 'منتجات مخزونها 5 أو أقل', '库存不超过 5 的商品'],
  statReturns: ['Returns to decide', 'সিদ্ধান্তের অপেক্ষায় রিটার্ন', 'Devoluciones pendientes', 'Retours à traiter', 'مرتجعات بانتظار القرار', '待处理退货'],
  productsTitle: ['Products', 'পণ্য', 'Productos', 'Produits', 'المنتجات', '商品'],
  addProduct: ['Add product', 'পণ্য যোগ করুন', 'Añadir producto', 'Ajouter un produit', 'إضافة منتج', '添加商品'],
  editProduct: ['Edit product', 'পণ্য সম্পাদনা', 'Editar producto', 'Modifier le produit', 'تعديل المنتج', '编辑商品'],
  edit: ['Edit', 'সম্পাদনা', 'Editar', 'Modifier', 'تعديل', '编辑'],
  saveProduct: ['Save product', 'পণ্য সংরক্ষণ করুন', 'Guardar producto', 'Enregistrer le produit', 'حفظ المنتج', '保存商品'],
  updateProduct: ['Update product', 'পণ্য আপডেট করুন', 'Actualizar producto', 'Mettre à jour le produit', 'تحديث المنتج', '更新商品'],
  delete: ['Delete', 'মুছুন', 'Eliminar', 'Supprimer', 'حذف', '删除'],
  cancel: ['Cancel', 'বাতিল', 'Cancelar', 'Annuler', 'إلغاء', '取消'],
  confirmDelete: ['Delete “{name}”? Customers will no longer see it; order history keeps it.', '“{name}” মুছবেন? গ্রাহকরা আর দেখবেন না; অর্ডারের ইতিহাসে থেকে যাবে।', '¿Eliminar «{name}»? Los clientes dejarán de verlo; el historial de pedidos lo conserva.', 'Supprimer « {name} » ? Les clients ne le verront plus ; l’historique des commandes le garde.', 'حذف «{name}»؟ لن يراه العملاء بعد الآن، ويبقى في سجل الطلبات.', '删除“{name}”？顾客将看不到它，订单记录会保留。'],
  yesDelete: ['Yes, delete', 'হ্যাঁ, মুছুন', 'Sí, eliminar', 'Oui, supprimer', 'نعم، احذف', '确认删除'],
  productSaved: ['Product saved', 'পণ্য সংরক্ষিত হয়েছে', 'Producto guardado', 'Produit enregistré', 'تم حفظ المنتج', '商品已保存'],
  productUpdated: ['Product updated', 'পণ্য আপডেট হয়েছে', 'Producto actualizado', 'Produit mis à jour', 'تم تحديث المنتج', '商品已更新'],
  productDeleted: ['Product deleted', 'পণ্য মুছে ফেলা হয়েছে', 'Producto eliminado', 'Produit supprimé', 'تم حذف المنتج', '商品已删除'],
  name: ['Name', 'নাম', 'Nombre', 'Nom', 'الاسم', '名称'],
  sku: ['SKU', 'SKU', 'SKU', 'SKU', 'رمز المنتج', 'SKU'],
  category: ['Category', 'ক্যাটাগরি', 'Categoría', 'Catégorie', 'الفئة', '类别'],
  price: ['Price ($)', 'দাম ($)', 'Precio ($)', 'Prix ($)', 'السعر ($)', '价格 ($)'],
  actions: ['Actions', 'কাজ', 'Acciones', 'Actions', 'إجراءات', '操作'],
  filter: ['Search by product name', 'পণ্যের নাম দিয়ে খুঁজুন', 'Buscar por nombre del producto', 'Rechercher par nom de produit', 'البحث باسم المنتج', '按商品名称搜索'],
  noRows: ['Nothing here yet.', 'এখনো কিছু নেই।', 'Todavía no hay nada.', 'Rien pour l’instant.', 'لا يوجد شيء بعد.', '暂无内容。'],
  stockHistory: ['Stock history', 'স্টকের ইতিহাস', 'Historial de stock', 'Historique du stock', 'سجل المخزون', '库存记录'],
  couponsTitle: ['Coupons', 'কুপন', 'Cupones', 'Coupons', 'القسائم', '优惠券'],
  createCoupon: ['Create coupon', 'কুপন তৈরি করুন', 'Crear cupón', 'Créer le coupon', 'إنشاء قسيمة', '创建优惠券'],
  couponCreated: ['Coupon created', 'কুপন তৈরি হয়েছে', 'Cupón creado', 'Coupon créé', 'تم إنشاء القسيمة', '优惠券已创建'],
  code: ['Code', 'কোড', 'Código', 'Code', 'الرمز', '代码'],
  type: ['Type', 'ধরন', 'Tipo', 'Type', 'النوع', '类型'],
  value: ['Value', 'মান', 'Valor', 'Valeur', 'القيمة', '数值'],
  minOrder: ['Minimum order ($)', 'সর্বনিম্ন অর্ডার ($)', 'Pedido mínimo ($)', 'Commande minimum ($)', 'الحد الأدنى للطلب ($)', '最低订单金额 ($)'],
  maxUses: ['Maximum uses (optional)', 'সর্বোচ্চ ব্যবহার (ঐচ্ছিক)', 'Usos máximos (opcional)', 'Utilisations max. (facultatif)', 'الحد الأقصى للاستخدام (اختياري)', '最多使用次数（可选）'],
  used: ['Used', 'ব্যবহৃত', 'Usado', 'Utilisé', 'مستخدم', '已用'],
  ordersAdminTitle: ['All orders', 'সব অর্ডার', 'Todos los pedidos', 'Toutes les commandes', 'كل الطلبات', '全部订单'],
  order: ['Order', 'অর্ডার', 'Pedido', 'Commande', 'الطلب', '订单'],
  status: ['Status', 'অবস্থা', 'Estado', 'Statut', 'الحالة', '状态'],
  total: ['Total', 'মোট', 'Total', 'Total', 'الإجمالي', '合计'],
  customer: ['Customer', 'গ্রাহক', 'Cliente', 'Client', 'العميل', '顾客'],
  allStatuses: ['All statuses', 'সব অবস্থা', 'Todos los estados', 'Tous les statuts', 'كل الحالات', '全部状态'],
  ship: ['Ship', 'শিপ করুন', 'Enviar', 'Expédier', 'شحن', '发货'],
  deliver: ['Mark delivered', 'ডেলিভারি হয়েছে', 'Marcar entregado', 'Marquer livrée', 'تم التسليم', '标记已送达'],
  carrier: ['Carrier', 'ক্যারিয়ার', 'Transportista', 'Transporteur', 'شركة الشحن', '承运商'],
  orderShipped: ['Order shipped', 'অর্ডার শিপ হয়েছে', 'Pedido enviado', 'Commande expédiée', 'تم شحن الطلب', '订单已发货'],
  orderDelivered: ['Order marked as delivered', 'অর্ডার ডেলিভারি হয়েছে', 'Pedido marcado como entregado', 'Commande marquée livrée', 'تم تعليم الطلب كمُسلَّم', '订单已标记为送达'],
  returnsTitle: ['Returns', 'রিটার্ন', 'Devoluciones', 'Retours', 'المرتجعات', '退货'],
  reason: ['Reason', 'কারণ', 'Motivo', 'Motif', 'السبب', '原因'],
  refund: ['Refund', 'রিফান্ড', 'Reembolso', 'Remboursement', 'المبلغ المسترد', '退款'],
  approve: ['Approve', 'অনুমোদন', 'Aprobar', 'Approuver', 'موافقة', '批准'],
  reject: ['Reject', 'প্রত্যাখ্যান', 'Rechazar', 'Refuser', 'رفض', '拒绝'],
  returnApproved: ['Return approved and refunded', 'রিটার্ন অনুমোদিত, টাকা ফেরত দেওয়া হয়েছে', 'Devolución aprobada y reembolsada', 'Retour approuvé et remboursé', 'تمت الموافقة على الإرجاع واسترداد المبلغ', '退货已批准并退款'],
  returnRejected: ['Return rejected', 'রিটার্ন প্রত্যাখ্যাত', 'Devolución rechazada', 'Retour refusé', 'تم رفض الإرجاع', '退货已拒绝'],
  reviewsTitle: ['Reviews', 'রিভিউ', 'Reseñas', 'Avis', 'المراجعات', '评价'],
  notificationsTitle: ['Customer e-mails', 'গ্রাহকের ইমেইল', 'Correos a clientes', 'E-mails clients', 'رسائل العملاء', '客户邮件'],
  notificationsHint: ['Order e-mails are sent in the background a few seconds after each change. Failed ones are retried 5 times.', 'প্রতিটা পরিবর্তনের কয়েক সেকেন্ড পর অর্ডারের ইমেইল পাঠানো হয়। ব্যর্থ হলে ৫ বার আবার চেষ্টা হয়।', 'Los correos se envían en segundo plano unos segundos después de cada cambio. Los fallidos se reintentan 5 veces.', 'Les e-mails partent en arrière-plan quelques secondes après chaque changement. Les échecs sont réessayés 5 fois.', 'تُرسل رسائل الطلبات في الخلفية بعد ثوانٍ من كل تغيير. تُعاد المحاولة 5 مرات عند الفشل.', '每次变更后几秒钟内在后台发送邮件。失败的邮件会重试 5 次。'],
  recipient: ['Recipient', 'প্রাপক', 'Destinatario', 'Destinataire', 'المستلم', '收件人'],
  subject: ['Subject', 'বিষয়', 'Asunto', 'Objet', 'الموضوع', '主题'],
  attempts: ['{n} attempts', '{n} বার চেষ্টা', '{n} intentos', '{n} tentatives', '{n} محاولات', '{n} 次尝试'],
  sendAgain: ['Send again', 'আবার পাঠান', 'Enviar de nuevo', 'Renvoyer', 'إعادة الإرسال', '重新发送'],
  emailQueued: ['E-mail queued again', 'ইমেইল আবার পাঠানোর তালিকায়', 'Correo en cola de nuevo', 'E-mail remis en file', 'أُعيدت الرسالة إلى قائمة الإرسال', '邮件已重新排队'],
  rating: ['Rating', 'রেটিং', 'Valoración', 'Note', 'التقييم', '评分'],
  review: ['Review', 'রিভিউ', 'Reseña', 'Avis', 'المراجعة', '评价'],
  hide: ['Hide', 'লুকান', 'Ocultar', 'Masquer', 'إخفاء', '隐藏'],
  publish: ['Publish', 'প্রকাশ করুন', 'Publicar', 'Publier', 'نشر', '发布'],
  reviewHidden: ['Review hidden', 'রিভিউ লুকানো হয়েছে', 'Reseña oculta', 'Avis masqué', 'تم إخفاء المراجعة', '评价已隐藏'],
  reviewPublished: ['Review published', 'রিভিউ প্রকাশিত হয়েছে', 'Reseña publicada', 'Avis publié', 'تم نشر المراجعة', '评价已发布'],
  product: ['Product', 'পণ্য', 'Producto', 'Produit', 'المنتج', '商品'],
  date: ['Date', 'তারিখ', 'Fecha', 'Date', 'التاريخ', '日期'],
  change: ['Change', 'পরিবর্তন', 'Cambio', 'Variation', 'التغيير', '变动'],
  stockAfter: ['Stock after', 'পরের স্টক', 'Stock después', 'Stock après', 'المخزون بعد', '变动后库存'],
  close: ['Close', 'বন্ধ করুন', 'Cerrar', 'Fermer', 'إغلاق', '关闭'],
  loading: ['Loading…', 'লোড হচ্ছে…', 'Cargando…', 'Chargement…', 'جارٍ التحميل…', '加载中…'],
  picture: ['Picture', 'ছবি', 'Imagen', 'Image', 'الصورة', '图片'],
  pictureHint: ['/products/name.svg or an https:// link. Empty: the category picture.', '/products/name.svg অথবা https:// লিংক। খালি রাখলে ক্যাটাগরির ছবি।', '/products/nombre.svg o un enlace https://. Vacío: la imagen de la categoría.', '/products/nom.svg ou un lien https://. Vide : l’image de la catégorie.', '‎/products/name.svg أو رابط https://. فارغ: صورة الفئة.', '/products/name.svg 或 https:// 链接。留空：使用分类图片。'],
  vImage: ['Use /products/<name>.svg, .png, .jpg, .webp or an https:// link.', '/products/<name>.svg, .png, .jpg, .webp অথবা https:// লিংক দিন।', 'Usa /products/<nombre>.svg, .png, .jpg, .webp o un enlace https://.', 'Utilisez /products/<nom>.svg, .png, .jpg, .webp ou un lien https://.', 'استخدم ‎/products/<name>.svg أو .png أو .jpg أو .webp أو رابط https://.', '请使用 /products/<名称>.svg、.png、.jpg、.webp 或 https:// 链接。'],
  // ---- audit trail ----
  navAudit: ['Audit trail', 'অডিট ট্রেইল', 'Registro de auditoría', 'Piste d’audit', 'سجل التدقيق', '审计记录'],
  auditTitle: ['Audit trail', 'অডিট ট্রেইল', 'Registro de auditoría', 'Piste d’audit', 'سجل التدقيق', '审计记录'],
  auditHint: ['Every change to a product and every order status change: who, when, from what to what. Entries can never be edited or deleted.', 'প্রতিটা পণ্যের পরিবর্তন আর অর্ডারের অবস্থা বদল: কে, কখন, কী থেকে কী। কোনো এন্ট্রি কখনো বদলানো বা মোছা যায় না।', 'Cada cambio de un producto y de estado de un pedido: quién, cuándo, de qué a qué. Las entradas nunca se pueden editar ni borrar.', 'Chaque modification d’un produit et chaque changement de statut d’une commande : qui, quand, de quoi à quoi. Les entrées ne peuvent jamais être modifiées ni supprimées.', 'كل تغيير على منتج وكل تغيير في حالة طلب: من، ومتى، ومن ماذا إلى ماذا. لا يمكن تعديل الإدخالات أو حذفها أبدًا.', '每次商品变更和订单状态变更：谁、何时、从什么改为什么。记录永远无法编辑或删除。'],
  auditVerify: ['Check integrity', 'অখণ্ডতা যাচাই', 'Comprobar integridad', 'Vérifier l’intégrité', 'التحقق من السلامة', '校验完整性'],
  auditExport: ['Download CSV', 'CSV ডাউনলোড', 'Descargar CSV', 'Télécharger le CSV', 'تنزيل CSV', '下载 CSV'],
  auditValid: ['Intact: all {n} entries match their hash chain.', 'অক্ষত: {n}টি এন্ট্রিই হ্যাশ চেইনের সাথে মেলে।', 'Intacto: las {n} entradas coinciden con su cadena de hash.', 'Intacte : les {n} entrées correspondent à leur chaîne de hachage.', 'سليم: جميع الإدخالات ({n}) تطابق سلسلة التجزئة.', '完整：全部 {n} 条记录与哈希链一致。'],
  auditBroken: ['Broken at entry #{seq}', 'এন্ট্রি #{seq}-এ চেইন ভাঙা', 'Rota en la entrada #{seq}', 'Rompue à l’entrée n°{seq}', 'منقطع عند الإدخال #{seq}', '在第 #{seq} 条记录处断开'],
  auditRecord: ['Record', 'রেকর্ড', 'Registro', 'Élément', 'السجل', '对象'],
  auditAllRecords: ['Products and orders', 'পণ্য ও অর্ডার', 'Productos y pedidos', 'Produits et commandes', 'المنتجات والطلبات', '商品和订单'],
  auditProduct: ['Product', 'পণ্য', 'Producto', 'Produit', 'منتج', '商品'],
  auditOrder: ['Order', 'অর্ডার', 'Pedido', 'Commande', 'طلب', '订单'],
  auditId: ['ID', 'আইডি', 'ID', 'ID', 'المعرّف', '编号'],
  auditActor: ['Changed by', 'যিনি বদলেছেন', 'Cambiado por', 'Modifié par', 'بواسطة', '修改人'],
  auditShow: ['Show', 'দেখাও', 'Mostrar', 'Afficher', 'عرض', '显示'],
  auditChange: ['Change', 'পরিবর্তন', 'Cambio', 'Modification', 'التغيير', '变更'],
  auditOld: ['Before', 'আগে', 'Antes', 'Avant', 'قبل', '之前'],
  auditNew: ['After', 'পরে', 'Después', 'Après', 'بعد', '之后'],
  // ---- help ----
  helpTitle: ['Help center', 'সাহায্য কেন্দ্র', 'Centro de ayuda', 'Centre d’aide', 'مركز المساعدة', '帮助中心'],
  helpLead: ['Answers for shopping, payment, returns and running the store.', 'কেনাকাটা, পেমেন্ট, রিটার্ন আর স্টোর চালানোর উত্তর।', 'Respuestas sobre compras, pagos, devoluciones y gestión de la tienda.', 'Réponses sur les achats, le paiement, les retours et la gestion de la boutique.', 'إجابات عن التسوق والدفع والإرجاع وإدارة المتجر.', '关于购物、付款、退货和店铺管理的解答。'],
  helpSearch: ['Search help', 'সাহায্যে খুঁজুন', 'Buscar en la ayuda', 'Rechercher dans l’aide', 'ابحث في المساعدة', '搜索帮助'],
  helpNone: ['No help topic matches this search.', 'এই খোঁজে কোনো বিষয় পাওয়া যায়নি।', 'Ningún tema coincide con la búsqueda.', 'Aucun sujet ne correspond à cette recherche.', 'لا يوجد موضوع مطابق للبحث.', '没有匹配的帮助主题。'],
  topics: ['Topics', 'বিষয়', 'Temas', 'Sujets', 'المواضيع', '主题'],
};

const read = () => {
  try { const v = localStorage.getItem('lang'); return ORDER.includes(v) ? v : 'en'; } catch { return 'en'; }
};
const listeners = new Set();

function apply(code) {
  const lang = LANGUAGES.find(l => l.code === code) || LANGUAGES[0];
  document.documentElement.lang = lang.code;
  document.documentElement.dir = lang.dir;
}
apply(read());

export function setLanguage(code) {
  try { localStorage.setItem('lang', code); } catch { /* private mode: language lasts for this page only */ }
  apply(code);
  listeners.forEach(l => l(code));
}

/** t('showing', {n: 3, total: 9}) - falls back to English when a text is missing. */
export function translate(lang, key, vars) {
  const row = T[key];
  let s = row ? (row[ORDER.indexOf(lang)] ?? row[0]) : key;
  if (vars) Object.entries(vars).forEach(([k, v]) => { s = s.replaceAll(`{${k}}`, v); });
  return s;
}

export function useI18n() {
  const [lang, setLang] = useState(read());
  useEffect(() => {
    listeners.add(setLang);
    return () => listeners.delete(setLang);
  }, []);
  return { lang, t: (key, vars) => translate(lang, key, vars), setLanguage };
}
