import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import {
  assertFails,
  assertSucceeds,
  initializeTestEnvironment,
} from "@firebase/rules-unit-testing";
import {
  deleteDoc,
  doc,
  getDoc,
  setDoc,
  Timestamp,
  updateDoc,
} from "firebase/firestore";

const projectId = "demo-roadar-rules";
const reportId = "2b250fac-94df-4f07-971a-feb4e28131b2";
let testEnvironment;

before(async () => {
  testEnvironment = await initializeTestEnvironment({
    projectId,
    firestore: {
      rules: await readFile(new URL("../firestore.rules", import.meta.url), "utf8"),
    },
  });
});

after(async () => {
  await testEnvironment.cleanup();
});

beforeEach(async () => {
  await testEnvironment.clearFirestore();
});

function validReport(id = reportId) {
  return {
    reportId: id,
    latitude: 14.5995,
    longitude: 120.9842,
    confidence: 0.87,
    detectedAt: Timestamp.fromMillis(1_800_000_000_000),
    createdAt: Timestamp.fromMillis(1_800_000_001_000),
    locationAccuracy: 12,
  };
}

function reportDocument(db, uid, id = reportId) {
  return doc(db, "users", uid, "reports", id);
}

test("owner can create, read, and delete a valid report", async () => {
  const ownerDb = testEnvironment.authenticatedContext("owner-1").firestore();
  const reference = reportDocument(ownerDb, "owner-1");

  await assertSucceeds(setDoc(reference, validReport()));
  await assertSucceeds(getDoc(reference));
  await assertSucceeds(deleteDoc(reference));
});

test("a different authenticated user cannot access another user's report", async () => {
  const ownerDb = testEnvironment.authenticatedContext("owner-1").firestore();
  const otherDb = testEnvironment.authenticatedContext("owner-2").firestore();
  await assertSucceeds(
    setDoc(reportDocument(ownerDb, "owner-1"), validReport()),
  );

  await assertFails(getDoc(reportDocument(otherDb, "owner-1")));
  await assertFails(
    setDoc(reportDocument(otherDb, "owner-1"), validReport()),
  );
  await assertFails(deleteDoc(reportDocument(otherDb, "owner-1")));
});

test("unauthenticated access is denied", async () => {
  const db = testEnvironment.unauthenticatedContext().firestore();
  const reference = reportDocument(db, "owner-1");

  await assertFails(setDoc(reference, validReport()));
  await assertFails(getDoc(reference));
  await assertFails(deleteDoc(reference));
});

test("missing and extra fields are rejected", async () => {
  const db = testEnvironment.authenticatedContext("owner-1").firestore();
  const reference = reportDocument(db, "owner-1");
  const { locationAccuracy, ...missingField } = validReport();

  await assertFails(setDoc(reference, missingField));
  await assertFails(setDoc(reference, { ...validReport(), privateNote: "unexpected" }));
});

test("invalid coordinates, confidence, accuracy, timestamp types, and IDs are rejected", async () => {
  const db = testEnvironment.authenticatedContext("owner-1").firestore();
  const reference = reportDocument(db, "owner-1");

  for (const invalid of [
    { ...validReport(), latitude: 90.01 },
    { ...validReport(), longitude: -180.01 },
    { ...validReport(), confidence: 1.01 },
    { ...validReport(), locationAccuracy: 1000.01 },
    { ...validReport(), detectedAt: 1_800_000_000_000 },
    { ...validReport(), reportId: "different-id" },
  ]) {
    await assertFails(setDoc(reference, invalid));
  }
});

test("report fields are immutable after creation", async () => {
  const db = testEnvironment.authenticatedContext("owner-1").firestore();
  const reference = reportDocument(db, "owner-1");
  await assertSucceeds(setDoc(reference, validReport()));

  await assertSucceeds(setDoc(reference, validReport()));
  await assertFails(updateDoc(reference, { confidence: 0.9 }));
});
