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

## Wire mainnet config placeholder
- Files: src/main/resources/blockchain.json
- What: Added a placeholder `groupInviteExpiryHeight` entry (set to 99999999) to the mainnet featureTriggers map.
- Why: Ensures mainnet config recognizes the new invite-expiry trigger while keeping activation far in the future until a rollout height is chosen.

## Wire testnet config
- Files: testnet/testchain.json
- What: Added `groupInviteExpiryHeight` with immediate activation (height 0) to the testnet featureTriggers map.
- Why: Ensures testnet nodes recognize and activate invite-expiry logic right away for testing and validation.

## Wire test fixtures
- Files: src/test/resources/test-chain-v2.json, src/test/resources/test-chain-v2-block-timestamps.json, src/test/resources/test-chain-v2-disable-reference.json, src/test/resources/test-chain-v2-founder-rewards.json, src/test/resources/test-chain-v2-leftover-reward.json, src/test/resources/test-chain-v2-minting.json, src/test/resources/test-chain-v2-penalty-fix.json, src/test/resources/test-chain-v2-qora-holder-extremes.json, src/test/resources/test-chain-v2-qora-holder-reduction.json, src/test/resources/test-chain-v2-qora-holder.json, src/test/resources/test-chain-v2-reward-levels.json, src/test/resources/test-chain-v2-reward-scaling.json, src/test/resources/test-chain-v2-reward-shares.json, src/test/resources/test-chain-v2-self-sponsorship-algo-v1.json, src/test/resources/test-chain-v2-self-sponsorship-algo-v2.json, src/test/resources/test-chain-v2-self-sponsorship-algo-v3.json
- What: Added `groupInviteExpiryHeight` with low activation (0) across all test-chain fixture configs.
- Why: Keeps all test fixtures aligned with startup validation and allows tests to exercise invite-expiry logic immediately.

## (No commit) Sanity-check trigger coverage
- Files: (inspection only; no file changes)
- What: Verified all chain configs with featureTriggers already include `groupInviteExpiryHeight`; no additional files needed updates.
- Why: Ensures startup validation won’t fail on overlooked configs before proceeding to code changes.

## Gate invite-first expiry by trigger
- Files: src/main/java/org/qortal/group/Group.java
- What: Introduced trigger-gated invite expiry handling in `Group.join(...)`, using next block height to decide when to apply invite expiry logic.
- Why: Ensures invite expiry enforcement is only evaluated after the `groupInviteExpiryHeight` activation point.

## Use join tx timestamp for expiry check
- Files: src/main/java/org/qortal/group/Group.java
- What: Added join-transaction-timestamp-based expiry evaluation, respecting TTL=0 as never expiring and an inclusive `<= expiry` boundary.
- Why: Ensures invite validity checks rely on deterministic transaction timestamps instead of local time before applying invite consumption.

## Treat expired invite as absent in join
- Files: src/main/java/org/qortal/group/Group.java
- What: When the invite is expired post-trigger, `Group.join` now treats it as missing, falling back to a join request for closed groups without deleting the stale invite.
- Why: Prevents expired invites from granting membership while preserving the stored invite for deterministic replay/orphan handling.

## Preserve pre-trigger join behavior
- Files: src/main/java/org/qortal/group/Group.java
- What: Clarified the trigger gate in `Group.join` to note legacy behavior remains unchanged before activation.
- Why: Documents that expiry enforcement is strictly post-trigger, reassuring reviewers about pre-trigger compatibility.
