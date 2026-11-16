const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_uid_101";
const BOB_UID = "bob_uid_202";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

function pastTimestamp() {
  return new Date(Date.now() - 2000);
}

function validProfile(uid, ts = pastTimestamp()) {
  return {
    userId: uid,
    displayName: "Alice Scholar",
    avatarPreset: "owl",
    weeklyGoalMinutes: 600,
    pomodoroFocusMinutes: 25,
    pomodoroBreakMinutes: 5,
    totalXp: 1200,
    weeklyXp: 350,
    streakDays: 4,
    blockedPackages: ["com.instagram.android", "com.google.android.youtube"],
    shareOnLeaderboard: true,
    twoFactorEnabled: true,
    hasShieldPassword: true,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validSecurityPii(uid, ts = pastTimestamp()) {
  return {
    userId: uid,
    email: "alice@university.edu",
    phoneNumber: "+15551234567",
    totpSecret: "JBSWY3DPEHPK3PXP",
    shieldPasswordHash: "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validStudySession(uid, ts = pastTimestamp()) {
  return {
    userId: uid,
    subject: "Calculus II",
    durationMinutes: 25,
    xpEarned: 250,
    mode: "pomodoro",
    shieldEnabled: true,
    notes: "Integration by parts practice",
    sessionDate: "2026-11-16",
    createdAt: ts,
    updatedAt: ts,
  };
}

function validExamReminder(uid, ts = pastTimestamp()) {
  return {
    userId: uid,
    title: "Organic Chemistry Midterm",
    subject: "Chemistry",
    examDateMillis: Date.now() + 86400000 * 5,
    targetHours: 20,
    completedHours: 4.5,
    reminderMinutesBefore: 120,
    priority: "high",
    completed: false,
    createdAt: ts,
    updatedAt: ts,
  };
}

function validLeaderboardEntry(uid, ts = pastTimestamp(), isPublic = true) {
  return {
    userId: uid,
    displayName: "Alice Scholar",
    avatarPreset: "owl",
    totalXp: 1200,
    weeklyXp: 350,
    weeklyStudyMinutes: 180,
    streakDays: 4,
    level: 3,
    isPublic,
    createdAt: ts,
    updatedAt: ts,
  };
}

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

test("Unauthenticated users cannot read or write any collection", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("study_sessions").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("exams").get());
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("private").doc("security").get());
  await assertFails(unauthDb.collection("leaderboard").where("isPublic", "==", true).get());
});

test("User profile: owner can create and update, cross-user and ghost fields fail", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).set(validProfile(ALICE_UID))
  );

  // Cross-user read & write must fail
  await assertFails(bobDb.collection("users").doc(ALICE_UID).get());
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).update({ displayName: "Hacked", updatedAt: pastTimestamp() })
  );

  // Shadow update with ghost field must fail
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).update({
      displayName: "Alice",
      isAdmin: true,
      updatedAt: pastTimestamp(),
    })
  );

  // Valid update succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).update({
      weeklyXp: 400,
      updatedAt: pastTimestamp(),
    })
  );
});

test("Private PII security doc: strictly owner-only", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("security")
      .set(validSecurityPii(ALICE_UID))
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("private").doc("security").get()
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("private").doc("security").get()
  );
});

test("Study sessions: owner CRUD works, invalid mode/bounds fail, cross-user fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_sessions").doc("sess_1")
      .set(validStudySession(ALICE_UID))
  );

  // Invalid mode fails
  const badSession = validStudySession(ALICE_UID);
  badSession.mode = "cheating";
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_sessions").doc("sess_bad").set(badSession)
  );

  // Owner list succeeds
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("study_sessions").get()
  );

  // Cross-user list fails
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("study_sessions").get()
  );
});

test("Exams: owner CRUD works and completedHours number check passes", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("exams").doc("exam_1")
      .set(validExamReminder(ALICE_UID))
  );

  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("exams").doc("exam_1")
      .update({ completedHours: 8.5, completed: true, updatedAt: pastTimestamp() })
  );

  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("exams").get()
  );
});

test("Leaderboard: public query works with filter, unfiltered list fails, cross-user write fails", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();

  await assertSucceeds(
    aliceDb.collection("leaderboard").doc(ALICE_UID)
      .set(validLeaderboardEntry(ALICE_UID))
  );

  // Filtered query matching rule succeeds for Bob
  await assertSucceeds(
    bobDb.collection("leaderboard").where("isPublic", "==", true).get()
  );

  // Unfiltered list fails (rules are not filters)
  await assertFails(
    bobDb.collection("leaderboard").get()
  );

  // Bob cannot overwrite Alice's leaderboard entry
  await assertFails(
    bobDb.collection("leaderboard").doc(ALICE_UID).update({
      weeklyXp: 9999,
      updatedAt: pastTimestamp(),
    })
  );
});
