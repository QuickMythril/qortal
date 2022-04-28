package org.qortal.api.model.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class ZcashSendRequest {

	@Schema(description = "Zcash BIP32 extended private key", example = "tprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Recipient's Zcash address ('legacy' P2PKH only)", example = "1ZecCoinEaterAddressDontSendf59kuE")
	public String receivingAddress;

	@Schema(description = "Amount of ZEC to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long zcashAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 ZEC (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	public ZcashSendRequest() {
	}

}
