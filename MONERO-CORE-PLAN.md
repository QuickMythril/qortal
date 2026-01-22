# Monero (XMR) support in Qortal Core - wallet first, trades later

This document is scoped to Qortal Core only. It describes what we need to add or change in this repo to deliver:

Stage 1: Wallet only (receive + send)
Stage 2: Trade support (future, not implemented in Stage 1)

Assumptions
- Hub sends `xmrEntropyHex` (32-byte entropy, hex) to Core. Core derives Monero keys/address from entropy.
- We are using Monero wallet2_api via JNI (no monero-wallet-rpc).
- Stage 1 defaults to full-spend wallets (entropy => spend key derivable). Watch-only can be added later.
- Stage 1 uses Core's configured daemon list only (no per-request daemon override, no proxy yet).
- Mainnet only in Stage 1.

--------------------------------------------------------------------------------
Stage 1: Wallet only (receive + send)
--------------------------------------------------------------------------------

1) New JNI binding + native library loader

Add a new JNI bridge in Core, similar to com.rust.litewalletjni.LiteWalletJni:
- New Java class, e.g. src/main/java/com/monero/wallet2jni/MoneroWalletJni.java
  - Native methods for:
    - derive keys/address from entropy (hex)
    - wallet creation/open/close from derived keys
    - refresh, balance, transfers, error/status
  - A loadLibrary() helper that locates the platform-specific JNI library.
  - A isLoaded() check for safe runtime gating.

Native library discovery
- Follow the PirateChain pattern but use a Monero-specific path:
  - Base: Settings.getInstance().getWalletsPath()
  - Proposed: wallets/Monero/lib/<os-arch>/libmonerowallet2jni.(so|dll|dylib)
- Provide helper methods on a new controller (see section 2) similar to:
  - getMoneroLibOuterDirectory()
  - getMoneroLibFilename() (OS/arch switch)
  - getMoneroWalletsDirectory()

Notes
- The JNI library will be produced elsewhere, but Core must define:
  - The expected library name
  - Where to place it
  - The load order (System.load)
- Do not log secrets (keys, passwords, mnemonics).

2) Monero wallet controller (runtime lifecycle + sync)

Add a long-lived controller thread, similar to PirateChainWalletController:
- New class: src/main/java/org/qortal/controller/MoneroWalletController.java
- Responsibilities:
  - Load JNI library (if not loaded).
  - Manage wallet instances (single active wallet or map by walletId).
  - Drive sync/refresh loop with backoff.
  - Persist wallet files as needed (store on interval or after operations).
  - Provide sync status for API.
  - Auto-init on first wallet operation; start refresh immediately.

Recommended controller API
- initWalletFromEntropy(entropyHex, restoreHeight)
- ensureInitialized(entropyHex)
- ensureSynchronized(entropyHex)
- getWallet(entropyHex)
- close(entropyHex, store)
- shutdown()

Threading model
- Wallet2 APIs are not guaranteed to be thread-safe across calls.
- Use a per-wallet lock (synchronized or ReentrantLock) to serialize JNI calls.
- The refresh loop should be cancellable on shutdown.

3) Monero wallet wrapper

Add a Java wrapper that maps to wallet2_api calls, similar to PirateWallet:
- New class: src/main/java/org/qortal/crosschain/MoneroWallet.java
- Owns:
  - walletId = SHA256(entropyHex) (used as wallet identifier)
  - derived address + keys (do not log)
  - wallet path and password
  - daemon config
  - refresh state

Core methods needed for Stage 1
- initFromEntropy(entropyHex, restoreHeight)
- startRefresh() / refresh() / refreshAsync()
- balance() / unlockedBalance()
- address()
- transactions() (at least basic history for UI)
- send(toAddress, amountAtomic, priority, paymentId, sweep?)
- status() (wallet height, daemon height, isSynchronized, lastError)
- store() / close()

Wallet files and passwords
- Monero wallet files are already encrypted by wallet2.
- Deterministic password derived from entropy (no extra secret storage):
  - walletPassword = SHA256("QORTAL_XMR_WALLET_PASSWORD_v1" + xmrEntropyHex)
- Store wallet files under:
  - wallets/Monero/<walletId>.wallet
  - wallets/Monero/<walletId>.keys
