# Blood for Daudkandi — Android starter project

এটি একটি Android/Kotlin + Jetpack Compose + Firebase starter project। এটি এখনো compile করা APK নয়।

## এখন যা আছে
- ইমেইল/পাসওয়ার্ড দিয়ে অ্যাডমিন লগইন
- Firestore থেকে লাইভ ডোনার তালিকা
- নাম/এলাকা/মোবাইল দিয়ে সার্চ
- ডোনারের নাম ও মোবাইল নম্বর আলাদা Copy বাটনে কপি
- একক মালিক/অ্যাডমিন: শুধু নির্দিষ্ট Firebase Auth UID ডোনার ডেটা নিয়ন্ত্রণ করতে পারবে
- রক্তের গ্রুপ ফিল্টার
- নতুন ডোনার যোগ, সম্পাদনা ও মুছে ফেলা
- ফোন নম্বরে চাপলে Android Dialer খোলা (সরাসরি কল নয়; ব্যবহারকারী কল নিশ্চিত করবেন)

## APK বানানোর আগে প্রয়োজন
1. Firebase Console-এ নতুন project তৈরি করুন।
2. Android app যোগ করুন: `bd.bloodfordaudkandi.app`
3. `google-services.json` ডাউনলোড করে `app/` ফোল্ডারে রাখুন।
4. Authentication থেকে Email/Password চালু করুন।
5. Cloud Firestore database তৈরি করুন।
6. `firestore.rules`-এর নিয়ম Firebase Console-এ প্রকাশ করুন।
7. Authentication থেকে অ্যাডমিনদের account তৈরি করুন।
8. প্রতিটি অনুমোদিত অ্যাডমিনের UID দিয়ে Firestore-এর `admins/{uid}` document তৈরি করুন; document data: `{ "active": true, "role": "admin" }`।
9. Android Studio-তে project খুলে Gradle sync করুন এবং Build > Build APK(s) দিন।

## নিরাপত্তা
- কেবল আপনার নির্দিষ্ট Firebase Auth UID-ই ডোনার ডেটা পড়তে/লিখতে পারবে।
- Firestore rules ছাড়া কখনো production data চালু করবেন না।
- ডোনারের নাম, ফোন ও এলাকা ব্যক্তিগত তথ্য। অনুমতি নিয়ে সংগ্রহ করুন এবং কেবল প্রয়োজনীয় অ্যাডমিনদের প্রবেশাধিকার দিন।
- ডোনারের সম্মতি ছাড়া তথ্য প্রকাশ বা গণ-বার্তা পাঠাবেন না।

## পরবর্তী উন্নয়ন
- সুপার অ্যাডমিন প্যানেল
- অ্যাডমিন invite/disable
- CSV/Excel export
- ডোনারের সম্মতি, availability ও last verified date
- backup/export এবং audit log
- বাংলা তারিখ/ফোন নম্বর validation
