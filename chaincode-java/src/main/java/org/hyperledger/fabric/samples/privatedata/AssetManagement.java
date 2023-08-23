/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.Context;
import org.hyperledger.fabric.contract.ContractInterface;
import org.hyperledger.fabric.contract.annotation.Contact;
import org.hyperledger.fabric.contract.annotation.Contract;
import org.hyperledger.fabric.contract.annotation.Default;
import org.hyperledger.fabric.contract.annotation.Info;
import org.hyperledger.fabric.contract.annotation.License;
import org.hyperledger.fabric.contract.annotation.Transaction;
import org.hyperledger.fabric.shim.ChaincodeException;
import org.hyperledger.fabric.shim.ChaincodeStub;
// import org.hyperledger.fabric.shim.ledger.CompositeKey;

import org.hyperledger.fabric.shim.ledger.KeyValue;
import org.hyperledger.fabric.shim.ledger.QueryResultsIterator;
import org.hyperledger.fabric.shim.ledger.KeyModification;
import org.json.JSONObject;

import java.util.ArrayList;
// import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

/**
 * Main Chaincode class. A ContractInterface gets converted to Chaincode internally.
 * @see org.hyperledger.fabric.shim.Chaincode
 *
 * Each chaincode transaction function must take, Context as first parameter.
 * Unless specified otherwise via annotation (@Contract or @Transaction), the contract name
 * is the class name (without package)
 * and the transaction name is the method name.
 *
 * To create fabric test-network
 *   cd fabric-samples/test-network
 *   ./network.sh up createChannel -ca -s couchdb
 * To deploy this chaincode to test-network, use the collection config as described in
 * See <a href="https://hyperledger-fabric.readthedocs.io/en/latest/private_data_tutorial.html</a>
 * Change both -ccs sequence & -ccv version args for iterative deployment
 *  ./network.sh deployCC -ccn private -ccp ../asset-transfer-private-data/chaincode-java/ -ccl java -ccep "OR('Org1MSP.peer','Org2MSP.peer')" -cccg ../asset-transfer-private-data/chaincode-go/collections_config.json -ccs 1 -ccv 1
 */
@Contract(
        name = "private",
        info = @Info(
                title = "Asset Management Private Data",
                description = "The hyperlegendary asset management private data",
                version = "0.0.1-SNAPSHOT",
                license = @License(
                        name = "Apache 2.0 License",
                        url = "http://www.apache.org/licenses/LICENSE-2.0.html"),
                contact = @Contact(
                        email = "chensy96@gmail.com",
                        name = "Private Asset Management",
                        url = "https://hyperledger.example.com")))
@Default
public final class AssetManagement implements ContractInterface {

    static final String ASSET_COLLECTION_NAME = "medCollection";
    static final String AGREEMENT_KEYPREFIX = "transferAgreement";

    private enum AssetTransferErrors {
        INCOMPLETE_INPUT,
        INVALID_ACCESS,
        ASSET_NOT_FOUND,
        ASSET_ALREADY_EXISTS
    }

    /**
     * Retrieves the asset public details with the specified ID from the AssetCollection.
     *
     * @param ctx     the transaction context
     * @param assetID the ID of the asset
     * @return the asset found on the ledger if there was one
     */
    // @Transaction(intent = Transaction.TYPE.EVALUATE)
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String ReadAsset(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            System.out.printf("Asset not found: ID %s\n", assetID);
            return null;
        }

        Asset asset = Asset.deserialize(assetJSON);

        // Get the client's ID and role
        // String clientID = ctx.getClientIdentity().getId();
        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = extractId(ctx.getClientIdentity().getId());

