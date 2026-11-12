// Protocol Buffers - Google's data interchange format
// Copyright 2008 Google Inc.  All rights reserved.
// https://developers.google.com/protocol-buffers/
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are
// met:
//
//     * Redistributions of source code must retain the above copyright
// notice, this list of conditions and the following disclaimer.
//     * Redistributions in binary form must reproduce the above
// copyright notice, this list of conditions and the following disclaimer
// in the documentation and/or other materials provided with the
// distribution.
//     * Neither the name of Google Inc. nor the names of its
// contributors may be used to endorse or promote products derived from
// this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
// "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
// LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
// A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
// OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
// SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
// LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
// DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
// THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.

package com.google.protobuf;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Test;
import org.junit.function.ThrowingRunnable;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import recursion_limit_lite_test.RecursionLimitLiteProto.RecursiveGroup;
import recursion_limit_lite_test.RecursionLimitLiteProto.RecursiveMessage;
import recursion_limit_lite_test.RecursionLimitLiteProto.TestAllTypesLite;
import recursion_limit_lite_test.RecursionLimitLiteProto.TestMessageSetLite;
import recursion_limit_lite_test.RecursionLimitLiteProto3.MapContainer;

@RunWith(JUnit4.class)
public class LiteRecursionLimitTest {
  private static final String NESTING = "Protocol message had too many levels of nesting";

  private static Throwable parseThrows(final ThrowingRunnable parse) {
    return assertThrows(InvalidProtocolBufferException.class, parse);
  }

  private static ThrowingRunnable parseByteString(final ByteString data) {
    return new ThrowingRunnable() {
      @Override
      public void run() throws Throwable {
        TestAllTypesLite.parseFrom(data);
      }
    };
  }

  private static ThrowingRunnable parseBytes(final ByteString data) {
    return new ThrowingRunnable() {
      @Override
      public void run() throws Throwable {
        // Should pass in byte[] instead of ByteString to go into ArrayDecoders.
        TestAllTypesLite.parseFrom(data.toByteArray());
      }
    };
  }

  private static void assertNoNestingErrorInThreads(final boolean useBytes) throws Exception {
    int numThreads = 200;
    ArrayList<Thread> threads = new ArrayList<Thread>();

    final ByteString byteString = generateNestingGroups(99);
    final AtomicBoolean thrown = new AtomicBoolean(false);

    for (int i = 0; i < numThreads; i++) {
      Thread thread =
          new Thread(
              new Runnable() {
                @Override
                public void run() {
                  try {
                    if (useBytes) {
                      TestAllTypesLite.parseFrom(byteString.toByteArray());
                    } else {
                      TestAllTypesLite.parseFrom(byteString);
                    }
                  } catch (IOException e) {
                    if (e.getMessage().contains(NESTING)) {
                      thrown.set(true);
                    }
                  }
                }
              });
      thread.start();
      threads.add(thread);
    }

    for (Thread thread : threads) {
      thread.join();
    }

    assertThat(thrown.get()).isFalse();
  }

  @Test
  public void testParseFromInputStream_concurrent_nestingUnknownGroups() throws Exception {
    assertNoNestingErrorInThreads(false);
  }

  @Test
  public void testParseFromInputStream_nestingUnknownGroups() throws IOException {
    Throwable thrown = parseThrows(parseByteString(generateNestingGroups(99)));
    assertThat(thrown).hasMessageThat().doesNotContain(NESTING);
  }

  @Test
  public void testParseFromInputStream_nestingUnknownGroups_exception() throws IOException {
    Throwable thrown = parseThrows(parseByteString(generateNestingGroups(100)));
    assertThat(thrown).hasMessageThat().contains(NESTING);
  }

  @Test
  public void testParseFromInputStream_setRecursionLimit_exception() throws IOException {
    ByteString byteString = generateNestingGroups(199);
    UnknownFieldSchema<?, ?> schema = SchemaUtil.unknownFieldSetLiteSchema();
    schema.setRecursionLimit(200);

    Throwable thrown = parseThrows(parseByteString(byteString));
    assertThat(thrown).hasMessageThat().doesNotContain(NESTING);
    schema.setRecursionLimit(UnknownFieldSchema.DEFAULT_RECURSION_LIMIT);
  }

  @Test
  public void testParseFromBytes_concurrent_nestingUnknownGroups() throws Exception {
    assertNoNestingErrorInThreads(true);
  }

  @Test
  public void testParseFromBytes_nestingUnknownGroups() throws IOException {
    Throwable thrown = parseThrows(parseBytes(generateNestingGroups(99)));
    assertThat(thrown).hasMessageThat().doesNotContain(NESTING);
  }

