/*
 * Copyright IBM Corp. All Rights Reserved.
 *
 * SPDX-License-Identifier: Apache-2.0
 */

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParser;
import io.grpc.Grpc;
import io.grpc.ManagedChannel;
import io.grpc.TlsChannelCredentials;
import org.hyperledger.fabric.client.CommitException;
import org.hyperledger.fabric.client.CommitStatusException;
import org.hyperledger.fabric.client.Contract;
import org.hyperledger.fabric.client.EndorseException;
import org.hyperledger.fabric.client.Gateway;
import org.hyperledger.fabric.client.GatewayException;
import org.hyperledger.fabric.client.SubmitException;
import org.hyperledger.fabric.client.identity.Identities;
import org.hyperledger.fabric.client.identity.Identity;
import org.hyperledger.fabric.client.identity.Signer;
import org.hyperledger.fabric.client.identity.Signers;
import org.hyperledger.fabric.client.identity.X509Identity;
import org.hyperledger.fabric.client.Proposal.Builder;
import org.hyperledger.fabric.client.Transaction;
import org.hyperledger.fabric.client.Proposal;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Arrays;
import java.util.Map;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

public final class App {
	private static final String ORG = System.getenv().getOrDefault("ORG", "org1");
	// private static final String MSP_ID = System.getenv().getOrDefault("MSP_ID", "Org1MSP");
	// Update MSP ID based on organization.
    private static final String MSP_ID = ORG.substring(0, 1).toUpperCase() + ORG.substring(1) + "MSP";
	private static final String CHANNEL_NAME = System.getenv().getOrDefault("CHANNEL_NAME", "mako");
	private static final String CHAINCODE_NAME = System.getenv().getOrDefault("CHAINCODE_NAME", "request");
	private static final String USER = System.getenv().getOrDefault("USER", "owner");

	// Update paths to crypto materials based on organization and user.
	private static final Path CRYPTO_PATH = Paths.get("../../fabric-samples/test-network/organizations/peerOrganizations", ORG + ".example.com");
	private static final Path CERT_PATH = CRYPTO_PATH.resolve(Paths.get("users", USER + "@" + ORG + ".example.com", "msp", "signcerts", "cert.pem"));
	private static final Path KEY_DIR_PATH = CRYPTO_PATH.resolve(Paths.get("users", USER + "@" + ORG + ".example.com", "msp", "keystore"));
	private static final Path TLS_CERT_PATH = CRYPTO_PATH.resolve(Paths.get("peers", "peer0." + ORG + ".example.com", "tls", "ca.crt"));

	// Update peer endpoint and override authority based on organization.
	private static final String OVERRIDE_AUTH = "peer0." + ORG + ".example.com";
	private static final String PEER_ENDPOINT;
	static {
		switch (ORG) {
			case "org1":
				PEER_ENDPOINT = "localhost:7051";
				break;
			case "org2":
				PEER_ENDPOINT = "localhost:9051";
				break;
			case "org3":
				PEER_ENDPOINT = "localhost:11051";
				break;
			default:
				throw new IllegalArgumentException("Unsupported organization: " + ORG);
		}
	}

	private final Contract contract;
	// private final String assetId = "asset" + Instant.now().toEpochMilli();
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	public static void main(final String[] args) throws Exception {
		// The gRPC client connection should be shared by all Gateway connections to
		// this endpoint.
		var channel = newGrpcConnection();

		var builder = Gateway.newInstance().identity(newIdentity()).signer(newSigner()).connection(channel)
				// Default timeouts for different gRPC calls
				.evaluateOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.endorseOptions(options -> options.withDeadlineAfter(15, TimeUnit.SECONDS))
				.submitOptions(options -> options.withDeadlineAfter(5, TimeUnit.SECONDS))
				.commitStatusOptions(options -> options.withDeadlineAfter(1, TimeUnit.MINUTES));

		try (var gateway = builder.connect()) {
			// new App(gateway).run(args[0]);
			App app = new App(gateway);
			switch (args[0]) {
				case "RetrieveFile": 
					app.ReadAsset(args[1]); 
					break;
				case "CreateAsset": 
					app.createAsset(args[1], args[2], args[3], Integer.parseInt(args[4]), args[5], args[6]);
					break;
				case "UploadKey":
					app.UploadKey(args[1], args[2]);
					break;
				case "ReadAsset":
					app.ReadAsset(args[1]);
					break;
				case "ReadAcl":
					app.ReadAcl(args[1]);   
					break;
				case "RequestPermission":
					app.requestPermission(args[1], args[2]);   
					break;
				case "GetHistoryForAsset":
					app.getHistoryForAsset(args[1]);   
					break;
				case "UpdateAclPermission":
					app.updateAclPermission(args[1], args[2]);   
					break;
				case "RevokeAclPermission":
					app.revokeAclPermission(args[1], args[2]);   
					break;
				case "DeleteAsset":
					app.DeleteAsset(args[1]);   
					break;
				case "eraseDataRequest":
					app.eraseDataRequest(args[1]);   
					break;
				case "QueryAssetByPatient":
					app.QueryAssetByPatient(args[1]);   
					break;
				case "CreateAsset_mock": 
					app.createAsset(args[1], args[2], args[3], Integer.parseInt(args[4]), args[5], args[6]);
					break;
			}
		} finally {
			channel.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
		}
	}

