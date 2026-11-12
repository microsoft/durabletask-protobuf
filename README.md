# Durable Task Protobuf Definitions

This repo contains [protocol buffer](https://developers.google.com/protocol-buffers) (protobuf) definitions
used by the Durable Task framework sidecar architecture. It's recommended that Durable Task language SDKs reference
the protobuf contracts in this repo via [Git submodules](https://git-scm.com/book/en/v2/Git-Tools-Submodules).

## Activity retry metadata

`TaskScheduledEvent.retryMetadata` (field 6) and `ActivityRequest.retryMetadata` (field 8)
share the optional `RetryMetadata` message. The orchestration framework calculates the
metadata; services persist it with the scheduled event and deliver it unchanged to the
activity worker. Completion and failure events continue to correlate through
`taskScheduledId` and do not carry retry metadata.

When metadata is present, `attemptNumber` and `maxAttempts` must be positive `int32`
values, with `attemptNumber <= maxAttempts`. Attempts are one-based, and `maxAttempts`
includes the initial attempt. `retryGroupId` must be nonempty and remain stable for the
same logical activity call across attempts and replay. Consumers must validate these
invariants and report invalid metadata as an error rather than fill in defaults.

Message presence is significant: calls without a retry policy, older history, and
components that do not supply retry metadata omit the message. Absence does not imply
an attempt count, maximum, or group identifier; an empty message is present but invalid.
The new fields are additive, so older protobuf readers can ignore them.

## Contributing

This project welcomes contributions and suggestions.  Most contributions require you to agree to a
Contributor License Agreement (CLA) declaring that you have the right to, and actually do, grant us
the rights to use your contribution. For details, visit https://cla.opensource.microsoft.com.

When you submit a pull request, a CLA bot will automatically determine whether you need to provide
a CLA and decorate the PR appropriately (e.g., status check, comment). Simply follow the instructions
provided by the bot. You will only need to do this once across all repos using our CLA.

This project has adopted the [Microsoft Open Source Code of Conduct](https://opensource.microsoft.com/codeofconduct/).
For more information see the [Code of Conduct FAQ](https://opensource.microsoft.com/codeofconduct/faq/) or
contact [opencode@microsoft.com](mailto:opencode@microsoft.com) with any additional questions or comments.

## Trademarks

This project may contain trademarks or logos for projects, products, or services. Authorized use of Microsoft
trademarks or logos is subject to and must follow
[Microsoft's Trademark & Brand Guidelines](https://www.microsoft.com/legal/intellectualproperty/trademarks/usage/general).
Use of Microsoft trademarks or logos in modified versions of this project must not cause confusion or imply Microsoft sponsorship.
Any use of third-party trademarks or logos are subject to those third-party's policies.
