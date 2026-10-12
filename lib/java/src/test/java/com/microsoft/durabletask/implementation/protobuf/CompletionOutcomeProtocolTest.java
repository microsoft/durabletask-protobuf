// Copyright (c) Microsoft Corporation.
// Licensed under the MIT License.

package com.microsoft.durabletask.implementation.protobuf;

import com.google.protobuf.DescriptorProtos.DescriptorProto;
import com.google.protobuf.DescriptorProtos.FileDescriptorProto;
import com.google.protobuf.Descriptors.FileDescriptor;
import com.google.protobuf.DynamicMessage;
import com.google.protobuf.Int32Value;
import com.google.protobuf.Int64Value;
import com.google.protobuf.StringValue;
import com.microsoft.durabletask.implementation.protobuf.BackendServiceOuterClass.CompleteInstanceWorkItemResponse;
import com.microsoft.durabletask.implementation.protobuf.BackendServiceOuterClass.CompleteOrchestrationWorkItemRequest;
import com.microsoft.durabletask.implementation.protobuf.BackendServiceOuterClass.CompleteOrchestrationWorkItemResponse;
import com.microsoft.durabletask.implementation.protobuf.BackendServiceOuterClass.OrchestrationCompletionFailure;
import com.microsoft.durabletask.implementation.protobuf.BackendServiceOuterClass.OrchestrationCompletionOutcome;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.OrchestrationStatus;
import com.microsoft.durabletask.implementation.protobuf.OrchestratorService.TaskFailureDetails;
import org.junit.Test;

import static org.junit.Assert.*;

public class CompletionOutcomeProtocolTest {
    @Test
    public void legacyAcknowledgementsHaveNoOutcome() throws Exception {
        CompleteOrchestrationWorkItemResponse unary =
                CompleteOrchestrationWorkItemResponse.parseFrom(new byte[0]);
        assertFalse(unary.hasOutcome());
        assertArrayEquals(new byte[0], unary.toByteArray());

        CompleteInstanceWorkItemResponse stream =
                CompleteInstanceWorkItemResponse.parseFrom(new byte[] {10, 0});
        assertEquals(CompleteInstanceWorkItemResponse.RequestCase.ORCHESTRATORRESPONSE,
                stream.getRequestCase());
        assertFalse(stream.getOrchestratorResponse().hasOutcome());
        assertArrayEquals(new byte[] {10, 0}, stream.toByteArray());
    }

    @Test
    public void unaryAndExtendedSessionCarryTheSameAcceptedOutcome() throws Exception {
        OrchestrationCompletionOutcome outcome = OrchestrationCompletionOutcome.newBuilder()
                .setInstanceId("instance")
                .setExecutionId(StringValue.of("execution"))
                .setFunctionName(StringValue.of("orchestrator"))
                .setCompletionIdentity("opaque-completion-identity")
                .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED)
                .setProviderFailure(sizeFailure("CompletionRequest"))
                .build();
        CompleteOrchestrationWorkItemResponse response =
                CompleteOrchestrationWorkItemResponse.newBuilder().setOutcome(outcome).build();

        CompleteOrchestrationWorkItemResponse unary =
                CompleteOrchestrationWorkItemResponse.parseFrom(response.toByteArray());
        CompleteInstanceWorkItemResponse stream = CompleteInstanceWorkItemResponse.parseFrom(
                CompleteInstanceWorkItemResponse.newBuilder()
                        .setOrchestratorResponse(response).build().toByteArray());

