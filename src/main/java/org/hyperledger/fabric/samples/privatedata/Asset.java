/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.hyperledger.fabric.samples.privatedata;

import java.util.Objects;

import static java.nio.charset.StandardCharsets.UTF_8;

import org.hyperledger.fabric.contract.annotation.DataType;
import org.hyperledger.fabric.contract.annotation.Property;

import org.hyperledger.fabric.shim.ChaincodeException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;



@DataType()
public final class Asset {

    @Property()
    private final String assetID;

    @Property()
    private final String pointer;

    @Property()
    private final String dataSubject;

    @Property()
    private int version;

    @Property()
    private String owner;

    @Property()
    private final String filekey;

    @Property()
    private List<String> acl;

    public String getAssetID() {
        return assetID;
    }

    public String getDataSubject() {
        return dataSubject;
    }

    public int getVersion() {
        return version;
    }

    public String getOwner() {
        return owner;
    }

    public String getPointer() {
        return pointer;
    }

    public String getFilekey() {
        return filekey;
    }

    public List<String> getAcl() {
        return acl;
    }

    public void setOwner(final String newowner) {
        owner = newowner;
    }

    public void addToAcl(final String newEntry) {
        acl.add(newEntry);
    }

    public void removeFromAcl(final String entry) {
        acl.remove(entry);
    }

    public Asset(final String pointer,
                 final String assetID, final String dataSubject,
                 final int version, final String owner,
                 final String filekey, final List<String> acl) {
        this.pointer = pointer;
        this.assetID = assetID;
        this.dataSubject = dataSubject;
        this.version = version;
        this.owner = owner;
        this.filekey = filekey;
        this.acl = acl;
    }

    public byte[] serialize() {
        String jsonStr = new JSONObject(this).toString();
        return jsonStr.getBytes(UTF_8);
    }

    public static Asset deserialize(final byte[] assetJSON) {
        return deserialize(new String(assetJSON, UTF_8));
    }

    public static Asset deserialize(final String assetJSON) {
        try {
            JSONObject json = new JSONObject(assetJSON);
            String jsonString = json.toString();
            System.out.printf("check1: ", jsonString);
            final String id = json.getString("assetID");
            System.out.printf("check2: ", id);
            final String pointer = json.getString("pointer");
            System.out.printf("check3: ", pointer);
            final String dataSubject = json.getString("dataSubject");
            final String owner = json.getString("owner");
            final int version = json.getInt("version");
            final String filekey = json.getString("filekey");
            final JSONArray aclJsonArray = json.getJSONArray("acl");
            List<String> acl = new ArrayList<>();
            for (int i = 0; i < aclJsonArray.length(); i++) {
                acl.add(aclJsonArray.getString(i));
            }
            return new Asset(pointer, id, dataSubject, version, owner, filekey, acl);
        } catch (Exception e) {
            throw new ChaincodeException("Deserialize error: " + e.getMessage(), "DATA_ERROR");
        }
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }

        if ((obj == null) || (getClass() != obj.getClass())) {
            return false;
        }

        Asset other = (Asset) obj;

        return Objects.deepEquals(
                new String[]{getAssetID(), getDataSubject(), getOwner()},
                new String[]{other.getAssetID(), other.getDataSubject(), other.getOwner()})
                &&
                Objects.deepEquals(
                        new int[]{getVersion()},
                        new int[]{other.getVersion()});
    }

    @Override
    public int hashCode() {
        return Objects.hash(getPointer(), getAssetID(), getDataSubject(), getVersion(), getOwner(), getFilekey(), getAcl());
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "@" + Integer.toHexString(hashCode())
                + " [assetID=" + assetID + ", dataSubject="
                + dataSubject + ", version=" + version + ", owner=" + owner + ", filekey=" + filekey + ", acl=" + acl + "]";
    }


}