	private static ManagedChannel newGrpcConnection() throws IOException {
		var credentials = TlsChannelCredentials.newBuilder()
				.trustManager(TLS_CERT_PATH.toFile())
				.build();
		return Grpc.newChannelBuilder(PEER_ENDPOINT, credentials)
				.overrideAuthority(OVERRIDE_AUTH)
				.build();
	}

	private static Identity newIdentity() throws IOException, CertificateException {
		var certReader = Files.newBufferedReader(CERT_PATH);
		var certificate = Identities.readX509Certificate(certReader);

		return new X509Identity(MSP_ID, certificate);
	}

	private static Signer newSigner() throws IOException, InvalidKeyException {
		var keyReader = Files.newBufferedReader(getPrivateKeyPath());
		var privateKey = Identities.readPrivateKey(keyReader);

		return Signers.newPrivateKeySigner(privateKey);
	}

	private static Path getPrivateKeyPath() throws IOException {
		try (var keyFiles = Files.list(KEY_DIR_PATH)) {
			return keyFiles.findFirst().orElseThrow();
		}
	}

	public App(final Gateway gateway) {
		// Get a network instance representing the channel where the smart contract is
		// deployed.
		var network = gateway.getNetwork(CHANNEL_NAME);

		// Get the smart contract from the network.
		contract = network.getContract(CHAINCODE_NAME);
	}
	
	/**
	 * This type of transaction would typically only be run once by an application
	 * the first time it was started after its initial deployment. A new version of
	 * the chaincode deployed later would likely not need to run an "init" function.
	 */
	private void initLedger() throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: InitLedger, function creates the initial set of assets on the ledger");

		contract.submitTransaction("InitLedger");

