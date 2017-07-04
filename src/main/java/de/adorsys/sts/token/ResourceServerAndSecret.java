package de.adorsys.sts.token;

import org.apache.commons.lang3.StringUtils;

import de.adorsys.sts.rserver.ResourceServer;

/**
 * Temporarily holds a resource seerwver and a secret.
 * 
 * @author fpo
 *
 */
public class ResourceServerAndSecret {
	private ResourceServer resourceServer;
	private String rawSecret;
	private String encryptedSecret;
	
	public ResourceServer getResourceServer() {
		return resourceServer;
	}
	public void setResourceServer(ResourceServer resourceServer) {
		this.resourceServer = resourceServer;
	}

	public String getRawSecret() {
		return rawSecret;
	}
	public void setRawSecret(String rawSecret) {
		this.rawSecret = rawSecret;
	}
	public String getEncryptedSecret() {
		return encryptedSecret;
	}
	public void setEncryptedSecret(String encryptedSecret) {
		this.encryptedSecret = encryptedSecret;
	}
	
	public boolean hasEncryptedSecret(){
		return StringUtils.isNotBlank(encryptedSecret);
	}
	
}
