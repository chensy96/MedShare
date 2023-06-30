#!/bin/bash

# Ask the user for the file path
echo "Enter the path to the file you want to encrypt:"
read file_path

# Ask the user for the path to the public key PEM file
echo "Enter the path to the public key PEM file:"
read public_key_pem_path

file_encrypt_output=$(python3 encryption.py encrypt_file "$public_key_pem_path" "$file_path")

# Extract the key from the output
# encrypted_file_key=$(echo "$output" | grep -oP '(?<=Encryption key: ).*')
# encrypted_file_path=$(echo "$output" | grep -oP '(?<=Encrypted file path: ).*')
encrypted_file_key=$(echo "$file_encrypt_output" | grep -oP 'Capsule: \K[^,]*')
encrypted_file_path=$(echo "$file_encrypt_output" | grep -oP 'Encrypted file path: \K[^,]*')

echo "The encrypted_file_key is: $encrypted_file_key"
echo "The encrypted_file_path is: $encrypted_file_path"

# IPFS set up
# Set default paths
ipfs_staging=${PWD}/ipfs/staging
ipfs_data=${PWD}/ipfs/storage
# upload encrypted file to IPFS
# first copy the file into the staging folder
cp -r $encrypted_file_path $ipfs_staging
# Extract the filename from the path
encrypted_file_name=$(basename "$encrypted_file_path")
sleep 5
# Add the file to IPFS on the first node and capture the output
output1=$(docker exec ipfs_host ipfs add "/export/$encrypted_file_name")
# Extract the IPFS hash from the output
ipfs_hash=$(echo "$output1" | awk '{print $2}')

# Print the IPFS hash
# file_name="${file_path%.*}"
echo "The file_name is: $file_path"
echo "The IPFS hash is: $ipfs_hash"
echo "The encrypted file key is: $encrypted_file_key"
