package org.qortal.crypto;

import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public abstract class TrustlessSSLSocketFactory {

	// Create a trust manager that does not validate certificate chains
	private static final TrustManager[] TRUSTLESS_MANAGER = new TrustManager[] {
		new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return new X509Certificate[0];
			}

			public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
			}
		}
	};

	// Install the all-trusting trust manager
	private static final SSLContext sc;
	static {
		try {
			sc = SSLContext.getInstance("TLSv1.3");
			sc.init(null, TRUSTLESS_MANAGER, new java.security.SecureRandom());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static SSLSocketFactory getSocketFactory() {
		return sc.getSocketFactory();
	}

}
