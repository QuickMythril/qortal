package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class MoneroWalletTransaction {

    @Schema(description = "Transaction id", example = "e5d3...")
    public String txid;

    @Schema(description = "Transaction timestamp (epoch seconds)", example = "1700000000")
    public Long timestamp;

    @Schema(description = "Block height", example = "2845123")
    public Long blockHeight;

    @Schema(description = "Confirmations", example = "12")
    public Long confirmations;

    @Schema(description = "Whether transaction is pending", example = "false")
    public Boolean isPending;

    @Schema(description = "Direction (in/out)", example = "in")
    public String direction;

    @Schema(description = "Total amount in atomic units (piconero)", example = "1000000000000")
    public long totalAmount;

    @Schema(description = "Fee in atomic units (piconero)", example = "2000000000")
    public Long feeAmount;

    public MoneroWalletTransaction() {
    }
}
