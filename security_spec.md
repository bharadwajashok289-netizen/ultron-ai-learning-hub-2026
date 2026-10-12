# Ultron Security Specification (Payload-First Security TDD)

## 1. Data Invariants
1. **Authentication Gate**: No unauthenticated user (`request.auth == null`) may read or write any document in any collection.
2. **User Isolation & PII Protection**: A user profile `/users/{userId}` and all its subcollections (`tasks`, `conversations`, `flashcard_decks`, `quizzes`, `focus_sessions`, `homework_sessions`, `achievements`) can only be read or modified by `request.auth.uid == userId` or a verified administrator (`isAdmin()`).
3. **Anti Self-Upgrade (Subscription & Role Protection)**: On `/users/{userId}` creation, a non-admin user must initialize `plan == 'FREE'` and `role in ['STUDENT', 'TEACHER', 'PRINCIPAL']`. On `/users/{userId}` update, a non-admin user cannot modify `plan`, `subscriptionStatus`, `subscriptionStartDate`, `subscriptionExpiryDate`, or escalate `role` to `'ADMIN'`.
4. **Manual UPI Payment Integrity**: On `/payment_requests/{requestId}` creation, `userId == request.auth.uid` and `status == 'PENDING'`. Only `isAdmin()` may update `/payment_requests/{requestId}` (to `'APPROVED'` or `'REJECTED'`). Deletion is forbidden (`allow delete: if false`).
5. **Temporal Integrity**: All created/updated documents must supply server timestamps (`<= request.time`), and `createdAt` and ownership fields (`userId`, `creatorId`) are immutable across updates.
6. **Bounded Strings & IDs**: All document IDs must match `^[a-zA-Z0-9_\-]+$` (max 128 chars) and all string fields have strict `.size()` upper bounds.

## 2. The "Dirty Dozen" Adversarial Payloads
1. **Unauthenticated Read**: `unauthDb.collection("users").doc("alice").get()` -> REJECTED.
2. **Cross-User Profile Read (PII Leak)**: `bobDb.collection("users").doc("alice").get()` -> REJECTED.
3. **Self-Assigned Pro Plan on Signup**: `aliceDb.collection("users").doc("alice").set({ ..., plan: "PRO" })` -> REJECTED.
4. **Self-Upgrade Plan on Profile Update**: `aliceDb.collection("users").doc("alice").update({ plan: "PRO" })` -> REJECTED.
5. **Shadow Field Injection on Task**: `aliceDb.collection("users/alice/tasks").doc("t1").set({ ..., isVerified: true })` -> REJECTED (`hasOnly` check).
6. **Payment Self-Approval by Student**: `aliceDb.collection("payment_requests").doc("req1").update({ status: "APPROVED" })` -> REJECTED.
7. **Payment Creation with Pre-Approved Status**: `aliceDb.collection("payment_requests").doc("req1").set({ ..., status: "APPROVED" })` -> REJECTED.
8. **Admin Email Spoofing (Unverified Email)**: Token with `email: "manaskakkunuri55@gmail.com", email_verified: false` attempting admin payment approval -> REJECTED.
9. **Unfiltered Query on Payment Requests**: `aliceDb.collection("payment_requests").get()` without `.where("userId", "==", "alice")` -> REJECTED.
10. **Immutable Field Tampering (`createdAt` / `userId`)**: Updating `createdAt` or `userId` on an existing task -> REJECTED.
11. **Oversized String / Resource Poisoning**: Creating a task with a 10,000-char title -> REJECTED.
12. **Unauthorized School Portal Creation by Student**: Student with `creatorRole: "STUDENT"` attempting to create a `/school_portals/{portalId}` document -> REJECTED.
