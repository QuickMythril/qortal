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

## Honor TTL=0 and inclusive boundary
- Files: src/main/java/org/qortal/group/Group.java
- What: Confirmed invite expiry logic treats `expiry == null` as non-expiring and uses an inclusive `timestamp <= expiry` boundary with an explanatory comment.
- Why: Ensures invite validation semantics are explicit and deterministic, matching the intended TTL sentinel and boundary behavior.

## Document join-first time basis
- Files: src/main/java/org/qortal/group/Group.java
- What: Added a code comment clarifying that TTL/expiry is intentionally ignored when an invite approves a pending join request (join-first path), pre- and post-trigger.
- Why: Documents the deliberate legacy-preserving behavior so reviewers know this path remains TTL-agnostic.

## Auto-approve pending request
- Files: src/main/java/org/qortal/group/Group.java
- What: Clarified in code that a matching invite auto-approves and consumes a pending join request in the join-first path, maintaining legacy behavior.
- Why: Documents the intended behavior for reviewers and affirms no trigger/TTL gating is applied when approving stored requests.

## Honor TTL=0 sentinel (join-first)
- Files: src/main/java/org/qortal/group/Group.java
- What: Noted that TTL=0 invites remain valid in the join-first auto-approval path.
- Why: Documents that the non-expiring sentinel applies consistently even when approving stored join requests.

## Filter invites-by-invitee API
- Files: src/main/java/org/qortal/api/resource/GroupsResource.java
- What: Added chain-tip-based filtering for `/groups/invites/{address}` invites, treating `expiry == null` as non-expiring, using inclusive `expiry >= tip`, and skipping filtering when no chain tip is present; introduced a helper to reuse for other invite listings.
- Why: Hides expired invites from the API without relying on local time and prepares for consistent invite filtering across endpoints.

## Filter invites-by-group API
- Files: src/main/java/org/qortal/api/resource/GroupsResource.java
- What: Applied the same chain-tip-based invite filtering to `/groups/invites/group/{groupid}` via the shared helper, respecting `expiry == null`, inclusive boundary, and no-tip passthrough.
- Why: Ensures group-level invite listings also hide expired entries without depending on local time.

## Document unconditional filtering
- Files: src/main/java/org/qortal/api/resource/GroupsResource.java
- What: Updated swagger summaries for invite endpoints to explicitly state chain-tip-based filtering (inclusive boundary, `expiry == null` sentinel) and left filtering unconditional (no feature trigger) with no local-clock fallback when tip is missing.
- Why: Makes the filtering behavior clear to API consumers and documents the divergence from consensus time basis.

## Test invite-first expiry enforcement
- Files: src/test/java/org/qortal/test/group/MiscTests.java
- What: Added invite-first tests covering valid-before-expiry membership and expired-invite fallback to join request (invite retained), with helper builders for timestamped joins and TTL invites.
- Why: Verifies post-trigger invite expiry enforcement matches intended behavior in invite-first flow.

## Test join-first behavior
- Files: src/test/java/org/qortal/test/group/MiscTests.java
- What: Added join-first tests confirming invites auto-approve pending requests regardless of TTL/age (including TTL=0) and that backdated requests still auto-add when an invite arrives later.
- Why: Documents and validates the TTL-agnostic join-first path and back/forward-dated request approval behavior.
