#!/bin/bash

retrieved_file_name=$1
file_key=$2
ipfs_hash=$3
cfragString=$4
pub_key_string=$5

# Set up a second IPFS node (only for the experimental purpose in the)
# Set default paths for the second IPFS node
ipfs_staging2=${PWD}/ipfs_node2/staging2
ipfs_data2=${PWD}/ipfs_node2/storage2

# Retrieve the file inside the IPFS container
docker exec ipfs_host2 ipfs get $ipfs_hash -o /export/$retrieved_file_name
# Copy the file from the IPFS container to the host machine
docker cp ipfs_host2:/export/$retrieved_file_name ./downloads

# Ask the user for the path to the private key PEM file
echo "Enter the path to the private key PEM file:"
read private_key_pem_path

# Call the Python script to decrypt the file
python3 encryption.py decrypt_capsule "$private_key_pem_path" "$pub_key_string" "$file_key" "$cfragString" "$retrieved_file_name" 