  @Test
  public void testParseFromBytes_nestingUnknownGroups_exception() throws IOException {
    Throwable thrown = parseThrows(parseBytes(generateNestingGroups(100)));
    assertThat(thrown).hasMessageThat().contains(NESTING);
  }

  @Test
  public void testParseFromBytes_setRecursionLimit_exception() throws IOException {
    ByteString byteString = generateNestingGroups(199);
    ArrayDecoders.setRecursionLimit(200);

    Throwable thrown = parseThrows(parseBytes(byteString));
    assertThat(thrown).hasMessageThat().doesNotContain(NESTING);
    ArrayDecoders.setRecursionLimit(ArrayDecoders.DEFAULT_RECURSION_LIMIT);
  }

  @Test
  public void testParseFromBytes_recursiveMessages() throws Exception {
    byte[] data99 = makeRecursiveMessage(99).toByteArray();
    final byte[] data100 = makeRecursiveMessage(100).toByteArray();

    RecursiveMessage.parseFrom(data99);
    Throwable thrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                RecursiveMessage.parseFrom(data100);
              }
            });
    assertThat(thrown).hasMessageThat().contains(NESTING);
  }

  @Test
  public void testParseFromBytes_recursiveKnownGroups() throws Exception {
    byte[] data99 = makeRecursiveGroup(99).toByteArray();
    final byte[] data100 = makeRecursiveGroup(100).toByteArray();

    RecursiveGroup.parseFrom(data99);
    Throwable thrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                RecursiveGroup.parseFrom(data100);
              }
            });
    assertThat(thrown).hasMessageThat().contains(NESTING);
  }

  @Test
  public void testMaliciousSGroupTagsWithMapField_fromByteArray() throws Exception {
    final ByteString byteString = generateNestingGroups(102);

    Throwable parseFromThrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                MapContainer.parseFrom(byteString.toByteArray());
              }
            });
    Throwable mergeFromThrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                MapContainer.newBuilder().mergeFrom(byteString.toByteArray());
              }
            });

    assertThat(parseFromThrown).hasMessageThat().contains(NESTING);
    assertThat(mergeFromThrown).hasMessageThat().contains(NESTING);
  }

  @Test
  public void testMaliciousSGroupTagsWithMapField_fromInputStream() throws Exception {
    final byte[] bytes = generateNestingGroups(101).toByteArray();

    Throwable parseFromThrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                MapContainer.parseFrom(new ByteArrayInputStream(bytes));
              }
            });
    Throwable mergeFromThrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                MapContainer.newBuilder().mergeFrom(new ByteArrayInputStream(bytes));
              }
            });

    assertThat(parseFromThrown).hasMessageThat().contains(NESTING);
    assertThat(mergeFromThrown).hasMessageThat().contains(NESTING);
  }

  private static byte[] mapEntryWithSkippedGroups(int groups) throws IOException {
    byte[] nested = generateNestingGroups(groups).toByteArray();
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CodedOutputStream output = CodedOutputStream.newInstance(bytes);
    output.writeByteArray(3, nested);
    output.flush();
    return bytes.toByteArray();
  }

  private static byte[] messageSetItemWithSkippedGroups(int groups) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    bytes.write(WireFormat.MESSAGE_SET_ITEM_TAG);
    generateNestingGroups(groups).writeTo(bytes);
    return bytes.toByteArray();
  }

  private static void assertMapEntryParse(int groups, boolean refused) throws IOException {
    final byte[] data = mapEntryWithSkippedGroups(groups);
    Throwable thrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                MapContainer.parseFrom(data);
              }
            });
    assertRefused(thrown, refused);
  }

  private static void assertMessageSetParse(int groups, boolean refused) throws IOException {
    final byte[] data = messageSetItemWithSkippedGroups(groups);
    Throwable thrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                TestMessageSetLite.parseFrom(data);
              }
            });
    assertRefused(thrown, refused);
  }

  private static void assertRefused(Throwable thrown, boolean refused) {
    if (refused) {
      assertThat(thrown).hasMessageThat().contains(NESTING);
    } else {
      assertThat(thrown).hasMessageThat().doesNotContain(NESTING);
    }
  }

  @Test
  public void testParseFromBytes_skipFieldInMapEntry_recursionLimit() throws Exception {
    assertMapEntryParse(99, false);
    assertMapEntryParse(100, true);
  }

  @Test
  public void testParseFromBytes_skipFieldInMessageSetItem_recursionLimit() throws Exception {
    assertMessageSetParse(99, false);
    assertMessageSetParse(100, true);
  }

  private static CodedInputStream balancedGroups(int groups) throws IOException {
    int start = WireFormat.makeTag(3, WireFormat.WIRETYPE_START_GROUP);
    int end = WireFormat.makeTag(3, WireFormat.WIRETYPE_END_GROUP);
    ByteString.Output bytes = ByteString.newOutput();
    CodedOutputStream output = CodedOutputStream.newInstance(bytes);
    for (int i = 0; i < groups; i++) {
      output.writeInt32NoTag(start);
    }
    for (int i = 0; i < groups; i++) {
      output.writeInt32NoTag(end);
    }
    output.flush();
    return bytes.toByteString().newCodedInput();
  }

  private static boolean mergeFirstField(CodedInputStream input) throws IOException {
    return UnknownFieldSetLite.newInstance().mergeFieldFrom(input.readTag(), input);
  }

  @Test
  public void testUnknownFieldSetLite_mergeFieldFrom_withinLimit() throws Exception {
    CodedInputStream input = balancedGroups(100);

    assertThat(mergeFirstField(input)).isTrue();
    assertThat(input.recursionDepth).isEqualTo(0);
  }

  private static ByteString toByteString(UnknownFieldSetLite unknownFields) throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    CodedOutputStream output = CodedOutputStream.newInstance(bytes);
    unknownFields.writeTo(output);
    output.flush();
    return ByteString.copyFrom(bytes.toByteArray());
  }

  @Test
  public void testRecursionLimitExceeded() throws Exception {
    UnknownFieldSetLite current = UnknownFieldSetLite.newInstance();
    current.storeField(WireFormat.makeTag(1, WireFormat.WIRETYPE_VARINT), 1L);

    for (int i = 0; i < 150; i++) {
      UnknownFieldSetLite parent = UnknownFieldSetLite.newInstance();
      parent.storeField(WireFormat.makeTag(1, WireFormat.WIRETYPE_START_GROUP), current);
      current = parent;
    }

    ByteString data = toByteString(current);
    final CodedInputStream input = CodedInputStream.newInstance(data.toByteArray());

    final UnknownFieldSetLite instance = UnknownFieldSetLite.newInstance();

    final int tag = input.readTag();
    InvalidProtocolBufferException e =
        assertThrows(
            InvalidProtocolBufferException.class,
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                instance.mergeFieldFrom(tag, input);
              }
            });
    assertThat(e).hasMessageThat().contains("too many levels of nesting");
  }

  @Test
  public void testParseFromInputStream_recursiveKnownGroups() throws Exception {
    byte[] data100 = makeRecursiveGroup(100).toByteArray();
    final byte[] data101 = makeRecursiveGroup(101).toByteArray();

    RecursiveGroup.parseFrom(new ByteArrayInputStream(data100));
    Throwable thrown =
        parseThrows(
            new ThrowingRunnable() {
              @Override
              public void run() throws Throwable {
                RecursiveGroup.parseFrom(new ByteArrayInputStream(data101));
              }
            });
    assertThat(thrown).hasMessageThat().contains(NESTING);
  }

  private static ByteString generateNestingGroups(int num) throws IOException {
    int groupTap = WireFormat.makeTag(3, WireFormat.WIRETYPE_START_GROUP);
    ByteString.Output byteStringOutput = ByteString.newOutput();
    CodedOutputStream codedOutput = CodedOutputStream.newInstance(byteStringOutput);
    for (int i = 0; i < num; i++) {
      codedOutput.writeInt32NoTag(groupTap);
    }
    codedOutput.flush();
    return byteStringOutput.toByteString();
  }

  private static RecursiveMessage makeRecursiveMessage(int num) {
    if (num == 0) {
      return RecursiveMessage.getDefaultInstance();
    } else {
      return RecursiveMessage.newBuilder().setRecurse(makeRecursiveMessage(num - 1)).build();
    }
  }

  // Protoc 3.19 has no delimited self-reference, so a level alternates group and message.
  private static RecursiveGroup makeRecursiveGroup(int levels) {
    if (levels == 0) {
      return RecursiveGroup.getDefaultInstance();
    } else if (levels == 1) {
      return RecursiveGroup.newBuilder()
          .setRecurse(RecursiveGroup.Recurse.getDefaultInstance())
          .build();
    } else {
      return RecursiveGroup.newBuilder()
          .setRecurse(RecursiveGroup.Recurse.newBuilder().setNext(makeRecursiveGroup(levels - 2)))
          .build();
    }
  }
}
