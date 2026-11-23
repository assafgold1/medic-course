# App Review notes — מסלול חובש

Copy the English section below into **App Store Connect → App Review Information → Notes**.
Fill in every `[[...]]` first. Only you can do the items in "Before you resubmit".

## Before you resubmit (checklist)

- [ ] **Deploy the server** (`deploy/deploy-server.sh`) before you resubmit. Block and report are new endpoints, and the reviewer's build talks to production.
- [ ] **Check reports daily** while the app is in review and afterwards. You promised review within 24 hours. How to do it: `deploy/MODERATION.md`.
- [ ] **Demo accounts** on the production server (`https://uh.taninoni.com`). Enter them below and in the "Sign-in required" fields:
  - **student**: sign up in the app.
  - **instructor**: sign up in the app, then add its email to `MEDIC_INSTRUCTORS` on the server and restart the service.
  - Make the two accounts friends, so the reviewer sees a board that already has entries. Make a third spare account a friend of the student too, so the reviewer can block or report someone and still have a friend left.
- [ ] **Screen recording on a physical iPhone, latest iOS.** Start with the app closed and record in this order:
  1. Launch the app (the opening animation).
  2. Register a new account (all three sign-up steps).
  3. Home: the streak card, then open a topic (text and images).
  4. A short practice quiz with an explanation, then the results screen.
  5. The streak celebration, then the streak page with the reminder switch (allow notifications).
  6. The defibrillator simulator.
  7. Friends: share code, add a friend by code, the weekly board. Then tap a friend: **block** them, add them back, tap again and **report** them (pick a reason, then "דיווח וחסימה").
  8. *(Optional)* Add the home-screen widget and tap it to open the app.
  9. Sign out, then sign in again.
  10. Settings → חשבון וסנכרון → מחיקת חשבון: delete the account, then show that signing in with it now fails.

  Upload the video (iCloud/Drive/YouTube unlisted) and paste the link into item 1.
- [ ] **Item 6, the course material.** The content is based on the medic course presentations. Make sure you have permission to use that material and its images, and say so in item 6 (attach or describe the written permission). Without permission, Apple is likely to reject under 5.2 (intellectual property).
- [ ] **Keyword "מד״א"** in `STORE_LISTING.md` is Magen David Adom's name. Unless MDA has authorised you, remove it from the keywords: it's another 5.2 risk.
- [ ] `STORE_LISTING.md` is out of date: its review note says an anonymous account is created automatically, its privacy answers are wrong, and its counts are wrong. Don't reuse any of it. Update the App Privacy answers too: name and organisation are shown to friends.
- [ ] Replace `DOMAIN` in the privacy and support URLs if you haven't already.

---

## Notes (English, for App Review)

**1. Screen recording (physical iPhone, latest iOS):** [[link to video]]

The recording starts at launch and shows registration, the main study flow, the friends feature with block and report, sign-out and sign-in, and account deletion.

**2. Purpose and audience**
מסלול חובש ("Medic Track") is a free study app for students in Israeli emergency medical technician (EMT, "חובש") courses, and for their instructors. Students struggle to review a large syllabus and practise for the course exam. The app turns the syllabus into structured study material and practice:
- 50 topics in five chapters
- about 800 multiple-choice questions, with an explanation for every answer
- over 400 illustrative images and diagrams, plus field-protocol flowcharts
- flashcards and a "numbers to memorise" table
- a timed final exam with a pass mark, and practice of the student's own mistakes
- a daily study streak, with optional reminders and a home-screen widget
- an AED (defibrillator) training simulator

Instructors can follow their students' progress.

The app is for study and practice only. It doesn't replace the course, its instructors or official medical protocols, and it gives no medical advice or diagnosis. The interface and content are in Hebrew.

**3. How to use it, and demo logins**
You need an account, because progress syncs between devices and instructors follow each student's progress.

- Student demo account: email `[[student email]]`, password `[[password]]`
- Instructor demo account: email `[[instructor email]]`, password `[[password]]`

New users can create an account from the sign-in screen (the "הרשמה" tab, three short steps: name and form of address, organisation, email and password).

Main features after signing in:
- **Home (נושאים tab):** the streak card, mastery, smart practice (תרגול חכם) and the final exam (מבחן מסכם). Tap a topic to read it and take its quiz.
- **Practice (תרגול tab):** choose categories and start a quiz.
- **Defibrillator (דפיברילטור tab):** press the green button and follow the voice and on-screen prompts.
- **Numbers (מספרים tab):** reference tables.
- **Settings (הגדרות tab):** the account, the streak and daily reminder, the theme and text size. Instructors also see "התקדמות החניכים" (student progress).
- **Account deletion:** Settings → חשבון וסנכרון (Account and sync) → מחיקת חשבון (Delete account). This immediately and permanently deletes the account and all its data (progress, friends) from our server. It's also described at [[privacy URL]]#delete.

**Friends (חברים card on Home) and user safety:** each user has a private 6-character code. A friendship exists only when one user enters the other's code, which they have to receive directly from that person. Users can't search or browse other users. Friends see only each other's display name, organisation and study statistics (streak, questions this week, mastery %). The app has no messaging, posts or free-text content between users.
- To try it, sign in as the student demo account: it's already friends with the instructor demo account and with a spare account.
- Tap a friend on the board to remove, **block** or **report** them.
- **Block:** removes both users from each other's boards, and the blocked user can't add you again.
- **Report:** you pick a reason and can add details. The report is stored on our server for review, and the reported user is blocked at once.
- We review reports within 24 hours, and rename or remove offending names and accounts.
- The rules (no offensive, misleading or impersonating names) and a support email for reports are on our privacy page: [[privacy URL]]#friends.

There are no in-app purchases, subscriptions or ads, and no paid content.

**4. External services**
- Our own backend server (`uh.taninoni.com`, self-hosted, HTTPS) handles accounts, syncing study progress, the friends board (including blocks and reports), and content updates.
- The app uses no third-party authentication, analytics, advertising, payment or AI services.
- On-device Apple frameworks only: AVSpeechSynthesizer (the simulator's spoken prompts), local notifications (the optional daily study reminder), and WidgetKit (the home-screen widget, which reads data the app stores locally in an App Group).

**5. Regional differences**
None. The app works the same in every region. It's in Hebrew and is aimed at students in Israeli EMT courses.

**6. Regulated industry / third-party material**
The app is an educational study aid. It isn't a medical device, and it provides no diagnosis, treatment or medical services. The AED simulator is a training tool and doesn't connect to or control any device. [[Statement about the course material, e.g. "The study content is based on the EMT course presentations and is used with the written permission of …", plus any documentation.]]
