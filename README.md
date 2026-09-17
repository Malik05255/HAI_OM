# HAI OM

تطبيق Android مفتوح المصدر يعمل كـ **Free-only AI Coding Agent** مرتبط بـ GitHub.

## ما الذي ينفذه الآن؟

- Android Native بـ Kotlin + Jetpack Compose وواجهة عربية RTL.
- لا توجد أي شاشة دفع أو مزود مدفوع.
- `FreeModelCatalog` عبارة عن allowlist؛ أي model/endpoint غير معروف يتم رفضه من داخل الكود.
- ترتيب نماذج البرمجة ثم fallback تلقائي عند rate-limit أو تعطل نموذج.
- ربط GitHub عبر Fine-grained Personal Access Token محفوظ محليًا بتشفير Android Keystore.
- إعطاء رابط المستودع + مواصفات العمل.
- قراءة ملفات المشروع المهمة وبناء context محدود وآمن.
- تقسيم الطلب إلى مهام تلقائيًا.
- إنشاء فرع `hai-agent/*` بدل العبث بـ `main` مباشرة.
- تعديل الملفات مهمة بعد مهمة.
- انتظار GitHub Actions بعد كل مهمة.
- عند فشل CI: تنزيل logs، تحليلها، إصلاح الكود، وإعادة الاختبار حتى 5 مرات.
- عند اكتمال جميع المهام: إنشاء Pull Request تلقائيًا.
- GitHub Actions يبني APK ويشغّل unit tests وlint.

## سياسة المجانية

الإصدار الأول يعتمد افتراضيًا على نماذج Pollinations الموثقة كـ keyless/free في سجل OmniRoute الحالي. مفتاح Pollinations اختياري فقط لرفع حدود الاستخدام؛ التطبيق لا يحتاجه للبدء ولا يحتوي على billing.

> المجانية لا تعني موارد غير محدودة. عند انتهاء حدود جميع المصادر المجانية يتوقف التطبيق برسالة واضحة بدل الانتقال إلى خدمة مدفوعة.

## تشغيل البناء محليًا

المشروع لا يضم Gradle Wrapper ثنائيًا عمدًا في bootstrap الأول. استخدم Gradle 8.10.2 + JDK 17:

```bash
gradle testDebugUnitTest lintDebug assembleDebug
```

أو استخدم GitHub Actions؛ سيظهر `HAI-OM-debug-apk` كـ artifact بعد نجاح البناء.

## صلاحيات GitHub المقترحة للتوكن

استخدم Fine-grained PAT وحدده للمستودعات التي تريد أن يعمل عليها HAI OM فقط. يحتاج عمليًا إلى صلاحيات Contents وPull requests وActions بالقدر اللازم للتعديل، إنشاء PR، وقراءة نتائج CI.

## أمان الوكيل

- لا تعديل مباشر على `main`.
- منع مسارات `..` و`.git`.
- حد أقصى لعدد الملفات في الدفعة وحجم الملف.
- حد أقصى 5 محاولات إصلاح لكل فشل CI.
- يمنع prompt الوكيل إضافة خدمات مدفوعة أو أسرار.
- التوكنات لا تُكتب في سجلات التطبيق أو المستودع.

## الحالة

`0.1.0` — أساس قابل للبناء مع واجهة + router + GitHub autonomous agent + CI self-healing.
