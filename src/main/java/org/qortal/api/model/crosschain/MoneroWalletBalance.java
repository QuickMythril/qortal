package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class MoneroWalletBalance {

    @Schema(description = "Balance in atomic units (piconero)", example = "1234567890000")
    public long balance;

    @Schema(description = "Unlocked balance in atomic units (piconero)", example = "1234500000000")
    public Long unlockedBalance;

    @Schema(description = "Wallet sync height", example = "2845123")
    public Long walletHeight;

    @Schema(description = "Daemon chain height", example = "2845125")
    public Long daemonHeight;

    public MoneroWalletBalance() {
    }
}
