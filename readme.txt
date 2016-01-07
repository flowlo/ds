# dslab2

All stages stated in the Lab Description were implemented. Implementation details were discussed as a team and afterwards reflected exhaustively.

## Stage 1

The nameservers were easy to implement, there wasn’t much room for decisions. The two Interfaces INameserver and INameserverForChatserver caused some confusion, because INameserver implements NameserverForChatserver.

## Stage 2

We decided to reimplement transformation streams (something similar to Cipher(In|Out)putStream), as it is by far the most elegant and easiest way to provide encrypted streamed communication.


## Stage 3

We added a small utility to perform HMAC-related operations, including transforming a message into a HMAC-annotated one.

## Synchronization

We previously had one listener thread which would handle all incoming DTOs. As the client should be able to look up other users before sending a private message, we faced a challenge in order to synchronize our centralized listening mechanism with the execution of commands. This was solved by providing locked memory, that would be synchronized in order to allow for commands to block upon new input handed over by the receiving thread.