        assertTrue(unary.hasOutcome());
        assertEquals(outcome, unary.getOutcome());
        assertEquals(unary, stream.getOrchestratorResponse());
        assertEquals(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED,
                stream.getOrchestratorResponse().getOutcome().getRuntimeStatus());
        assertEquals("CompletionRequest",
                unary.getOutcome().getProviderFailure().getSizeLimitType().getValue());
    }

    @Test
    public void infrastructureFailureProvenanceIsExplicit() throws Exception {
        CompleteOrchestrationWorkItemRequest applicationFailure =
                CompleteOrchestrationWorkItemRequest.newBuilder()
                        .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED).build();
        assertFalse(CompleteOrchestrationWorkItemRequest.parseFrom(
                applicationFailure.toByteArray()).hasProviderFailure());

        CompleteOrchestrationWorkItemRequest providerFailure = applicationFailure.toBuilder()
                .setProviderFailure(sizeFailure("ExternalizedPayload")).build();
        assertEquals(providerFailure, CompleteOrchestrationWorkItemRequest.parseFrom(
                providerFailure.toByteArray()));
        assertTrue(providerFailure.hasProviderFailure());

        OrchestrationCompletionOutcome applicationOutcome =
                OrchestrationCompletionOutcome.newBuilder()
                        .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED).build();
        assertFalse(applicationOutcome.hasProviderFailure());

        OrchestrationCompletionFailure validationFailure =
                OrchestrationCompletionFailure.newBuilder()
                        .setReportingReason("BackendCompletionValidationFailed")
                        .setSizeLimitType(StringValue.of("BackendValidation")).build();
        assertFalse(validationFailure.hasSizeLimitBytes());
        assertFalse(validationFailure.hasRequestSizeBytes());
    }

    @Test
    public void optionalDiagnosticsDistinguishUnavailableFromZero() throws Exception {
        OrchestrationCompletionFailure absent = OrchestrationCompletionFailure.getDefaultInstance();
        assertFalse(absent.hasFailureDetails());
        assertFalse(absent.hasRequestSizeBytes());
        assertFalse(absent.hasSizeLimitBytes());
        assertFalse(absent.hasSizeLimitType());

        OrchestrationCompletionFailure zero = OrchestrationCompletionFailure.parseFrom(
                absent.toBuilder().setRequestSizeBytes(Int32Value.of(0))
                        .setSizeLimitBytes(Int64Value.of(0)).build().toByteArray());
        assertTrue(zero.hasRequestSizeBytes());
        assertEquals(0, zero.getRequestSizeBytes().getValue());
        assertTrue(zero.hasSizeLimitBytes());
        assertEquals(0L, zero.getSizeLimitBytes().getValue());

        OrchestrationCompletionFailure large = OrchestrationCompletionFailure.parseFrom(
                absent.toBuilder().setRequestSizeBytes(Int32Value.of(Integer.MAX_VALUE))
                        .setSizeLimitBytes(Int64Value.of(Long.MAX_VALUE)).build().toByteArray());
        assertEquals(Integer.MAX_VALUE, large.getRequestSizeBytes().getValue());
        assertEquals(Long.MAX_VALUE, large.getSizeLimitBytes().getValue());
    }

    @Test
    public void nonterminalAcceptedStatusStillHasAnOutcome() throws Exception {
        OrchestrationCompletionOutcome running = OrchestrationCompletionOutcome.newBuilder()
                .setInstanceId("instance")
                .setCompletionIdentity("opaque-completion-identity")
                .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_RUNNING).build();
        CompleteOrchestrationWorkItemResponse response =
                CompleteOrchestrationWorkItemResponse.parseFrom(
                        CompleteOrchestrationWorkItemResponse.newBuilder()
                                .setOutcome(running).build().toByteArray());
        assertTrue(response.hasOutcome());
        assertEquals(OrchestrationStatus.ORCHESTRATION_STATUS_RUNNING,
                response.getOutcome().getRuntimeStatus());
        assertFalse(response.getOutcome().hasProviderFailure());
        assertFalse(response.getOutcome().hasExecutionId());
        assertFalse(response.getOutcome().hasFunctionName());
    }

    @Test
    public void legacyReadersIgnoreAndPreserveAdditiveFields() throws Exception {
        FileDescriptor legacy = legacyBackendDescriptor();
        CompleteOrchestrationWorkItemRequest request =
                CompleteOrchestrationWorkItemRequest.newBuilder()
                        .setCompletionToken("test-token")
                        .setCompletionCount(2)
                        .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED)
                        .setProviderFailure(sizeFailure("CompletionRequest")).build();
        DynamicMessage legacyRequest = DynamicMessage.parseFrom(
                legacy.findMessageTypeByName("CompleteOrchestrationWorkItemRequest"),
                request.toByteArray());
        assertEquals("test-token",
                legacyRequest.getField(legacyRequest.getDescriptorForType().findFieldByNumber(1)));
        assertEquals(2,
                legacyRequest.getField(legacyRequest.getDescriptorForType().findFieldByNumber(13)));
        assertTrue(legacyRequest.getUnknownFields().hasField(14));
        assertEquals(request, CompleteOrchestrationWorkItemRequest.parseFrom(
                legacyRequest.toByteArray()));

        CompleteOrchestrationWorkItemResponse response =
                CompleteOrchestrationWorkItemResponse.newBuilder().setOutcome(
                        OrchestrationCompletionOutcome.newBuilder()
                                .setInstanceId("instance")
                                .setCompletionIdentity("opaque-completion-identity")
                                .setRuntimeStatus(OrchestrationStatus.ORCHESTRATION_STATUS_FAILED)
                                .setProviderFailure(sizeFailure("CompletionRequest"))).build();
        DynamicMessage legacyResponse = DynamicMessage.parseFrom(
                legacy.findMessageTypeByName("CompleteOrchestrationWorkItemResponse"),
                response.toByteArray());
        assertTrue(legacyResponse.getAllFields().isEmpty());
        assertTrue(legacyResponse.getUnknownFields().hasField(1));
        assertEquals(response, CompleteOrchestrationWorkItemResponse.parseFrom(
                legacyResponse.toByteArray()));

        CompleteInstanceWorkItemResponse stream = CompleteInstanceWorkItemResponse.newBuilder()
                .setOrchestratorResponse(response).build();
        DynamicMessage legacyStream = DynamicMessage.parseFrom(
                legacy.findMessageTypeByName("CompleteInstanceWorkItemResponse"),
                stream.toByteArray());
        DynamicMessage legacyAck = (DynamicMessage) legacyStream.getField(
                legacyStream.getDescriptorForType().findFieldByNumber(1));
        assertTrue(legacyAck.getAllFields().isEmpty());
        assertTrue(legacyAck.getUnknownFields().hasField(1));
        assertEquals(stream, CompleteInstanceWorkItemResponse.parseFrom(legacyStream.toByteArray()));
    }

    private static OrchestrationCompletionFailure sizeFailure(String limitType) {
        return OrchestrationCompletionFailure.newBuilder()
                .setFailureDetails(TaskFailureDetails.newBuilder()
                        .setErrorType("CompletionSizeLimitExceeded")
                        .setErrorMessage("The completion exceeds the configured size limit."))
                .setReportingReason("CompletionSizeLimitExceeded")
                .setRequestSizeBytes(Int32Value.of(2000000))
                .setSizeLimitBytes(Int64Value.of(1000000))
                .setSizeLimitType(StringValue.of(limitType)).build();
    }

    private static FileDescriptor legacyBackendDescriptor() throws Exception {
        FileDescriptor current = BackendServiceOuterClass.getDescriptor();
        FileDescriptorProto.Builder legacy = current.toProto().toBuilder().clearMessageType();
        for (DescriptorProto message : current.toProto().getMessageTypeList()) {
            if (message.getName().equals("OrchestrationCompletionFailure")
                    || message.getName().equals("OrchestrationCompletionOutcome")) {
                continue;
            }
            DescriptorProto.Builder oldMessage = message.toBuilder();
            if (message.getName().equals("CompleteOrchestrationWorkItemRequest")) {
                oldMessage.clearField();
                message.getFieldList().stream().filter(field -> field.getNumber() != 14)
                        .forEach(oldMessage::addField);
            } else if (message.getName().equals("CompleteOrchestrationWorkItemResponse")) {
                oldMessage.clearField();
            }
            legacy.addMessageType(oldMessage);
        }
        return FileDescriptor.buildFrom(legacy.build(),
                current.getDependencies().toArray(new FileDescriptor[0]));
    }
}
