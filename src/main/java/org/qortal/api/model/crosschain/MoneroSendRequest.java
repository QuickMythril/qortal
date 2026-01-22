package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;

@XmlAccessorType(XmlAccessType.FIELD)
public class MoneroSendRequest {

    @Schema(description = "32 bytes of entropy, hex encoded", example = "aabbcc...")
    public String entropyHex;

    @Schema(description = "Recipient's Monero address", example = "4...")
    public String receivingAddress;

    @Schema(description = "Amount of XMR to send (decimal string or numeric)", example = "0.123")
    public Object xmrAmount;

    @Schema(description = "Optional memo (ignored in Stage 1)", example = "hello")
    public String memo;

    public MoneroSendRequest() {
    }
}
