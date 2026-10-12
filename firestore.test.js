const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_student_1";
const BOB_UID = "bob_student_2";
const ADMIN_UID = "manas_admin_1";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

function buildValidProfile(uid, role = "STUDENT", plan = "FREE") {
  const now = new Date();
  return {
    userId: uid,
    fullName: "Alice Sharma",
    email: "alice@school.edu",
    studentClass: "Class 12",
    section: "A",
    schoolName: "National Public School",
    dob: "2008-06-15",
    age: 17,
    role: role,
    language: "English",
    themePreference: "DARK",
    plan: plan,
    subscriptionStatus: "ACTIVE",
    chatsUsed: 0,
    imagesUsed: 0,
    usageMonth: "2026-10",
    xp: 0,
    level: 1,
    currentStreak: 1,
    longestStreak: 1,
    onboardingCompleted: true,
    createdAt: now,
    updatedAt: now,
  };
}

test("1. Unauthenticated user cannot read user profiles or tasks", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("tasks").get());
});

test("2. Authenticated student can create their own FREE profile, but cannot self-assign PRO plan", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidProfile(ALICE_UID, "STUDENT", "PRO"))
  );
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidProfile(ALICE_UID, "STUDENT", "FREE"))
  );
});

test("3. Cross-user profile read is rejected (PII isolation)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(buildValidProfile(ALICE_UID, "STUDENT", "FREE"))
  );

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
});

test("4. Student cannot self-upgrade their plan via profile update", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const profile = buildValidProfile(ALICE_UID, "STUDENT", "FREE");
  await assertSucceeds(aliceDb.collection("users").doc(ALICE_UID).set(profile));

  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      plan: "PRO",
      updatedAt: new Date(),
    })
  );

  // Updating XP or language is allowed
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      xp: 25,
      language: "Hindi",
      updatedAt: new Date(),
    })
  );
});

test("5. Shadow update test: injecting ghost field on task is rejected", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("tasks").doc("task_1").set({
      userId: ALICE_UID,
      title: "Revise Calculus",
      subject: "Mathematics",
      deadline: "2026-10-12",
      priority: "HIGH",
      category: "TODAY",
      completed: false,
      xpAwarded: false,
      isVerified: true, // Ghost field
      createdAt: now,
      updatedAt: now,
    })
  );
});

test("6. Student can create PENDING UPI payment request, cannot self-approve, and verified Admin can approve", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();

  const validRequest = {
    userId: ALICE_UID,
    studentName: "Alice Sharma",
    studentEmail: "alice@school.edu",
    selectedPlan: "PRO",
    amount: 299,
    utr: "428910394812",
    status: "PENDING",
    processedByAdmin: "",
    adminNote: "",
    notificationStatus: "SENT",
    createdAt: now,
    updatedAt: now,
  };

  // Cannot create already APPROVED request
  await assertFails(
    aliceDb.collection("payment_requests").doc("req_1").set({
      ...validRequest,
      status: "APPROVED",
    })
  );

  // Can create PENDING request
  await assertSucceeds(
    aliceDb.collection("payment_requests").doc("req_1").set(validRequest)
  );

  // Student cannot self-approve
  await assertFails(
    aliceDb.collection("payment_requests").doc("req_1").update({
      status: "APPROVED",
      processedByAdmin: "alice@school.edu",
      updatedAt: new Date(),
    })
  );

  // Spoofed admin with email_verified: false is rejected
  const spoofAdminDb = testEnv.authenticatedContext("spoof_uid", {
    email: "manaskakkunuri55@gmail.com",
    email_verified: false,
  }).firestore();
  await assertFails(
    spoofAdminDb.collection("payment_requests").doc("req_1").update({
      status: "APPROVED",
      processedByAdmin: "manaskakkunuri55@gmail.com",
      updatedAt: new Date(),
    })
  );

  // Verified official admin can approve
  const realAdminDb = testEnv.authenticatedContext(ADMIN_UID, {
    email: "manaskakkunuri55@gmail.com",
    email_verified: true,
  }).firestore();
  await assertSucceeds(
    realAdminDb.collection("payment_requests").doc("req_1").update({
      status: "APPROVED",
      processedByAdmin: "manaskakkunuri55@gmail.com",
      adminNote: "Verified UTR in SBI statement",
      notificationStatus: "APPROVED_NOTIFIED",
      updatedAt: new Date(),
    })
  );
});

test("7. Client query alignment on payment_requests", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const now = new Date();
  await assertSucceeds(
    aliceDb.collection("payment_requests").doc("req_alice").set({
      userId: ALICE_UID,
      studentName: "Alice Sharma",
      studentEmail: "alice@school.edu",
      selectedPlan: "BASIC",
      amount: 99,
      utr: "998877665544",
      status: "PENDING",
      processedByAdmin: "",
      adminNote: "",
      notificationStatus: "SENT",
      createdAt: now,
      updatedAt: now,
    })
  );

  // Query with userId filter succeeds
  await assertSucceeds(
    aliceDb.collection("payment_requests").where("userId", "==", ALICE_UID).get()
  );

  // Query without userId filter fails for non-admin
  await assertFails(
    aliceDb.collection("payment_requests").get()
  );
});
