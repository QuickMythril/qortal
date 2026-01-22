package org.qortal.api.resource;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.qortal.api.ApiError;
import org.qortal.api.ApiErrors;
import org.qortal.api.ApiExceptionFactory;
import org.qortal.api.Security;
import org.qortal.api.model.crosschain.MoneroSendRequest;
import org.qortal.api.model.crosschain.MoneroWalletBalance;
import org.qortal.api.model.crosschain.MoneroWalletTransaction;
import org.qortal.controller.MoneroWalletController;
import com.monero.wallet2jni.MoneroWalletJni;
import org.qortal.crosschain.ForeignBlockchainException;
import org.qortal.crosschain.Monero;
import org.qortal.settings.Settings;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Path("/crosschain/xmr")
@Tag(name = "Cross-Chain (Monero)")
public class CrossChainMoneroResource {

    private static final int MAX_DECIMALS = 12;

    @Context
    HttpServletRequest request;

    private void checkGateway() {
        if (Settings.getInstance().isGatewayEnabled()) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }
    }

    private String requireEntropyHex(String body) {
        String normalized = MoneroWalletController.normalizeEntropyHex(body);
        if (normalized == null) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_PRIVATE_KEY);
        }
        return normalized;
    }

    @POST
    @Path("/walletaddress")
    @Operation(
            summary = "Returns Monero wallet address",
            description = "Supply 32 bytes of entropy, hex encoded",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            schema = @Schema(
                                    type = "string",
                                    description = "32 bytes of entropy, hex encoded",
                                    example = "aabbcc..."
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string"))
                    )
            }
    )
    @ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
    @SecurityRequirement(name = "apiKey")
    public String getMoneroWalletAddress(@HeaderParam(Security.API_KEY_HEADER) String apiKey, String entropyHex) {
        Security.checkApiCallAllowed(request);
        checkGateway();

        String normalized = requireEntropyHex(entropyHex);

        try {
            return MoneroWalletController.getInstance().getWalletAddress(normalized);
        } catch (ForeignBlockchainException e) {
            throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE, e.getMessage());
        }
    }

    @POST
    @Path("/walletbalance")
    @Operation(
            summary = "Returns Monero balance in atomic units",
            description = "Supply 32 bytes of entropy, hex encoded",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            schema = @Schema(
                                    type = "string",
                                    description = "32 bytes of entropy, hex encoded",
                                    example = "aabbcc..."
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string", description = "balance (atomic units)"))
                    )
            }
    )
    @ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
    @SecurityRequirement(name = "apiKey")
    public String getMoneroWalletBalance(@HeaderParam(Security.API_KEY_HEADER) String apiKey, String entropyHex) {
        Security.checkApiCallAllowed(request);
        checkGateway();

        String normalized = requireEntropyHex(entropyHex);

        try {
            MoneroWalletBalance balance = MoneroWalletController.getInstance().getWalletBalance(normalized);
            return Long.toString(balance.balance);
        } catch (ForeignBlockchainException e) {
            throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE, e.getMessage());
        }
    }

    @POST
    @Path("/wallettransactions")
    @Operation(
            summary = "Returns Monero wallet transactions",
            description = "Supply 32 bytes of entropy, hex encoded",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            schema = @Schema(
                                    type = "string",
                                    description = "32 bytes of entropy, hex encoded",
                                    example = "aabbcc..."
                            )
                    )
            ),
            responses = {
                    @ApiResponse(
                            content = @Content(array = @ArraySchema(schema = @Schema(implementation = MoneroWalletTransaction.class)))
                    )
            }
    )
    @ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
    @SecurityRequirement(name = "apiKey")
    public List<MoneroWalletTransaction> getMoneroWalletTransactions(
            @HeaderParam(Security.API_KEY_HEADER) String apiKey,
            @QueryParam("limit") Integer limit,
            @QueryParam("offset") Integer offset,
            @QueryParam("reverse") Boolean reverse,
            String entropyHex
    ) {
        Security.checkApiCallAllowed(request);
        checkGateway();

        String normalized = requireEntropyHex(entropyHex);

        int effectiveLimit = limit != null && limit > 0 ? limit : 100;
        int effectiveOffset = offset != null && offset >= 0 ? offset : 0;
        boolean effectiveReverse = reverse == null || reverse;

        try {
            return MoneroWalletController.getInstance().getWalletTransactions(normalized, effectiveLimit, effectiveOffset, effectiveReverse);
        } catch (ForeignBlockchainException e) {
            throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE, e.getMessage());
        }
    }

    @POST
    @Path("/send")
    @Operation(
            summary = "Sends XMR from wallet",
            description = "Supply 32 bytes of entropy, hex encoded",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            schema = @Schema(implementation = MoneroSendRequest.class)
                    )
            ),
            responses = {
                    @ApiResponse(content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string", description = "transaction hash")))
            }
    )
    @ApiErrors({ApiError.INVALID_PRIVATE_KEY, ApiError.INVALID_CRITERIA, ApiError.INVALID_ADDRESS, ApiError.FOREIGN_BLOCKCHAIN_BALANCE_ISSUE, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE})
    @SecurityRequirement(name = "apiKey")
    public String sendMonero(@HeaderParam(Security.API_KEY_HEADER) String apiKey, MoneroSendRequest sendRequest) {
        Security.checkApiCallAllowed(request);
        checkGateway();

        if (sendRequest == null) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }

        String entropyHex = requireEntropyHex(sendRequest.entropyHex);

        if (sendRequest.receivingAddress == null || sendRequest.receivingAddress.isBlank()) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);
        }

        long amountAtomic = parseAtomicAmount(sendRequest.xmrAmount);

        boolean isValidAddress = Monero.getInstance().isValidAddress(sendRequest.receivingAddress);
        if (!MoneroWalletJni.isLoaded()) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE);
        }
        if (!isValidAddress) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_ADDRESS);
        }
        String memo = sendRequest.memo;

        try {
            return MoneroWalletController.getInstance().send(entropyHex, sendRequest.receivingAddress, amountAtomic, memo);
        } catch (ForeignBlockchainException e) {
            throw ApiExceptionFactory.INSTANCE.createCustomException(request, ApiError.FOREIGN_BLOCKCHAIN_NETWORK_ISSUE, e.getMessage());
        }
    }

    @POST
    @Path("/syncstatus")
    @Operation(
            summary = "Returns Monero sync status",
            description = "Supply 32 bytes of entropy, hex encoded",
            requestBody = @RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.TEXT_PLAIN,
                            schema = @Schema(
                                    type = "string",
                                    description = "32 bytes of entropy, hex encoded",
                                    example = "aabbcc..."
                            )
                    )
            ),
            responses = {
                    @ApiResponse(content = @Content(mediaType = MediaType.TEXT_PLAIN, schema = @Schema(type = "string")))
            }
    )
    @ApiErrors({ApiError.INVALID_PRIVATE_KEY})
    @SecurityRequirement(name = "apiKey")
    public String getMoneroSyncStatus(@HeaderParam(Security.API_KEY_HEADER) String apiKey, String entropyHex) {
        Security.checkApiCallAllowed(request);
        checkGateway();

        String normalized = requireEntropyHex(entropyHex);
        return MoneroWalletController.getInstance().getSyncStatusString(normalized);
    }

    private long parseAtomicAmount(Object amountObj) {
        if (amountObj == null) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }

        String raw;
        if (amountObj instanceof String) {
            raw = ((String) amountObj).trim();
        } else {
            raw = amountObj.toString().trim();
        }

        if (raw.isEmpty()) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }

        if (raw.startsWith("\"") && raw.endsWith("\"") && raw.length() >= 2) {
            raw = raw.substring(1, raw.length() - 1).trim();
        }

        String plain = raw;
        if (raw.contains("e") || raw.contains("E")) {
            plain = new BigDecimal(raw).toPlainString();
        }

        int decimals = 0;
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            decimals = plain.length() - dot - 1;
        }
        if (decimals > MAX_DECIMALS) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }

        try {
            BigDecimal decimal = new BigDecimal(raw);
            if (decimal.signum() <= 0) {
                throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
            }
            BigDecimal atomic = decimal.movePointRight(MAX_DECIMALS);
            atomic = atomic.setScale(0, RoundingMode.UNNECESSARY);
            return atomic.longValueExact();
        } catch (ArithmeticException | NumberFormatException e) {
            throw ApiExceptionFactory.INSTANCE.createException(request, ApiError.INVALID_CRITERIA);
        }
    }
}
