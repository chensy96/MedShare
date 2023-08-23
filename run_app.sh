#!/bin/bash

# This script runs the App.java application.

# Navigate to the directory containing the gradle build file
# cd ./

# Ask the user to set the environment variables
read -p "Please enter the organization (ORG): " ORG
export ORG

read -p "Please enter the channel name (CHANNEL_NAME): " CHANNEL_NAME
export CHANNEL_NAME

read -p "Please enter the chaincode name (CHAINCODE_NAME): " CHAINCODE_NAME
export CHAINCODE_NAME

read -p "Please enter the user (USER): " USER
export USER

# Build the project
# ./application-gateway-java/gradlew build
cd ./application-gateway-java
./gradlew build
cd ..

while true; do
    # Display the possible operations
    echo "Possible operations:"
    echo "0. RetrieveFile (and read asset)"
    echo "1. CreateAsset (and upload file)"
    echo "2. ReadAsset"
    echo "3. ReadAcl"
    echo "4. RequestPermission"
    echo "5. GetHistoryForAsset"
    echo "6. UpdateAclPermission"
    echo "7. RevokeAclPermission"
    echo "8. QueryAssetByPatient"
    echo "9. DeleteAsset"
    echo "10. EraseDataRequest"
    echo "11. CreateAsset_mock"
    echo "12. RetrieveOwnFile"
    echo "13. UploadKey"

    # Ask the user to choose an operation
    read -p "Please enter the number of the operation you want to perform: " OPERATION_NUMBER

    # Set the operation and the required arguments based on the user's choice
    case $OPERATION_NUMBER in
        0)
            OPERATION="RetrieveFile"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        1)
            OPERATION="CreateAsset"
            # upload_results=$(./file_upload.sh)
            upload_results=$(source ./file_upload.sh | tee /dev/fd/2)
            assetID=$(echo "$upload_results" | grep -oP '(?<=The file_name is: ).*')
            pointer=$(echo "$upload_results" | grep -oP '(?<=The IPFS hash is: ).*')         
            read -p "Please enter the dataSubject: " ARG3
            read -p "Please enter the version: " ARG4
            filekey=$(echo "$upload_results" | grep -oP '(?<=The encrypted file key is: ).*')
            read -p "Please specify the acl (comma-separated): " ARG6
            echo "assetID:$assetID pointer:$pointer dataSubject:$ARG3 version:$ARG4 filekey:$filekey acl:$ARG6"
            ARGS="$assetID $pointer $ARG3 $ARG4 $filekey $ARG6"
            ;;
        2)
            OPERATION="ReadAsset"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        3)
            OPERATION="ReadAcl"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        4)
            OPERATION="RequestPermission"
            read -p "Please enter the assetID: " ARG1
            read -p "Please enter your purpose: " ARG2
            ARGS="$ARG1 $ARG2"
            ;;
        5)
            OPERATION="GetHistoryForAsset"
            read -p "Please enter the assetID with the corresponding tail (_creation _read _acl _deletion _erasure _request): " ARG1
            ARGS="$ARG1"
            ;;
        6)
            OPERATION="UpdateAclPermission"
            read -p "Please enter the assetID: " ARG1
            read -p "Please enter the newOrg: " ARG2
            ARGS="$ARG1 $ARG2"
            ;;
        7)
            OPERATION="RevokeAclPermission"
            read -p "Please enter the assetID: " ARG1
            read -p "Please enter the targetOrg: " ARG2
            ARGS="$ARG1 $ARG2"
            ;;
        8)
            OPERATION="QueryAssetByPatient"
            read -p "Please enter the data subject ID: " ARG1
            ARGS="$ARG1"
            ;;
        9)
            OPERATION="DeleteAsset"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        10)
            OPERATION="eraseDataRequest"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        11)
            OPERATION="CreateAsset_mock"
            read -p "Please enter the assetID: " ARG1
            read -p "Please enter the pointer: " ARG2
            read -p "Please enter the dataSubject: " ARG3
            read -p "Please enter the version: " ARG4
            read -p "Please enter the filekey: " ARG5
            read -p "Please enter the acl (comma-separated): " ARG6
            ARGS="$ARG1 $ARG2 $ARG3 $ARG4 $ARG5 $ARG6"
            ;;
        12)
            OPERATION="RetrieveOwnFile"
            read -p "Please enter the assetID: " ARG1
            ARGS="$ARG1"
            ;;
        13)
            OPERATION="UploadKey"
            echo "1. Public Key"
            echo "2. Verification Key"
            read -p "Please choose the type of key " KEY_OPTION   
            case $KEY_OPTION in
                1)
                    key_type=public_key    
                    ;;
                2)
                    key_type=verification_key    
                    ;;
            esac
            read -p "Please enter the key string: " ARG2
            ARGS="$key_type $ARG2"
            ;;
        *)
            echo "Invalid operation. Please choose a number between 1 and 4."
            # exit 1
            continue
            ;;
    esac
    # Run the application
    cd ./application-gateway-java
    if [[ $OPERATION == "RetrieveFile" ]]; then
        file_info=$(./gradlew run --args="$OPERATION $ARGS" | grep "^\*\*\* Result:")  
        file_assetId=$(echo "$file_info" | grep -oP 'Asset ID: \K[^,]*')
        file_fileKey=$(echo "$file_info" | grep -oP 'File Key: \K[^,]*')
        file_pointer=$(echo "$file_info" | grep -oP 'Pointer: \K[^,]*')
        file_owner=$(echo "$file_info" | awk -F'O=' '{print $3}' | awk -F'.' '{print $1}')

        MSP_ID="$(echo ${file_owner:0:1} | tr '[:lower:]' '[:upper:]')${file_owner:1}MSP"
        query="${MSP_ID}_verification_key"
        OPERATION2="GetHistoryForAsset"

        verification_key_output=$(./gradlew run --args="$OPERATION2 $query")
        verification_key_string=$(echo "$verification_key_output" | grep -oP 'Result: \K[^,]*')

        #temp until change the server database
        query2="${MSP_ID}_public_key"
        pub_key_output=$(./gradlew run --args="$OPERATION2 $query2")
        pub_key_string=$(echo "$pub_key_output" | grep -oP 'Result: \K[^,]*')
        cd ..  
        requestor_ID="$(echo ${ORG:0:1} | tr '[:lower:]' '[:upper:]')${ORG:1}MSP"
        response=$(curl -s -X POST -H "Content-Type: application/json" -d "{\"file_id\":\"$file_assetId\", \"requestor_id\":\"$requestor_ID\", \"capsule\":\"$file_fileKey\", \"verifying_key\":\"$verification_key_string\"}" http://localhost:5000/re_encrypt)
        cfrag=$(echo $response | jq -r '.cfrag')
        ./file_retrieval.sh $file_assetId $file_fileKey $file_pointer $cfrag $pub_key_string
        #  | tee /dev/fd/2
        # have the file downloaded
    elif [[ $OPERATION == "UpdateAclPermission" ]]; then
        result=$(./gradlew run --args="$OPERATION $ARGS") 
        if [[ $result == *"Failed"* ]]; then
            cd ..
            echo "You are not authorized to grant permission for this file."
        else
            cd ..
            # Ask the user for the path to the public key PEM file
            echo "Successfully updated acl list, now generating re-encryption keys for PRE servers."
            echo "Enter the path to your signing key:"
            read signing_key_path
            echo "Enter the path to your private key:"
            read private_key_path
            # retrieve pubkeys of orgs on acl
            OPERATION2="GetHistoryForAsset"
            ID=$ARG2
            assetID=$ARG1
            extracted_id=${ID:0:-3}
            extracted_id=$(echo "$extracted_id" | tr '[:upper:]' '[:lower:]')
            echo "Generating PRE key for organization $extracted_id."
            query="${ID}_public_key"
            cd ./application-gateway-java
            public_key_output=$(./gradlew run --args="$OPERATION2 $query")
            public_key_string=$(echo "$public_key_output" | grep -oP 'Result: \K[^,]*')
            cd .. 
            # generate re-encryption key fragments and send them to PRE servers
            kFragStrings=$(python3 encryption.py split_key "$private_key_path" "$public_key_string" "$signing_key_path")
            # kFrag1=$(echo "$kFragStrings" | grep -oP 'kFragStrings: \K[^,]*')
            kFrags=$(echo "$kFragStrings" | grep -oP 'kFragStrings: \K[^;]*')
            kFrag1=$(echo "$kFrags" | tr ',' '\n' | sed -n '1p')
            kFrag2=$(echo "$kFrags" | tr ',' '\n' | sed -n '2p')
            # sending to the server
            curl -X POST -H "Content-Type: application/json" -d "{\"file_id\":\"$assetID\", \"requestor_id\":\"$ID\", \"reencryption_key\":\"$kFrag1\"}" http://localhost:5000/store_key
        fi
    elif [[ $OPERATION == "RevokeAclPermission" ]]; then
        result=$(./gradlew run --args="$OPERATION $ARGS")  
        if [[ $result == *"Failed"* ]]; then
            cd ..
            echo "You are not authorized to revoke permission for this file."
        else
            cd ..
            ID=$ARG2
            assetID=$ARG1
            # sending deletion request to the server
            curl -X POST -H "Content-Type: application/json" -d "{\"file_id\":\"$assetID\", \"requestor_id\":\"$ID\"}" http://localhost:5000/delete_key
        fi
    elif [[ $OPERATION == "RetrieveOwnFile" ]]; then
        file_info=$(./gradlew run --args="$OPERATION $ARGS" | grep "^\*\*\* Result:")  
        file_assetId=$(echo "$file_info" | grep -oP 'Asset ID: \K[^,]*')
        file_fileKey=$(echo "$file_info" | grep -oP 'File Key: \K[^,]*')
        file_pointer=$(echo "$file_info" | grep -oP 'Pointer: \K[^,]*')
        cd ..  
        ./own_file_retrieval.sh $file_assetId $file_fileKey $file_pointer | tee /dev/fd/2
    elif [[ $OPERATION == "CreateAsset" ]]; then
        ./gradlew run --args="$OPERATION $ARGS"
        cd ..
        # Ask the user for the path to the public key PEM file
        echo "Successfully uploaded file and created asset, now generating re-encryption keys for PRE servers."
        echo "Enter the path to the signing key:"
        read signing_key_path
        echo "Enter the path to the private key:"
        read private_key_path
        # retrieve pubkeys of orgs on acl
        OPERATION2="GetHistoryForAsset"
        # convert acl to array
        IFS=',' read -ra ID_ARRAY <<< "$ARG6"
        # Loop through the acl
        for ID in "${ID_ARRAY[@]}"; do
            extracted_id=${ID:0:-3}
            extracted_id=$(echo "$extracted_id" | tr '[:upper:]' '[:lower:]')
            if [ "$extracted_id" == "$ORG" ]; then
                echo "Skipping the owner organization."
            else
                echo "Generating PRE key for organization $extracted_id."
                query="${ID}_public_key"
                cd ./application-gateway-java
                public_key_output=$(./gradlew run --args="$OPERATION2 $query")
                public_key_string=$(echo "$public_key_output" | grep -oP 'Result: \K[^,]*')
                cd .. 
                # generate re-encryption key fragments and send them to PRE servers
                kFragStrings=$(python3 encryption.py split_key "$private_key_path" "$public_key_string" "$signing_key_path")
                # kFrag1=$(echo "$kFragStrings" | grep -oP 'kFragStrings: \K[^,]*')
                kFrags=$(echo "$kFragStrings" | grep -oP 'kFragStrings: \K[^;]*')
                kFrag1=$(echo "$kFrags" | tr ',' '\n' | sed -n '1p')
                kFrag2=$(echo "$kFrags" | tr ',' '\n' | sed -n '2p')
                # echo "sending kFrag1: $kFrag1"
                # echo "sending kFrag2: $kFrag2"
                # sending to the server
                curl -X POST -H "Content-Type: application/json" -d "{\"file_id\":\"$assetID\", \"requestor_id\":\"$ID\", \"reencryption_key\":\"$kFrag1\"}" http://localhost:5000/store_key
            fi
        done
    else
        ./gradlew run --args="$OPERATION $ARGS"
        cd ..
    fi
done