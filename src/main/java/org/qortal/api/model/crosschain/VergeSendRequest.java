package org.qortal.api.model.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class VergeSendRequest {

	@Schema(description = "Verge BIP32 extended private key", example = "tprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Recipient's Verge address ('legacy' P2PKH only)", example = "1XvgCoinEaterAddressDontSendf59kuE")
	public String receivingAddress;

	@Schema(description = "Amount of XVG to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long vergeAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 XVG (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	public VergeSendRequest() {
	}

}
