# Commits Log

This document tracks each commit made for the invite-expiration work. For every TODO sub-item we complete, add a new entry before committing:

- Use the sub-item title as the commit message.
- List the files changed.
- Describe what the changes do.
- Explain why the changes are needed.

Template for entries:

```
## <commit message / TODO sub-item title>
- Files: <comma-separated paths>
- What: <concise description of the changes>
- Why: <reasoning/intent>
```

## Add feature trigger enum entry
- Files: src/main/java/org/qortal/block/BlockChain.java
- What: Added `groupInviteExpiryHeight` to the `FeatureTrigger` enum and exposed a getter to read it from chain configs.
- Why: This prepares the blockchain config/validation to recognize the new invite-expiration trigger and makes it accessible to invite-handling code.

## Expose feature trigger getter
- Files: src/main/java/org/qortal/block/BlockChain.java
- What: Documented the `getGroupInviteExpiryHeight` convenience method with the intended activation scope (invite-first expiry enforcement).
- Why: Clarifies the purpose of the getter so future invite-expiry logic and reviews have an explicit reference to its role.
