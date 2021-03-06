// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.model;

import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.fieldMask;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.mutationResult;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.transformMutation;
import static com.google.firebase.firestore.testutil.TestUtil.unknownDoc;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static com.google.firebase.firestore.testutil.TestUtil.wrapObject;
import static org.junit.Assert.assertEquals;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.model.mutation.ArrayTransformOperation;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.TransformMutation;
import com.google.firebase.firestore.model.value.ObjectValue;
import com.google.firebase.firestore.model.value.ServerTimestampValue;
import com.google.firebase.firestore.model.value.StringValue;
import com.google.firebase.firestore.model.value.TimestampValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests Mutations */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class MutationTest {
  @Test
  public void testAppliesSetsToDocuments() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation set = setMutation("collection/key", map("bar", "bar-value"));
    MaybeDocument setDoc = set.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    assertEquals(
        doc("collection/key", 0, map("bar", "bar-value"), Document.DocumentState.LOCAL_MUTATIONS),
        setDoc);
  }

  @Test
  public void testAppliesPatchToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    MaybeDocument local = patch.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"), "baz", "baz-value");
    assertEquals(
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS), local);
  }

  @Test
  public void testAppliesPatchWithMergeToDocuments() {
    MaybeDocument baseDoc = deletedDoc("collection/key", 0);
    Mutation upsert =
        patchMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    MaybeDocument newDoc = upsert.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"));
    assertEquals(
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS), newDoc);
  }

  @Test
  public void testAppliesPatchToNullDocWithMergeToDocuments() {
    MaybeDocument baseDoc = null;
    Mutation upsert =
        patchMutation(
            "collection/key", map("foo.bar", "new-bar-value"), Arrays.asList(field("foo.bar")));
    MaybeDocument newDoc = upsert.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"));
    assertEquals(
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS), newDoc);
  }

  @Test
  public void testDeletesValuesFromTheFieldMask() {
    Map<String, Object> data = map("foo", map("bar", "bar-value", "baz", "baz-value"));
    Document baseDoc = doc("collection/key", 0, data);

    DocumentKey key = key("collection/key");
    FieldMask mask = fieldMask("foo.bar");
    Mutation patch = new PatchMutation(key, ObjectValue.emptyObject(), mask, Precondition.NONE);

    MaybeDocument patchDoc = patch.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("baz", "baz-value"));
    assertEquals(
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS), patchDoc);
  }

  @Test
  public void testPatchesPrimitiveValue() {
    Map<String, Object> data = map("foo", "foo-value", "baz", "baz-value");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation patch = patchMutation("collection/key", map("foo.bar", "new-bar-value"));
    MaybeDocument patchedDoc = patch.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    Map<String, Object> expectedData = map("foo", map("bar", "new-bar-value"), "baz", "baz-value");
    assertEquals(
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS), patchedDoc);
  }

  @Test
  public void testPatchingDeletedDocumentsDoesNothing() {
    MaybeDocument baseDoc = deletedDoc("collection/key", 0);
    Mutation patch = patchMutation("collection/key", map("foo", "bar"));
    MaybeDocument patchedDoc = patch.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    assertEquals(baseDoc, patchedDoc);
  }

  @Test
  public void testAppliesLocalServerTimestampTransformsToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    Document baseDoc = doc("collection/key", 0, data);

    Timestamp timestamp = Timestamp.now();
    Mutation transform =
        transformMutation("collection/key", map("foo.bar", FieldValue.serverTimestamp()));
    MaybeDocument transformedDoc = transform.applyToLocalView(baseDoc, baseDoc, timestamp);

    // Server timestamps aren't parsed, so we manually insert it.
    ObjectValue expectedData =
        wrapObject(map("foo", map("bar", "<server-timestamp>"), "baz", "baz-value"));
    expectedData =
        expectedData.set(
            field("foo.bar"),
            new ServerTimestampValue(timestamp, StringValue.valueOf("bar-value")));

    Document expectedDoc =
        new Document(
            key("collection/key"),
            version(0),
            expectedData,
            Document.DocumentState.LOCAL_MUTATIONS);
    assertEquals(expectedDoc, transformedDoc);
  }

  // NOTE: This is more a test of UserDataConverter code than Mutation code but we don't have unit
  // tests for it currently. We could consider removing this test once we have integration tests.
  @Test
  public void testCreateArrayUnionTransform() {
    TransformMutation transform =
        transformMutation(
            "collection/key",
            map(
                "a", FieldValue.arrayUnion("tag"),
                "bar.baz",
                    FieldValue.arrayUnion(true, map("nested", map("a", Arrays.asList(1, 2))))));
    assertEquals(2, transform.getFieldTransforms().size());

    FieldTransform first = transform.getFieldTransforms().get(0);
    assertEquals(field("a"), first.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Union(Collections.singletonList(wrap("tag"))),
        first.getOperation());

    FieldTransform second = transform.getFieldTransforms().get(1);
    assertEquals(field("bar.baz"), second.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Union(
            Arrays.asList(wrap(true), wrapObject(map("nested", map("a", Arrays.asList(1, 2)))))),
        second.getOperation());
  }

  // NOTE: This is more a test of UserDataConverter code than Mutation code but
  // we don't have unit tests for it currently. We could consider removing this
  // test once we have integration tests.
  @Test
  public void testCreateArrayRemoveTransform() {
    TransformMutation transform =
        transformMutation("collection/key", map("foo", FieldValue.arrayRemove("tag")));
    assertEquals(1, transform.getFieldTransforms().size());

    FieldTransform first = transform.getFieldTransforms().get(0);
    assertEquals(field("foo"), first.getFieldPath());
    assertEquals(
        new ArrayTransformOperation.Remove(Collections.singletonList(wrap("tag"))),
        first.getOperation());
  }

  @Test
  public void testAppliesLocalArrayUnionTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.arrayUnion(1, 2));
    Map<String, Object> expected = map("missing", Arrays.asList(1, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformToNonArrayField() {
    Map<String, Object> baseDoc = map("nonArray", 42);
    Map<String, Object> transform = map("nonArray", FieldValue.arrayUnion(1, 2));
    Map<String, Object> expected = map("nonArray", Arrays.asList(1, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithNonExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2, 4));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3, 2, 4));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(1, 3));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithDuplicateExistingElements() {
    // Duplicate entries in your existing array should be preserved.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 2, 2, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2));
    Map<String, Object> expected = map("array", Arrays.asList(1, 2, 2, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithDuplicateUnionElements() {
    // Duplicate entries in your union array should only be added once.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayUnion(2, 2));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3, 2));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithNonPrimitiveElements() {
    // Union nested object values (one existing, one not).
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayUnion(map("a", "b"), map("c", "d")));
    Map<String, Object> expected = map("array", Arrays.asList(1, map("a", "b"), map("c", "d")));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayUnionTransformWithPartiallyOverlappingElements() {
    // Union objects that partially overlap an existing object.
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b", "c", "d")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayUnion(map("a", "b"), map("c", "d")));
    Map<String, Object> expected =
        map("array", Arrays.asList(1, map("a", "b", "c", "d"), map("a", "b"), map("c", "d")));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformToMissingField() {
    Map<String, Object> baseDoc = map();
    Map<String, Object> transform = map("missing", FieldValue.arrayRemove(1, 2));
    Map<String, Object> expected = map("missing", Collections.emptyList());
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformToNonArrayField() {
    Map<String, Object> baseDoc = map("nonArray", 42);
    Map<String, Object> transform = map("nonArray", FieldValue.arrayRemove(1, 2));
    Map<String, Object> expected = map("nonArray", Collections.emptyList());
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithNonExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 3));
    Map<String, Object> transform = map("array", FieldValue.arrayRemove(2, 4));
    Map<String, Object> expected = map("array", Arrays.asList(1, 3));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithExistingElements() {
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, 2, 3, 4));
    Map<String, Object> transform = map("array", FieldValue.arrayRemove(1, 3));
    Map<String, Object> expected = map("array", Arrays.asList(2, 4));
    verifyTransform(baseDoc, transform, expected);
  }

  @Test
  public void testAppliesLocalArrayRemoveTransformWithNonPrimitiveElements() {
    // Remove nested object values (one existing, one not).
    Map<String, Object> baseDoc = map("array", Arrays.asList(1, map("a", "b")));
    Map<String, Object> transform =
        map("array", FieldValue.arrayRemove(map("a", "b"), map("c", "d")));
    Map<String, Object> expected = map("array", Arrays.asList(1));
    verifyTransform(baseDoc, transform, expected);
  }

  private void verifyTransform(
      Map<String, Object> baseData,
      Map<String, Object> transformData,
      Map<String, Object> expectedData) {
    Document baseDoc = doc("collection/key", 0, baseData);
    TransformMutation transform = transformMutation("collection/key", transformData);
    MaybeDocument transformedDoc = transform.applyToLocalView(baseDoc, baseDoc, Timestamp.now());

    Document expectedDoc =
        doc("collection/key", 0, expectedData, Document.DocumentState.LOCAL_MUTATIONS);
    assertEquals(expectedDoc, transformedDoc);
  }

  @Test
  public void testAppliesServerAckedServerTimestampTransformsToDocuments() {
    Map<String, Object> data = map("foo", map("bar", "bar-value"), "baz", "baz-value");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation transform =
        transformMutation("collection/key", map("foo.bar", FieldValue.serverTimestamp()));

    Timestamp serverTimestamp = new Timestamp(2, 0);

    MutationResult mutationResult =
        new MutationResult(
            version(1), Collections.singletonList(TimestampValue.valueOf(serverTimestamp)));

    MaybeDocument transformedDoc = transform.applyToRemoteDocument(baseDoc, mutationResult);

    Map<String, Object> expectedData =
        map("foo", map("bar", serverTimestamp.toDate()), "baz", "baz-value");
    assertEquals(
        doc("collection/key", 1, expectedData, Document.DocumentState.COMMITTED_MUTATIONS),
        transformedDoc);
  }

  @Test
  public void testAppliesServerAckedArrayTransformsToDocuments() {
    Map<String, Object> data =
        map("array1", Arrays.asList(1, 2), "array2", Arrays.asList("a", "b"));
    Document baseDoc = doc("collection/key", 0, data);
    Mutation transform =
        transformMutation(
            "collection/key",
            map("array1", FieldValue.arrayUnion(2, 3), "array2", FieldValue.arrayRemove("a", "c")));

    // Server just sends null transform results for array operations.
    MutationResult mutationResult =
        new MutationResult(version(1), Arrays.asList(wrap(null), wrap(null)));
    MaybeDocument transformedDoc = transform.applyToRemoteDocument(baseDoc, mutationResult);

    Map<String, Object> expectedData =
        map("array1", Arrays.asList(1, 2, 3), "array2", Arrays.asList("b"));
    assertEquals(
        doc("collection/key", 1, expectedData, Document.DocumentState.COMMITTED_MUTATIONS),
        transformedDoc);
  }

  @Test
  public void testDeleteDeletes() {
    Map<String, Object> data = map("foo", "bar");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation delete = deleteMutation("collection/key");
    MaybeDocument deletedDoc = delete.applyToLocalView(baseDoc, baseDoc, Timestamp.now());
    assertEquals(deletedDoc("collection/key", 0), deletedDoc);
  }

  @Test
  public void testSetWithMutationResult() {
    Map<String, Object> data = map("foo", "bar");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation set = setMutation("collection/key", map("foo", "new-bar"));
    MaybeDocument setDoc = set.applyToRemoteDocument(baseDoc, mutationResult(4));

    assertEquals(
        doc("collection/key", 4, map("foo", "new-bar"), Document.DocumentState.COMMITTED_MUTATIONS),
        setDoc);
  }

  @Test
  public void testPatchWithMutationResult() {
    Map<String, Object> data = map("foo", "bar");
    Document baseDoc = doc("collection/key", 0, data);

    Mutation patch = patchMutation("collection/key", map("foo", "new-bar"));
    MaybeDocument patchDoc = patch.applyToRemoteDocument(baseDoc, mutationResult(4));

    assertEquals(
        doc("collection/key", 4, map("foo", "new-bar"), Document.DocumentState.COMMITTED_MUTATIONS),
        patchDoc);
  }

  private void assertVersionTransitions(
      Mutation mutation,
      MaybeDocument base,
      MutationResult mutationResult,
      MaybeDocument expected) {
    MaybeDocument actual = mutation.applyToRemoteDocument(base, mutationResult);
    assertEquals(expected, actual);
  }

  @Test
  public void testTransitions() {
    Document docV3 = doc("collection/key", 3, map());
    NoDocument deletedV3 = deletedDoc("collection/key", 3);

    Mutation set = setMutation("collection/key", map());
    Mutation patch = patchMutation("collection/key", map());
    Mutation transform = transformMutation("collection/key", map());
    Mutation delete = deleteMutation("collection/key");

    NoDocument docV7Deleted = deletedDoc("collection/key", 7, /*hasCommittedMutations=*/ true);
    Document docV7Committed =
        doc("collection/key", 7, map(), Document.DocumentState.COMMITTED_MUTATIONS);
    UnknownDocument docV7Unknown = unknownDoc("collection/key", 7);

    MutationResult mutationResult = new MutationResult(version(7), /*transformResults=*/ null);
    MutationResult transformResult = new MutationResult(version(7), Collections.emptyList());

    assertVersionTransitions(set, docV3, mutationResult, docV7Committed);
    assertVersionTransitions(set, deletedV3, mutationResult, docV7Committed);
    assertVersionTransitions(set, null, mutationResult, docV7Committed);

    assertVersionTransitions(patch, docV3, mutationResult, docV7Committed);
    assertVersionTransitions(patch, deletedV3, mutationResult, docV7Unknown);
    assertVersionTransitions(patch, null, mutationResult, docV7Unknown);

    assertVersionTransitions(transform, docV3, transformResult, docV7Committed);
    assertVersionTransitions(transform, deletedV3, transformResult, docV7Unknown);
    assertVersionTransitions(transform, null, transformResult, docV7Unknown);

    assertVersionTransitions(delete, docV3, mutationResult, docV7Deleted);
    assertVersionTransitions(delete, deletedV3, mutationResult, docV7Deleted);
    assertVersionTransitions(delete, null, mutationResult, docV7Deleted);
  }
}