		System.out.println("*** Transaction committed successfully");
	}

	private String prettyJson(final byte[] json) {
		return prettyJson(new String(json, StandardCharsets.UTF_8));
	}

	private String prettyJson(final String json) {
		var parsedJson = JsonParser.parseString(json);
		return gson.toJson(parsedJson);
	}

	private void createAsset(final String assetID, final String pointer, final String dataSubject, final int version, final String filekey, final String acl_string) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: CreateAsset, creates new asset with ID %s", assetID);
		List<String> acl = Arrays.asList(acl_string.split(","));
		// Create a map to hold the transient data
		Map<String, byte[]> transientDataMap = new HashMap<>();

		// Create a JSON object with the data you want to pass
		JSONObject json = new JSONObject();
		json.put("pointer", pointer);
		json.put("filekey", filekey);
		json.put("acl", acl);
		json.put("assetID", assetID);
		json.put("dataSubject", dataSubject);
		json.put("version", version);

		// Convert the JSON object to a byte array and add it to the transient data map
		transientDataMap.put("asset_properties", json.toString().getBytes(StandardCharsets.UTF_8));

		var transaction = contract.newProposal("CreateAsset")
				.putAllTransient(transientDataMap)
				.build()
				.endorse()
				.submit();

		System.out.println("*** Transaction committed successfully");
	}

	private void UploadKey(final String keyType, final String key) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: uploadKey, adding key %s",keyType);
		// Create a map to hold the transient data
		Map<String, byte[]> transientDataMap = new HashMap<>();

		// Create a JSON object with the data you want to pass
		JSONObject json = new JSONObject();
		json.put("key", key);
		json.put("keyType", keyType);

		// Convert the JSON object to a byte array and add it to the transient data map
		transientDataMap.put("key_info", json.toString().getBytes(StandardCharsets.UTF_8));

		var transaction = contract.newProposal("uploadKey")
				.putAllTransient(transientDataMap)
				.build()
				.endorse()
				.submit();

		System.out.println("*** Transaction committed successfully");
	}

	private void DeleteAsset(final String assetID) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: DeleteAsset, delete asset with ID %s", assetID);
		// Create a map to hold the transient data
		Map<String, byte[]> transientDataMap = new HashMap<>();

		// Create a JSON object with the data you want to pass
		JSONObject json = new JSONObject();
		json.put("assetID", assetID);

		// Convert the JSON object to a byte array and add it to the transient data map
		transientDataMap.put("asset_delete", json.toString().getBytes(StandardCharsets.UTF_8));

		var transaction = contract.newProposal("DeleteAsset")
				.putAllTransient(transientDataMap)
				.build()
				.endorse()
				.submit();

		System.out.println("*** Transaction committed successfully");
	}

	private void GetAsset(final String assetID) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: ReadAsset, function returns asset attributes and write this access record into the ledger");
		var evaluateResult = contract.submitTransaction("ReadAsset", assetID);
		System.out.println("*** Transaction committed successfully");

		String results = new String(evaluateResult, StandardCharsets.UTF_8);

		// Extract Asset ID
		int assetIdIndex = results.indexOf("Asset ID:") + 10;
		int dataSubjectIndex = results.indexOf("Data Subject:") - 2;
		String assetId = results.substring(assetIdIndex, dataSubjectIndex);

		// Extract File Key
		int fileKeyIndex = results.indexOf("File Key:") + 9;
		int pointerIndex = results.indexOf("Pointer:") - 2;
		String fileKey = results.substring(fileKeyIndex, pointerIndex);

		// Extract Pointer
		int pointerStartIndex = results.indexOf("Pointer:") + 9;
		String pointer = results.substring(pointerStartIndex);

		// return assetId, fileKey, pointer
		System.out.printf("assetId: %s, fileKey: %s, pointer: %s",assetId, fileKey, pointer);
	}

	private void ReadAsset(final String assetID) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.println("\n--> Submit Transaction: ReadAsset, function returns asset attributes and write this access record into the ledger");

		var evaluateResult = contract.submitTransaction("ReadAsset", assetID);

		System.out.println("*** Transaction committed successfully");
		// System.out.println("*** Result:" + prettyJson(evaluateResult));
		System.out.println("*** Result: " + new String(evaluateResult, StandardCharsets.UTF_8));
	}

	private void eraseDataRequest(final String assetID) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: eraseDataRequest, function tries to send data erase request to all data holders for asset %s", assetID);

		var evaluateResult = contract.submitTransaction("eraseDataRequest", assetID);

		System.out.println("*** Transaction committed successfully");

		System.out.println("*** Result: " + new String(evaluateResult, StandardCharsets.UTF_8));
	}

	private void ReadAcl(final String assetID) throws GatewayException {
		System.out.println("\n--> Evaluate Transaction: ReadAcl, function returns asset attribute");

		var evaluateResult = contract.evaluateTransaction("ReadAcl", assetID);
		
		System.out.println("*** Result:" + prettyJson(evaluateResult));
	}

	private void QueryAssetByPatient(final String dataSubject) throws GatewayException {
		System.out.printf("\n--> Evaluate Transaction: QueryAssetByPatient, function returns all assets related to dataSubject %s:", dataSubject);

		var evaluateResult = contract.evaluateTransaction("QueryAssetByPatient", dataSubject);
		
		System.out.println("*** Result: " + new String(evaluateResult, StandardCharsets.UTF_8));
	}

	private void getHistoryForAsset(final String assetID) throws GatewayException {
		System.out.printf("\n--> Evaluate Transaction: retrieving transaction records of %s", assetID);

		var evaluateResult = contract.evaluateTransaction("getHistoryForAsset", assetID);
		
		System.out.println(" Result: " + new String(evaluateResult, StandardCharsets.UTF_8) + ",");
	}

	private void updateAclPermission(final String assetID, final String newOrg) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: updateAclPermission for asset %s to add %s to access control list", assetID, newOrg);
		
		contract.submitTransaction("updateAclPermission", assetID, newOrg);
		
		System.out.println("*** Transaction committed successfully");
	}

	private void revokeAclPermission(final String assetID, final String targetOrg) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: revokeAclPermission for asset %s to revoke the permission of %s to access", assetID, targetOrg);
		
		contract.submitTransaction("revokeAclPermission", assetID, targetOrg);
		
		System.out.println("*** Transaction committed successfully");
	}

	private void requestPermission(final String assetID, final String purpose) throws EndorseException, SubmitException, CommitStatusException, CommitException {
		System.out.printf("\n--> Submit Transaction: requesting access for asset %s for the purpose of: %s", assetID, purpose);
		
		contract.submitTransaction("requestPermission", assetID, purpose);
		
		System.out.println("*** Transaction committed successfully");
	}

}