        // Check if the client's ID is in the asset's acl list and if the client's role is not 'patient'
        if (!asset.getAcl().contains(clientMspId)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to read asset %s", clientMspId, clientRole, assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        String dataSubject = asset.getDataSubject();
        int version = asset.getVersion();
        String owner = asset.getOwner();
        String filekey = asset.getFilekey();
        String pointer = asset.getPointer();
        String result = String.format("Asset ID: %s,  Data Subject: %s,  Version: %d,  Owner: %s,  File Key: %s,  Pointer: %s", assetID, dataSubject, version, owner, filekey, pointer);

        // prevent a patient to read another patient's data within the same hospital
        if ("patient".equals(clientRole) && !idName.equals(dataSubject)) {
            String errorMessage = String.format("Patient with id %s is not authorized to read asset of %s", idName, dataSubject);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // Record the asset read on the public ledger
        String assetReadRecord = String.format("Asset %s read by %s at %s", assetID, clientMspId, Instant.now());
        String assetReadRecordId = assetID + "_read";

        stub.putStringState(assetReadRecordId, assetReadRecord);
        // return asset;
        return result;
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String ReadAcl(final Context ctx, final String assetID) {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("ReadAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            System.out.printf("Asset not found: ID %s\n", assetID);
            return null;
        }

        Asset asset = Asset.deserialize(assetJSON);

        // Get the client's ID and role
        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = extractId(ctx.getClientIdentity().getId());

        // Extract the organization from the owner's certificate
        String owner = asset.getOwner();
        String dataSubject = asset.getDataSubject();
        String ownerOrg = null;
        String[] parts = owner.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("O=")) {
                String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
            }
        }
        // Check if the client is from the owner's organization
        if (!clientMspId.equals(ownerOrg)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to read ACL from %s", clientMspId, clientRole, ownerOrg);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
        // prevent a patient to read another patient's acl within the same hospital
        if ("patient".equals(clientRole) && !idName.equals(dataSubject)) {
            String errorMessage = String.format("Patient with id %s is not authorized to read ACL of %s", idName, dataSubject);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        List<String> acl = asset.getAcl();

        return acl.toString();
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void updateAclPermission(final Context ctx, final String assetID, final String newOrg) {
        ChaincodeStub stub = ctx.getStub();
        // Retrieve the asset from the ledger
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);
        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset not found: ID %s\n", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
        Asset asset = Asset.deserialize(assetJSON);

        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");

        // Extract the organization from the owner's certificate
        String owner = asset.getOwner();
        String ownerOrg = null;
        String[] parts = owner.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("O=")) {
                String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
            }
        }

        // Check if the client is from the owner's organization
        if (!clientMspId.equals(ownerOrg) || "patient".equals(clientRole)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to update asset from %s", clientMspId, clientRole, ownerOrg);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // If both signatures are valid, perform the update
        asset.addToAcl(newOrg);
        stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, asset.serialize());

        // Record the asset read on the public ledger
        String aclUpdateRecord = String.format("ACL of asset %s added user %s at %s", assetID, newOrg, Instant.now());
        String aclUpdateRecordId = assetID + "_acl";

        stub.putStringState(aclUpdateRecordId, aclUpdateRecord);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void revokeAclPermission(final Context ctx, final String assetID, final String targetOrg) {
        ChaincodeStub stub = ctx.getStub();
        // Retrieve the asset from the ledger
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);
        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset not found: ID %s\n", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
        Asset asset = Asset.deserialize(assetJSON);

        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = extractId(ctx.getClientIdentity().getId());

        // Extract the organization from the owner's certificate
        String owner = asset.getOwner();
        String ownerOrg = null;
        String dataSubject = asset.getDataSubject();
        String[] parts = owner.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("O=")) {
                String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
            }
        }

        // Check if the client is from the owner's organization
        if (!clientMspId.equals(ownerOrg)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to revoke access rights from %s", clientMspId, clientRole, ownerOrg);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // prevent a patient to change another patient's data acl within the same hospital
        if ("patient".equals(clientRole) && !idName.equals(dataSubject)) {
            String errorMessage = String.format("Patient with id %s is not authorized to evoke access rights of %s", idName, dataSubject);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // If both signatures are valid, perform the update
        asset.removeFromAcl(targetOrg);
        stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, asset.serialize());

        // Record the asset read on the public ledger
        String aclUpdateRecord = String.format("ACL of asset %s deleted user %s at %s", assetID, targetOrg, Instant.now());
        String aclUpdateRecordId = assetID + "_acl";

        stub.putStringState(aclUpdateRecordId, aclUpdateRecord);
        }

    /**
     * GetAssetByRange performs a range query based on the start and end keys provided. Range
     * queries can be used to read data from private data collections, but can not be used in
     * a transaction that also writes to private collection, since transaction may not get endorsed
     * on some peers that do not have the collection.
     *
     * @param ctx      the transaction context
     * @param startKey for ID range of the asset
     * @param endKey   for ID range of the asset
     * @return the asset found on the ledger if there was one
     */
    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public Asset[] GetAssetByRange(final Context ctx, final String startKey, final String endKey) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("GetAssetByRange: start %s, end %s\n", startKey, endKey);

        List<Asset> queryResults = new ArrayList<>();
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataByRange(ASSET_COLLECTION_NAME, startKey, endKey)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Asset asset = Asset.deserialize(result.getStringValue());
                queryResults.add(asset);
                System.out.println("QueryResult: " + asset.toString());
            }
        }
        return queryResults.toArray(new Asset[0]);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void requestPermission(final Context ctx, final String assetID, final String purpose) {
        ChaincodeStub stub = ctx.getStub();

        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = ctx.getClientIdentity().getId();

        // patients are not allowed to request permissions
        if ("patient".equals(clientRole)) {
            String errorMessage = String.format("Client with role %s is not authorized to request access to asset", clientRole);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // Record the request on the public ledger
        String assetRequestRecord = String.format("User %s requested access to asset %s for organization %s at %s, for the strict usage purpose: %s.", idName, assetID, clientMspId, Instant.now(), purpose);
        String assetRequestRecordId = assetID + "_request";

        stub.putStringState(assetRequestRecordId, assetRequestRecord);
    }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String getHistoryForAsset(final Context ctx, final String assetId) {
        ChaincodeStub stub = ctx.getStub();

        List<String> assetHistory = new ArrayList<>();
        QueryResultsIterator<KeyModification> results = stub.getHistoryForKey(assetId);

        for (KeyModification modification : results) {
            String value = new String(modification.getValue(), UTF_8);
            assetHistory.add(value);
        }

        return String.join(",", assetHistory);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void uploadKey(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("key_info")) {
            String errorMessage = String.format("uploadKey call must specify key_info in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        byte[] transientAssetJSON = transientMap.get("key_info");
        final String key;
        final String keyType;
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            String jsonString = json.toString();
            Map<String, Object> tMap = json.toMap();
            key = (String) tMap.get("key");
            keyType = (String) tMap.get("keyType");
        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }
        String clientMspId = ctx.getClientIdentity().getMSPID();
        String keyId = clientMspId + "_" + keyType;
        stub.putStringState(keyId, key);
    }

    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public String eraseDataRequest(final Context ctx, final String assetId) {
        ChaincodeStub stub = ctx.getStub();

        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = extractId(ctx.getClientIdentity().getId());

        // Retrieve the asset from the ledger
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetId);
        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset not found: ID %s\n", assetId);
            System.err.println(errorMessage);
            // throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
            String assetDeletionRecordId = assetId + "_deletion";
            String assetDeletionRecord = getHistoryForAsset(ctx, assetDeletionRecordId);
            if (assetDeletionRecord == null || assetDeletionRecord.length() == 0) {
                throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
            }
            String[] parts = assetDeletionRecord.split(" ");
            String deletedAssetId = parts[1];
            String dataSubjectId = parts[3];
            String clientMspIdId = parts[7];
            if (deletedAssetId.equals(assetId)) {
                if (!clientMspId.equals(clientMspIdId)) {
                    String errorMessage2 = String.format("Client %s with role %s is not authorized to erase asset %s.", idName, clientRole, assetId);
                    System.err.println(errorMessage2);
                    throw new ChaincodeException(errorMessage2, AssetTransferErrors.INVALID_ACCESS.toString());
                }
                if ("patient".equals(clientRole) && !idName.equals(dataSubjectId)) {
                    String errorMessage2 = String.format("Patient with id %s is not authorized to erase asset %s of %s", idName, assetId, dataSubjectId);
                    System.err.println(errorMessage2);
                    throw new ChaincodeException(errorMessage2, AssetTransferErrors.INVALID_ACCESS.toString());
                }
                // Record the asset erasure on the public ledger
                String dataErasureRecord = String.format("Asset %s was erased by %s with id %s from %s at %s", assetId, clientMspId, idName, ASSET_COLLECTION_NAME, Instant.now());
                String dataErasureRecordId = assetId + "_erasure";
                stub.putStringState(dataErasureRecordId, dataErasureRecord);
                String assetReadRecordId = assetId + "_read";
                return getHistoryForAsset(ctx, assetReadRecordId);
            } else {
                throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
            }
        }
        Asset asset = Asset.deserialize(assetJSON);
        // Extract the organization from the owner's certificate
        String owner = asset.getOwner();
        String dataSubject = asset.getDataSubject();
        String ownerOrg = null;
        String[] parts = owner.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("O=")) {
                String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
            }
        }
        // Check if the client is from the owner's organization
        if (!clientMspId.equals(ownerOrg)) {
            String errorMessage2 = String.format("Client %s with role %s is not authorized to erase asset from %s", clientMspId, clientRole, ownerOrg);
            System.err.println(errorMessage2);
            throw new ChaincodeException(errorMessage2, AssetTransferErrors.INVALID_ACCESS.toString());
        }
        if ("patient".equals(clientRole) && !idName.equals(asset.getDataSubject())) {
            String errorMessage2 = String.format("Patient with id %s is not authorized to erase asset %s of %s", idName, assetId, dataSubject);
            System.err.println(errorMessage2);
            throw new ChaincodeException(errorMessage2, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // also delete the key from asset collection
        System.out.printf("DeleteAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetId);
        stub.delPrivateData(ASSET_COLLECTION_NAME, assetId);

        // Record the asset deletion on the public ledger
        String assetDeletionRecord = String.format("Asset %s of %s was deleted by %s with id %s from %s at %s", assetId, dataSubject, clientMspId, idName, ASSET_COLLECTION_NAME, Instant.now());
        String assetDeletionRecordId = assetId + "_deletion";
        stub.putStringState(assetDeletionRecordId, assetDeletionRecord);

        // Record the asset erasure on the public ledger
        String dataErasureRecord = String.format("Asset %s was erased by %s with id %s from %s at %s", assetId, clientMspId, idName, ASSET_COLLECTION_NAME, Instant.now());
        String dataErasureRecordId = assetId + "_erasure";
        stub.putStringState(dataErasureRecordId, dataErasureRecord);

        String assetReadRecordId = assetId + "_read";
        return getHistoryForAsset(ctx, assetReadRecordId);
    }

    // =======Rich queries =========================================================================
    // Two examples of rich queries are provided below (parameterized query and ad hoc query).
    // Rich queries pass a query string to the state database.
    // Rich queries are only supported by state database implementations
    //  that support rich query (e.g. CouchDB).
    // The query string is in the syntax of the underlying state database.
    // With rich queries there is no guarantee that the result set hasn't changed between
    //  endorsement time and commit time, aka 'phantom reads'.
    // Therefore, rich queries should not be used in update transactions, unless the
    // application handles the possibility of result set changes between endorsement and commit time.
    // Rich queries can be used for point-in-time queries against a peer.
    // ============================================================================================

    /**
     * QueryAssetByOwner queries for assets based on pointer, owner.
     * This is an example of a parameterized query where the query logic is baked into the chaincode,
     * and accepting a single query parameter (owner).
     *
     * @param ctx       the transaction context
     * @param type type to query for
     * @param owner     asset owner to query for
     * @return the asset found on the ledger if there was one
     */
    // @Transaction(intent = Transaction.TYPE.EVALUATE)
    // public Asset[] QueryAssetByOwner(final Context ctx, final String type, final String owner) throws Exception {
    //     String queryString = String.format("{\"selector\":{\"pointer\":\"%s\",\"owner\":\"%s\"}}", type, owner);
    //     return getQueryResult(ctx, queryString);
    // }

    @Transaction(intent = Transaction.TYPE.EVALUATE)
    public String QueryAssetByPatient(final Context ctx, final String patient) throws Exception {
        String queryString = String.format("{\"selector\":{\"dataSubject\":\"%s\"}}", patient);
        return getQueryResult(ctx, queryString);
    }

    /**
     * QueryAssets uses a query string to perform a query for assets.
     * Query string matching state database syntax is passed in and executed as is.
     * Supports ad hoc queries that can be defined at runtime by the client.
     *
     * @param ctx         the transaction context
     * @param queryString query string matching state database syntax
     * @return the asset found on the ledger if there was one
     */

    private String getQueryResult(final Context ctx, final String queryString) throws Exception {
        ChaincodeStub stub = ctx.getStub();
        System.out.printf("QueryAssets: %s\n", queryString);

        String queryResults = " ";
        // retrieve asset with keys between startKey (inclusive) and endKey(exclusive) in lexical order.
        try (QueryResultsIterator<KeyValue> results = stub.getPrivateDataQueryResult(ASSET_COLLECTION_NAME, queryString)) {
            for (KeyValue result : results) {
                if (result.getStringValue() == null || result.getStringValue().length() == 0) {
                    System.err.printf("Invalid Asset json: %s\n", result.getStringValue());
                    continue;
                }
                Asset asset = Asset.deserialize(result.getStringValue());
                String owner = asset.getOwner();
                String ownerOrg = null;
                String[] parts = owner.split(",");
                for (String part : parts) {
                    if (part.trim().startsWith("O=")) {
                        String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                        String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                        String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                        ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
                    }
                }
                String temp = asset.getAssetID() + "-" + ownerOrg;
                queryResults = queryResults + "," + temp;
                // System.out.println("QueryResult: " + asset.toString());
            }
        }
        return queryResults;
    }


    /**
     * Creates a new asset on the ledger from asset properties passed in as transient map.
     * Asset owner will be inferred from the ClientId via stub api
     *
     * @param ctx the transaction context
     *            Transient map with asset_properties key with asset json as value
     * @return the created asset
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public Asset CreateAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_properties")) {
            String errorMessage = String.format("CreateAsset call must specify asset_properties in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_properties");
        final String assetID;
        final String pointer;
        final String dataSubject;
        final String filekey;
        List<String> acl;
        int version = 0;
        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            String jsonString = json.toString();
            System.out.printf("check01: ", jsonString);
            Map<String, Object> tMap = json.toMap();

            pointer = (String) tMap.get("pointer");
            filekey = (String) tMap.get("filekey");
            acl = (List<String>) tMap.get("acl");
            assetID = (String) tMap.get("assetID");
            dataSubject = (String) tMap.get("dataSubject");
            if (tMap.containsKey("version")) {
                version = (Integer) tMap.get("version");
            }
        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        //input validations
        String errorMessage = null;
        if (assetID.equals("")) {
            errorMessage = String.format("Empty input in Transient map: assetID");
        }
        if (pointer.equals("")) {
            errorMessage = String.format("Empty input in Transient map: pointer");
        }
        if (filekey.equals("")) {
            errorMessage = String.format("Empty input in Transient map: filekey");
        }
        if (acl.isEmpty()) {
            errorMessage = String.format("Empty input in Transient map: acl");
        }
        if (dataSubject.equals("")) {
            errorMessage = String.format("Empty input in Transient map: dataSubject");
        }
        if (version <= 0) {
            errorMessage = String.format("Wrong input in Transient map: version");
        }
        if (errorMessage != null) {
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        Asset asset = new Asset(pointer, assetID, dataSubject, version, "", filekey, acl);
        // Check if asset already exists
        byte[] assetJSON = ctx.getStub().getPrivateData(ASSET_COLLECTION_NAME, assetID);
        if (assetJSON != null && assetJSON.length > 0) {
            errorMessage = String.format("Asset %s already exists", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_ALREADY_EXISTS.toString());
        }

        // Get ID of submitting client identity
        String clientID = ctx.getClientIdentity().getId();
        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");

        // Patient is not allowed to create assets
        if ("patient".equals(clientRole)) {
            String errorMessageRole = String.format("Client with role %s is not authorized to create asset", clientRole);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessageRole, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // Verify that the client is submitting request to peer in their organization
        // This is to ensure that a client from another org doesn't attempt to read or
        // write private data from this peer.
        verifyClientOrgMatchesPeerOrg(ctx);

        // Make submitting client the owner
        asset.setOwner(clientID);
        System.out.printf("CreateAsset Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        System.out.printf("Put: collection %s, ID %s\n", ASSET_COLLECTION_NAME, new String(asset.serialize()));
        stub.putPrivateData(ASSET_COLLECTION_NAME, assetID, asset.serialize());

        // Get collection name for this organization.
        // String orgCollectionName = getCollectionName(ctx);

        // Record the asset creation on the public ledger
        String assetCreationRecord = String.format("Asset %s created by %s in %s at %s", assetID, clientMspId, ASSET_COLLECTION_NAME, Instant.now());
        String assetCreationRecordId = assetID + "_creation";
        stub.putStringState(assetCreationRecordId, assetCreationRecord);

        return asset;
    }

    /**
     * Deletes a asset & related details from the ledger.
     * Input in transient map: asset_delete
     *
     * This deletes the private data, but does not trigger an immediate cleanup
     * of the history. To specifically force removal right now use purge
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void DeleteAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_delete")) {
            String errorMessage = String.format("DeleteAsset call must specify 'asset_delete' in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_delete");
        final String assetID;

        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            assetID = json.getString("assetID");

        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        System.out.printf("DeleteAsset: verify asset %s exists\n", assetID);
        byte[] assetJSON = stub.getPrivateData(ASSET_COLLECTION_NAME, assetID);

        if (assetJSON == null || assetJSON.length == 0) {
            String errorMessage = String.format("Asset %s does not exist", assetID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.ASSET_NOT_FOUND.toString());
        }

        verifyClientOrgMatchesPeerOrg(ctx);

        Asset asset = Asset.deserialize(assetJSON);

        String clientMspId = ctx.getClientIdentity().getMSPID();
        String clientRole = ctx.getClientIdentity().getAttributeValue("role");
        String idName = extractId(ctx.getClientIdentity().getId());
        String dataSubject = asset.getDataSubject();

        if ("patient".equals(clientRole) && !idName.equals(dataSubject)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to delete asset %s of %s.", idName, clientRole, assetID, dataSubject);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // Extract the organization from the owner's certificate
        String owner = asset.getOwner();
        String ownerOrg = null;
        String[] parts = owner.split(",");
        for (String part : parts) {
            if (part.trim().startsWith("O=")) {
                String orgDomain = part.trim().substring(2); // This will give"org1.example.com"
                String orgName = orgDomain.split("\\.")[0]; // This will give "org1"
                String orgNameCapitalized = orgName.substring(0, 1).toUpperCase() + orgName.substring(1); // This will give "Org1"
                ownerOrg = orgNameCapitalized + "MSP"; // This will give "Org1MSP"
            }
        }

        // Check if the client is from the owner's organization
        if (!clientMspId.equals(ownerOrg)) {
            String errorMessage = String.format("Client %s with role %s is not authorized to delete asset from %s", clientMspId, clientRole, ownerOrg);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }

        // delete the key from asset collection
        System.out.printf("DeleteAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        stub.delPrivateData(ASSET_COLLECTION_NAME, assetID);

        // Record the asset creation on the public ledger
        String assetDeletionRecord = String.format("Asset %s of %s was deleted by %s with id %s from %s at %s", assetID, dataSubject, clientMspId, idName, ASSET_COLLECTION_NAME, Instant.now());
        String assetDeletionRecordId = assetID + "_deletion";
        stub.putStringState(assetDeletionRecordId, assetDeletionRecord);
    }

    /**
     * Purges the history of the asset from Private Data
     * (delete does not need to be called as well)
     * Input in transient map: asset_delete
     *
     * @param ctx the transaction context
     */
    @Transaction(intent = Transaction.TYPE.SUBMIT)
    public void PurgeAsset(final Context ctx) {
        ChaincodeStub stub = ctx.getStub();
        Map<String, byte[]> transientMap = ctx.getStub().getTransient();
        if (!transientMap.containsKey("asset_purge")) {
            String errorMessage = String.format("PurgeAsset call must specify 'asset_purge' in Transient map input");
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        byte[] transientAssetJSON = transientMap.get("asset_purge");
        final String assetID;

        try {
            JSONObject json = new JSONObject(new String(transientAssetJSON, UTF_8));
            assetID = json.getString("assetID");

        } catch (Exception err) {
            String errorMessage = String.format("TransientMap deserialized error: %s ", err);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INCOMPLETE_INPUT.toString());
        }

        // Note that there is no check here to see if the id exist; it might have been 'deleted' already
        // so a check here is pointless. We would need to call purge irrespective of the result
        // A delete can be called before purge, but is not essential

        verifyClientOrgMatchesPeerOrg(ctx);

        // delete the key from asset collection
        System.out.printf("PurgeAsset: collection %s, ID %s\n", ASSET_COLLECTION_NAME, assetID);
        stub.purgePrivateData(ASSET_COLLECTION_NAME, assetID);
    }

    private void verifyClientOrgMatchesPeerOrg(final Context ctx) {
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        String peerMSPID = ctx.getStub().getMspId();

        if (!peerMSPID.equals(clientMSPID)) {
            String errorMessage = String.format("Client from org %s is not authorized to read or write private data from an org %s peer", clientMSPID, peerMSPID);
            System.err.println(errorMessage);
            throw new ChaincodeException(errorMessage, AssetTransferErrors.INVALID_ACCESS.toString());
        }
    }

    private String getCollectionName(final Context ctx) {

        // Get the MSP ID of submitting client identity
        String clientMSPID = ctx.getClientIdentity().getMSPID();
        // Create the collection name
        return clientMSPID + "PrivateCollection";
    }

    private String extractId(final String input) {
        String id = "";
        Pattern pattern = Pattern.compile("CN=(.*?),");
        Matcher matcher = pattern.matcher(input);
        if (matcher.find()) {
            id = matcher.group(1);
        }
        return id;
    }

}
