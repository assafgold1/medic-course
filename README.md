# מסלול חובש

לימוד, תרגול ומבחנים לקורס חובשים: 49 נושאים ו-556 שאלות, לפי מצגות הקורס.

- **אתר:** https://assafgold1.github.io/medic-course/ (נבנה מתיקיית `docs/`)
- **Android (Kotlin):** `android/`. האפליקציה פותחת את האתר, ואם אין אינטרנט היא מציגה עותק שמובנה בה.
- **iOS (Swift):** `ios/`. אותו דבר. הפרויקט נוצר עם XcodeGen (`xcodegen generate`).

## עדכון תוכן
אחרי שינוי ב-`docs/index.html`, מעתיקים אותו גם לאפליקציות (עבור מצב לא מקוון):

```bash
cp docs/index.html android/app/src/main/assets/index.html
cp docs/index.html ios/Medic/index.html
```

החומר נועד לחזרה ולתרגול, ואינו מחליף את מצגות הקורס או את הפרוטוקולים הרשמיים.
