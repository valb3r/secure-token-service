package de.adorsys.sts.token;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.adorsys.jjwk.selector.JWEEncryptedSelector;
import org.adorsys.jjwk.selector.KeyExtractionException;
import org.adorsys.jjwk.selector.UnsupportedEncAlgorithmException;
import org.adorsys.jjwk.selector.UnsupportedKeyLengthException;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.nimbusds.jose.EncryptionMethod;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWEAlgorithm;
import com.nimbusds.jose.JWEEncrypter;
import com.nimbusds.jose.JWEHeader;
import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.Payload;
import com.nimbusds.jose.RemoteKeySourceException;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.RemoteJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jose.util.DefaultResourceRetriever;
import com.nimbusds.jose.util.ResourceRetriever;

import de.adorsys.sts.rserver.ResourceServer;
import de.adorsys.sts.rserver.ResourceServerInfo;
import de.adorsys.sts.rserver.ResourceServerManager;
import de.adorsys.sts.rserver.ResourceServers;
import de.adorsys.sts.user.UserCredentials;
import de.adorsys.sts.user.UserDataService;

/**
 * Processes information specific to a resoruce server.
 * 
 * @author fpo
 *
 */
@Service
public class ResourceServerProcessor {

	/**
	 * The default HTTP connect timeout for JWK set retrieval, in
	 * milliseconds. Set to 250 milliseconds.
	 */
	public static final int DEFAULT_HTTP_CONNECT_TIMEOUT = 250;


	/**
	 * The default HTTP read timeout for JWK set retrieval, in
	 * milliseconds. Set to 250 milliseconds.
	 */
	public static final int DEFAULT_HTTP_READ_TIMEOUT = 250;


	/**
	 * The default HTTP entity size limit for JWK set retrieval, in bytes.
	 * Set to 50 KBytes.
	 */
	public static final int DEFAULT_HTTP_SIZE_LIMIT = 50 * 1024;
	
    @Autowired
    private ResourceServerManager resourceServerManager;
    private static JWKSelector encKeySelector = new JWKSelector(new JWKMatcher.Builder().keyUse(KeyUse.ENCRYPTION).build());
	private ResourceRetriever resourceRetriever=new DefaultResourceRetriever(DEFAULT_HTTP_CONNECT_TIMEOUT, DEFAULT_HTTP_READ_TIMEOUT, DEFAULT_HTTP_SIZE_LIMIT);
	
	/**
	 * Returns the list of resource server with corresponding user custom secret.
	 * 
	 * @param audiences
	 * @param resources
	 * @param userDataService
	 * @return
	 */
	public List<ResourceServerAndSecret> processResources(String[] audiences, String[] resources, UserDataService userDataService){
		
		// Result
		List<ResourceServerAndSecret> resurceServers = new ArrayList<>();
			
		Map<String, Map<String, ResourceServer>> resourceServersMultiMap = resourceServerManager.getResourceServersMultiMap();
		
		if(audiences!=null) filterServersByAudience(audiences, resourceServersMultiMap, resurceServers);

		if(resources!=null)filterServersByResources(resources, resourceServersMultiMap, resurceServers);
		
		if(resurceServers.isEmpty()) return resurceServers;
		
		// If Resources are set, we can get or create the corresponding user secrets and have them included in the token.
		loadUserCredentials(userDataService, resurceServers);

		// Encrypt credentials for token
		for (ResourceServerAndSecret resourceServerAndSecret : resurceServers) {
			ResourceServerInfo serverInfo = new ResourceServerInfo(resourceRetriever, resourceServerAndSecret.getResourceServer());
			RemoteJWKSet<SecurityContext> jwkSource = serverInfo.getJWKSource();
			List<JWK> keys;
			try {
				keys = jwkSource.get(encKeySelector, null);
			} catch (RemoteKeySourceException e) {
				// TODO. Log Warn
				e.printStackTrace();
				continue;
			}
			if(keys==null ||  keys.isEmpty()) continue;
			JWK jwk = keys.iterator().next();
			JWEEncrypter jweEncrypter;
			try {
				jweEncrypter = JWEEncryptedSelector.geEncrypter(jwk, null, null);
			} catch (UnsupportedEncAlgorithmException | KeyExtractionException | UnsupportedKeyLengthException e) {
				// TODO log.warn
				e.printStackTrace();
				continue;
			}
			Payload payload = new Payload(resourceServerAndSecret.getRawSecret());
			// JWE encrypt secret.
			JWEObject jweObj;
			try {
				jweObj = new JWEObject(getHeader(jwk), payload);
				jweObj.encrypt(jweEncrypter);
			} catch (JOSEException e) {
				// TODO log.warn
				e.printStackTrace();
				continue;
			}
			String serializedCredential = jweObj.serialize();
			resourceServerAndSecret.setEncryptedSecret(serializedCredential);
		}
		return resurceServers;
	}

	private JWEHeader getHeader(JWK jwk) throws JOSEException {
        if (jwk instanceof RSAKey) {
            return new JWEHeader(JWEAlgorithm.RSA_OAEP, EncryptionMethod.A128GCM);
        } else if (jwk instanceof ECKey) {
            return new JWEHeader(JWEAlgorithm.ECDH_ES_A128KW, EncryptionMethod.A192GCM);
        }
        return null;
    }
	
	private List<ResourceServerAndSecret> filterServersByResources(String[] resources, Map<String, Map<String, ResourceServer>> resourceServersMultiMap, final List<ResourceServerAndSecret> result){
		Map<String, ResourceServer> map = resourceServersMultiMap.get(ResourceServers.ENDPOINT);
		return filterServers0(resources, map, result);
	}
	private List<ResourceServerAndSecret> filterServersByAudience(String[] audiences, Map<String, Map<String, ResourceServer>> resourceServersMultiMap, final List<ResourceServerAndSecret> result){
		Map<String, ResourceServer> map = resourceServersMultiMap.get(ResourceServers.AUNDIENCE);
		return filterServers0(audiences, map, result);
	}

	private List<ResourceServerAndSecret> filterServers0(String[] keys, Map<String, ResourceServer> map, final List<ResourceServerAndSecret> result){
		for (String key : keys) {
			ResourceServer resourceServer = map.get(key);
			if(resourceServer==null) continue;
			for (ResourceServerAndSecret resourceServerAndSecret : result) {
				if(resourceServer.equals(resourceServerAndSecret.getResourceServer())) continue;
			}
			ResourceServerAndSecret resourceServerAndSecret = new ResourceServerAndSecret();
			resourceServerAndSecret.setResourceServer(resourceServer);
			result.add(resourceServerAndSecret);
		}
		return result;
	}
	
	private void loadUserCredentials(UserDataService userDataService, List<ResourceServerAndSecret> resurceServers){
		if(userDataService==null) return;
		// If Resources are set, we can get or create the corresponding user secrets and have them included in the token.
		UserCredentials userCredentials = userDataService.loadUserCredentials();

		boolean store = false;
		for (ResourceServerAndSecret resourceServer : resurceServers) {
			String credentialForResourceServer = userCredentials.getCredentialForResourceServer(resourceServer.getResourceServer().getAudience());
			if(credentialForResourceServer==null){
				// create one
				credentialForResourceServer = RandomStringUtils.randomGraph(16);
				userCredentials.setCredentialForResourceServer(resourceServer.getResourceServer().getAudience(), credentialForResourceServer);
				store = true;
			}
			resourceServer.setRawSecret(credentialForResourceServer);
		}
		if(store){
			userDataService.storeUserCredentials(userCredentials);
		}
	}
}
