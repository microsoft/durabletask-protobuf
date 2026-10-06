// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.durabletask.implementation.protobuf;

import com.google.protobuf.Descriptors.Descriptor;
import com.google.protobuf.Descriptors.FieldDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Message;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class StartNewOrchestrationActionTest {
    @Test
    public void tagsUseStringMapFieldEight() {
        FieldDescriptor tags = tagsField();
        assertEquals(8, tags.getNumber());
        assertTrue(tags.isMapField());
        assertNull(tags.getContainingOneof());
        Descriptor entry = tags.getMessageType();
        assertEquals("key", entry.findFieldByNumber(1).getName());
        assertEquals(FieldDescriptor.Type.STRING, entry.findFieldByNumber(1).getType());
        assertEquals("value", entry.findFieldByNumber(2).getName());
        assertEquals(FieldDescriptor.Type.STRING, entry.findFieldByNumber(2).getType());
    }

    @Test
    public void emptyTagsRetainTheUntaggedWireFormat() {
        FieldDescriptor tags = tagsField();
        OrchestratorService.StartNewOrchestrationAction action =
                OrchestratorService.StartNewOrchestrationAction.newBuilder()
                        .setInstanceId("id")
                        .setName("name")
                        .setField(tags, Collections.emptyList())
                        .build();
        assertArrayEquals(
                new byte[] {10, 2, 'i', 'd', 18, 4, 'n', 'a', 'm', 'e'},
                action.toByteArray());
        assertArrayEquals(
                new byte[0],
                OrchestratorService.StartNewOrchestrationAction.getDefaultInstance().toByteArray());
    }

    @Test
    public void tagsRoundTripThroughTheEntityOperationEnvelope() throws Exception {
        FieldDescriptor tags = tagsField();
        Descriptor entry = tags.getMessageType();
        OrchestratorService.StartNewOrchestrationAction action =
                OrchestratorService.StartNewOrchestrationAction.newBuilder()
                        .setInstanceId("id")
                        .setName("name")
                        .addRepeatedField(tags, tagEntry(entry, "tenant", "example"))
                        .addRepeatedField(tags, tagEntry(entry, "empty", ""))
                        .build();
        OrchestratorService.EntityBatchResult batch =
                OrchestratorService.EntityBatchResult.newBuilder()
                        .addActions(OrchestratorService.OperationAction.newBuilder()
                                .setId(42)
                                .setStartNewOrchestration(action))
                        .build();
        OrchestratorService.EntityBatchResult parsed =
                OrchestratorService.EntityBatchResult.parseFrom(batch.toByteArray());
        assertEquals(batch, parsed);
        assertEquals(42, parsed.getActions(0).getId());
        assertTrue(parsed.getActions(0).hasStartNewOrchestration());
        Map<String, String> actual = new HashMap<>();
        List<?> entries = (List<?>) parsed.getActions(0).getStartNewOrchestration().getField(tags);
        for (Object value : entries) {
            Message tag = (Message) value;
            actual.put(
                    (String) tag.getField(entry.findFieldByNumber(1)),
                    (String) tag.getField(entry.findFieldByNumber(2)));
        }
        Map<String, String> expected = new HashMap<>();
        expected.put("tenant", "example");
        expected.put("empty", "");
        assertEquals(expected, actual);
    }

    private static FieldDescriptor tagsField() {
        FieldDescriptor tags =
                OrchestratorService.StartNewOrchestrationAction.getDescriptor().findFieldByName("tags");
        assertNotNull("StartNewOrchestrationAction must carry tags", tags);
        return tags;
    }

    private static Message tagEntry(Descriptor entry, String key, String value) {
        return DynamicMessage.newBuilder(entry)
                .setField(entry.findFieldByNumber(1), key)
                .setField(entry.findFieldByNumber(2), value)
                .build();
    }
}
