package org.qortal.api.model.crosschain;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

import io.swagger.v3.oas.annotations.media.Schema;

@XmlAccessorType(XmlAccessType.FIELD)
public class KomodoSendRequest {

	@Schema(description = "Komodo BIP32 extended private key", example = "tprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Recipient's Komodo address ('legacy' P2PKH only)", example = "1KmdCoinEaterAddressDontSendf59kuE")
	public String receivingAddress;

	@Schema(description = "Amount of KMD to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long komodoAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 KMD (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	public KomodoSendRequest() {
	}

}
