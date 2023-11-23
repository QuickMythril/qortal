package org.qortal.api.model.crosschain;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.xml.bind.annotation.XmlAccessType;
import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.adapters.XmlJavaTypeAdapter;

@XmlAccessorType(XmlAccessType.FIELD)
public class FiroSendRequest {

	@Schema(description = "Firo BIP32 extended private key", example = "tprv___________________________________________________________________________________________________________")
	public String xprv58;

	@Schema(description = "Recipient's Firo address ('legacy' P2PKH only)", example = "FiRocoinEaterAddressDontSendhLfzKD")
	public String receivingAddress;

	@Schema(description = "Amount of FIRO to send", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public long firoAmount;

	@Schema(description = "Transaction fee per byte (optional). Default is 0.00000100 FIRO (100 sats) per byte", example = "0.00000100", type = "number")
	@XmlJavaTypeAdapter(value = org.qortal.api.AmountTypeAdapter.class)
	public Long feePerByte;

	public FiroSendRequest() {
	}

}
