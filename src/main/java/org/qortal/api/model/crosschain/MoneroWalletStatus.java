package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class MoneroWalletStatus {

    @Schema(description = "Wallet sync height", example = "2845123")
    public long walletHeight;

    @Schema(description = "Daemon chain height", example = "2845125")
    public Long daemonHeight;

    @Schema(description = "Whether the wallet is synchronized", example = "true")
    public boolean isSynchronized;

    @Schema(description = "Last error encountered by the wallet", example = "Unable to connect")
    public String lastError;

    public MoneroWalletStatus() {
    }
}
