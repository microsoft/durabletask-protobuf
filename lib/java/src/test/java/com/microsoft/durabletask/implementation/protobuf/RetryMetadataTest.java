// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.durabletask.implementation.protobuf;

import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.DescriptorValidationException;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.ActivityRequest;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.RetryMetadata;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.TaskScheduledEvent;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RetryMetadataTest {
    @Test
    public void wireContractUsesAgreedFieldNumbersAndTypes() {
        Descriptor metadata = RetryMetadata.getDescriptor();
        assertEquals(3, metadata.getFields().size());
        assertField(metadata, "attemptNumber", 1, FieldDescriptor.Type.INT32);
        assertField(metadata, "maxAttempts", 2, FieldDescriptor.Type.INT32);
        assertField(metadata, "retryGroupId", 3, FieldDescriptor.Type.STRING);
        assertMetadataField(TaskScheduledEvent.getDescriptor(), 6);
        assertMetadataField(ActivityRequest.getDescriptor(), 8);
    }

    @Test
    public void absentMetadataIsNotSerialized() throws Exception {
        for (Message message : messages(null)) {
            Message parsed = message.getParserForType().parseFrom(message.toByteString());
            assertFalse(parsed.hasField(parsed.getDescriptorForType().findFieldByName("retryMetadata")));
            assertTrue(parsed.toByteString().isEmpty());
        }
    }

    @Test
    public void emptyMetadataRemainsPresentWithoutSynthesizedValues() throws Exception {
        for (Message message : messages(RetryMetadata.getDefaultInstance())) {
            Message parsed = message.getParserForType().parseFrom(message.toByteString());
            FieldDescriptor field = parsed.getDescriptorForType().findFieldByName("retryMetadata");
            assertTrue(parsed.hasField(field));
            assertEquals(RetryMetadata.getDefaultInstance(), parsed.getField(field));
            assertFalse(parsed.toByteString().isEmpty());
        }
    }

    @Test
    public void metadataRoundTripsIncludingInt32Maximum() throws Exception {
        for (int attempt : new int[] {1, Integer.MAX_VALUE}) {
            RetryMetadata metadata = RetryMetadata.newBuilder()
                    .setAttemptNumber(attempt)
                    .setMaxAttempts(Integer.MAX_VALUE)
                    .setRetryGroupId("logical-activity-call")
                    .build();
            for (Message message : messages(metadata)) {
                assertEquals(message, message.getParserForType().parseFrom(message.toByteString()));
            }
        }
    }

    @Test
    public void legacyMessagesDeserializeWithoutMetadata() throws Exception {
        for (Message prototype : messages(null)) {
            Descriptor legacy = withoutRetryMetadata(prototype.getDescriptorForType());
            DynamicMessage oldMessage = DynamicMessage.newBuilder(legacy)
                    .setField(legacy.findFieldByName("name"), "activity")
                    .build();
            Message parsed = prototype.getParserForType().parseFrom(oldMessage.toByteString());
            Descriptor descriptor = parsed.getDescriptorForType();
            assertEquals("activity", parsed.getField(descriptor.findFieldByName("name")));
            assertFalse(parsed.hasField(descriptor.findFieldByName("retryMetadata")));
            assertEquals(oldMessage.toByteString(), parsed.toByteString());
        }
    }

    @Test
    public void legacyReadersPreserveMetadataAsAnUnknownField() throws Exception {
        RetryMetadata metadata = RetryMetadata.newBuilder()
                .setAttemptNumber(2)
                .setMaxAttempts(3)
                .setRetryGroupId("logical-activity-call")
                .build();
        for (Message message : messages(metadata)) {
            Descriptor descriptor = message.getDescriptorForType();
            FieldDescriptor field = descriptor.findFieldByName("retryMetadata");
            DynamicMessage parsed = DynamicMessage.parseFrom(
                    withoutRetryMetadata(descriptor), message.toByteString());
            assertTrue(parsed.getUnknownFields().hasField(field.getNumber()));
            assertEquals(message, message.getParserForType().parseFrom(parsed.toByteString()));
        }
    }

    private static Message[] messages(RetryMetadata metadata) {
        ActivityRequest.Builder request = ActivityRequest.newBuilder();
        TaskScheduledEvent.Builder scheduled = TaskScheduledEvent.newBuilder();
        if (metadata != null) {
            request.setRetryMetadata(metadata);
            scheduled.setRetryMetadata(metadata);
        }
        return new Message[] {request.build(), scheduled.build()};
    }

    private static void assertField(
            Descriptor descriptor, String name, int number, FieldDescriptor.Type type) {
        FieldDescriptor field = descriptor.findFieldByName(name);
        assertEquals(number, field.getNumber());
        assertEquals(type, field.getType());
        assertFalse(field.isRepeated());
    }

    private static void assertMetadataField(Descriptor descriptor, int number) {
        assertField(descriptor, "retryMetadata", number, FieldDescriptor.Type.MESSAGE);
        assertEquals(RetryMetadata.getDescriptor(),
                descriptor.findFieldByName("retryMetadata").getMessageType());
    }

    private static Descriptor withoutRetryMetadata(Descriptor descriptor)
            throws DescriptorValidationException {
        FileDescriptor file = descriptor.getFile();
        FileDescriptorProto.Builder legacy = file.toProto().toBuilder();
        legacy.getMessageTypeBuilder(file.getMessageTypes().indexOf(descriptor))
                .removeField(descriptor.findFieldByName("retryMetadata").getIndex());
        return FileDescriptor.buildFrom(
                legacy.build(), file.getDependencies().toArray(new FileDescriptor[0]))
                .findMessageTypeByName(descriptor.getName());
    }
}
