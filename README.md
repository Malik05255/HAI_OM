# HAI OM

تطبيق Android مفتوح المصدر يعمل كـ **Free-only autonomous coding agent** مرتبط بـ GitHub، ويستخدم **OmniRoute فقط** كبوابة AI.

## المعمارية

```text
HAI OM Android
├─ GitHub API        → قراءة المستودع / الفروع / commits / Actions / PR
└─ OmniRoute API     → النماذج / free routing / fallback / quota / compression
                       └─ providers managed entirely by OmniRoute
```

HAI OM لا يحتوي تكاملات منفصلة مع Pollinations أو Gemini أو Groq أو غيرها، ولا يحتاج معرفة عدد المزودين داخل OmniRoute.

## ما الذي ينفذه الآن؟

- Android Native بـ Kotlin + Jetpack Compose وواجهة عربية RTL.
- اتصال واحد فقط للذكاء الاصطناعي: `OmniRoute`.
- مسار البرمجة الافتراضي: `auto/coding`.
- يفرض على OmniRoute قبل التنفيذ `freeAccessPolicy: strict`؛ إذا لم يمكن التحقق منه يرفض تشغيل الوكيل بدل المخاطرة بمسار مدفوع.
- يفعّل ضغط السياق في OmniRoute باستخدام pipeline: `RTK → Caveman`.
- يرسل CI/build logs كـ tool context حتى يستفيد RTK من ضغط مخرجات الأدوات.
- يجلب `Free Provider Rankings` لفئة Coding من OmniRoute ويرتبها من الأقوى إلى الأقل حسب score المقدم من OmniRoute.
- ربط GitHub عبر Fine-grained Personal Access Token محفوظ محليًا بتشفير Android Keystore.
- إعطاء رابط المستودع + مواصفات العمل فقط.
- قراءة ملفات المشروع المهمة وبناء context محدود.
- تقسيم الطلب إلى مهام تلقائيًا.
- إنشاء فرع `hai-agent/*` بدل التعديل المباشر على `main`.
- تعديل الملفات مهمة بعد مهمة.
- انتظار GitHub Actions بعد كل مهمة.
- عند فشل CI: تنزيل logs، تحليلها، إصلاح الكود، وإعادة الاختبار حتى 5 مرات.
- عند اكتمال جميع المهام: إنشاء Pull Request تلقائيًا.
- GitHub Actions يبني APK ويشغّل unit tests وlint.

## سياسة المجانية

التطبيق نفسه لا يقرر أي Provider مجاني أو مدفوع. OmniRoute هو مصدر الحقيقة، وHAI OM يطلب منه وضع **strict zero-cost** قبل أي طلب AI.

> المجانية لا تعني موارد غير محدودة. إذا لم يعد هناك مسار مجاني آمن، يجب أن يفشل الطلب بدل الانتقال لمسار يمكن أن يسبب فوترة.

## اتصال OmniRoute

الافتراضي محليًا:

```text
http://127.0.0.1:20128
```

يمكن استخدام عنوان LAN خاص مثل `192.168.x.x` عبر HTTP. أي خادم عام يجب أن يستخدم HTTPS؛ التطبيق يرفض HTTP العام.

إذا كان OmniRoute محميًا، أدخل API/management key يسمح بتعديل `/api/settings` و`/api/settings/compression` حتى يتمكن HAI OM من فرض strict zero-cost والضغط.

## تشغيل البناء محليًا

استخدم Gradle 8.10.2 + JDK 17:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

أو استخدم GitHub Actions؛ سيظهر `HAI-OM-debug-apk` كـ artifact بعد نجاح البناء.

## صلاحيات GitHub المقترحة

استخدم Fine-grained PAT وحدده للمستودعات التي تريد أن يعمل عليها HAI OM فقط. يحتاج عمليًا إلى Contents وPull requests وActions بالقدر اللازم للتعديل، إنشاء PR، وقراءة نتائج CI.

## أمان الوكيل

- لا تعديل مباشر على `main`.
- منع مسارات `..` و`.git`.
- حد أقصى لعدد الملفات في الدفعة وحجم الملف.
- حد أقصى 5 محاولات إصلاح لكل فشل CI.
- منع الخدمات المدفوعة في prompts الوكيل، مع حماية أقوى على مستوى OmniRoute عبر strict zero-cost.
- التوكنات ومفتاح OmniRoute تُخزن عبر Android Keystore ولا تُكتب في المستودع.
- HTTP العام مرفوض؛ HTTP مسموح فقط لـ loopback والشبكات الخاصة.

## الحالة

`0.2.0-dev` — OmniRoute-only routing + GitHub autonomous agent + strict zero-cost + RTK/Caveman compression + CI self-healing.