- Use base path without extension when calling wallet2 create/open APIs so both files are created consistently.
- Store minimal metadata in memory; avoid writing secrets to logs.

Watch-only limitations
- View-only wallets cannot see outgoing transactions unless key images are imported.
- Stage 1 uses full-spend wallets, but keep this note for future watch-only support.

4) Monero network config + settings

Add new settings to src/main/java/org/qortal/settings/Settings.java and settings.json:
- moneroDefaultRestoreHeight (int)
- moneroDaemonList (list of daemon URLs, e.g. https://host:port or http://host:port)
- moneroWalletsPath (optional override; otherwise reuse walletsPath)

5) ForeignBlockchain integration (wallet-only)

Add a minimal Monero implementation of ForeignBlockchain:
- New class: src/main/java/org/qortal/crosschain/Monero.java
  - getCurrencyCode() -> "XMR"
  - isValidAddress(address) -> call wallet2_api::addressValid via JNI
  - isValidWalletKey(key) -> call wallet2_api::keyValid via JNI (view/spend)
  - getMinimumOrderAmount() -> return 0 for wallet-only stage

Note:
- Do NOT add Monero to SupportedBlockchain in Stage 1 if trade support is not ready.
- Keep the wallet-only APIs decoupled from trade bot code.

6) API endpoints (wallet-only) — align with Hub contract

Add a new API resource similar to CrossChainPirateChainResource:
- New class: src/main/java/org/qortal/api/resource/CrossChainMoneroResource.java
- Base path: /crosschain/xmr
- Security: require API key for wallet operations.
- Gateway: block XMR wallet endpoints when running in gateway mode (match ARRR behavior).

Required endpoints (Hub already expects these)
- POST /crosschain/xmr/walletaddress
  - Body: raw `xmrEntropyHex` string (Hub posts as application/json; Core should read raw body as string)
  - Response: primary address (string)
- POST /crosschain/xmr/walletbalance
  - Body: raw `xmrEntropyHex` string (Hub posts as application/json; Core should read raw body as string)
  - Response: balance in atomic units (string or number)
- POST /crosschain/xmr/wallettransactions
  - Body: raw `xmrEntropyHex` string (Hub posts as application/json; Core should read raw body as string)
  - Optional query params: limit (default 100), offset (default 0), reverse (default true)
  - Response: list of transactions (txid/hash, timestamp, blockHeight, confirmations, isPending, direction, totalAmount, feeAmount)
- POST /crosschain/xmr/send
  - Body (JSON): { entropyHex, receivingAddress, xmrAmount }
    - Accept decimal XMR as string; numeric values are treated as strings.
    - Convert to atomic units using BigDecimal: atomic = decimal * 1e12 (exact).
    - Reject if more than 12 decimal places (no rounding).
    - Optional `memo` field is accepted (ignored in Stage 1).
  - Response: txid(s)
- POST /crosschain/xmr/syncstatus
  - Body: raw `xmrEntropyHex` string (Hub posts as application/json; Core should read raw body as string)
  - Response: string status (e.g. "Synchronized", "Not initialized yet", "Initializing wallet...")

Optional (Core-internal) endpoint for future use
- POST /crosschain/xmr/walletclose
  - Body: `xmrEntropyHex`
  - Response: success

Add request/response models in src/main/java/org/qortal/api/model/crosschain:
- MoneroWalletStatus
- MoneroWalletBalance
- MoneroWalletTransaction
- MoneroSendRequest

Reuse existing API errors where possible:
- INVALID_ADDRESS
- INVALID_PRIVATE_KEY
- FOREIGN_BLOCKCHAIN_NETWORK_ISSUE
- FOREIGN_BLOCKCHAIN_BALANCE_ISSUE

7) Controller lifecycle hooks

Update Controller to start/stop Monero wallet controller:
- src/main/java/org/qortal/controller/Controller.java
  - Start: MoneroWalletController.getInstance().start()
  - Shutdown: MoneroWalletController.getInstance().shutdown()

8) Logging, telemetry, and error handling

Rules
- Never log view keys, spend keys, passwords, or full addresses.
- Redact daemon URLs that include credentials.
- JNI errors should map to API errors with safe messages.

Error pathways
- JNI load failure: return "wallet library not loaded" with actionable hint.
- Daemon unreachable: FOREIGN_BLOCKCHAIN_NETWORK_ISSUE.
- Watch-only send attempt: INVALID_CRITERIA.
- Invalid view/spend key for address: INVALID_PRIVATE_KEY.

9) Tests (Core-only)

Unit tests (no daemon required)
- Address validation: known valid/invalid addresses for mainnet.
- Entropy -> address determinism: same entropy always yields same address.
- Key validation: view key + address pairs.
- Password derivation: deterministic and stable.

Integration tests (optional)
- Use a public daemon to validate refresh/balance on a known wallet.
- Keep these disabled by default or under a profile.

--------------------------------------------------------------------------------
Stage 2: Trade support (future)
--------------------------------------------------------------------------------

Trade support is not part of Stage 1. When ready, the Core changes will likely include:

1) New trade protocol and data model
- Monero does not support the existing HTLC/P2SH approach used by Bitcoiny coins.
- We need a Monero-specific atomic swap protocol (e.g. scriptless/adaptor-based) and a new ACCT implementation.
- This will require new classes in:
  - org.qortal.crosschain (new ACCT implementation)
  - org.qortal.controller.tradebot (new trade bot)
  - org.qortal.api.resource (trade endpoints)

2) SupportedBlockchain integration
- Add SupportedBlockchain.MONERO with getInstance() and getLatestAcct().
- Add Monero trade data parsing for AT state data.

3) API changes for trade bot
- Extend CrossChainTradeBotResource to accept non-Bitcoiny blockchains.
- Add Monero-specific trade endpoints as needed.

4) Core DB changes (if needed)
- TradeBotData may require additional fields (e.g. key images, swap state).

5) Additional daemon requirements
- Swap protocols may require more daemon calls, proofs, or specialized services.

--------------------------------------------------------------------------------
Suggested file list (Stage 1)
--------------------------------------------------------------------------------

New files
- src/main/java/com/monero/wallet2jni/MoneroWalletJni.java
- src/main/java/org/qortal/controller/MoneroWalletController.java
- src/main/java/org/qortal/crosschain/Monero.java
- src/main/java/org/qortal/crosschain/MoneroWallet.java
- src/main/java/org/qortal/api/resource/CrossChainMoneroResource.java
- src/main/java/org/qortal/api/model/crosschain/MoneroWalletStatus.java
- src/main/java/org/qortal/api/model/crosschain/MoneroWalletBalance.java
- src/main/java/org/qortal/api/model/crosschain/MoneroWalletTransaction.java
- src/main/java/org/qortal/api/model/crosschain/MoneroSendRequest.java

Updated files
- src/main/java/org/qortal/settings/Settings.java
- src/main/java/org/qortal/controller/Controller.java
- src/main/resources/i18n/ApiError_*.properties (only if new error codes are added)

--------------------------------------------------------------------------------
Stage 1 decisions (confirmed)
--------------------------------------------------------------------------------

- Send input: accept decimal XMR string; numeric values treated as strings; reject >12 decimals; convert to atomic via BigDecimal * 1e12.
- Wallet password: SHA256("QORTAL_XMR_WALLET_PASSWORD_v1" + xmrEntropyHex).
- Wallet ID/filename: SHA256(xmrEntropyHex) as 64-hex; store under wallets/Monero/<walletId>.
- Daemon selection: use Core's configured daemon list only; no per-request daemon override; no proxy in Stage 1.
- Sync behavior: auto-init on first wallet operation; start refresh immediately; report syncing until ready.
- /syncstatus response: return a string (Hub expects "Synchronized"); add a structured endpoint later if needed.
- Transactions response: minimal-plus fields; optional limit/offset; default reverse=true; amounts in atomic units.
- Gateway: block XMR wallet calls on gateways like ARRR.
- Network: mainnet only in Stage 1.

--------------------------------------------------------------------------------
Exit criteria for Stage 1
--------------------------------------------------------------------------------

- JNI library loads on all supported OS/arch.
- Wallet can sync to a daemon and report balance/height.
- Full wallet (with spend key) can send a transaction and return txid(s).
- API endpoints return stable, consistent JSON and do not leak secrets.
- Controller start/stop cleanly without hangs.
