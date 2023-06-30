#!/bin/bash

# Asymmetric key pair set up
echo "Generating a new pair of asymmetric keys for your organization..."
# Call the Python script to generate a key pair
output=$(python3 key_encryption.py generate)

# Print the output
echo "$output"

# IPFS set up
# Set default paths
ipfs_staging=${PWD}/ipfs/staging
ipfs_data=${PWD}/ipfs/storage

# Ask the user if they want to use their own paths
echo "The default path for IPFS staging is $ipfs_staging. Would you like to use a different path? (yes/no)"
read answer
if [ "$answer" == "yes" ]; then
    echo "Enter the path for IPFS staging:"
    read ipfs_staging
fi

echo "The default path for IPFS storage is $ipfs_data. Would you like to use a different path? (yes/no)"
read answer
if [ "$answer" == "yes" ]; then
    echo "Enter the path for IPFS storage:"
    read ipfs_data
fi

# Run the Docker command
docker run -d --name ipfs_host -v $ipfs_staging:/export -v $ipfs_data:/data/ipfs -p 4001:4001 -p 4001:4001/udp -p 127.0.0.1:8080:8080 -p 127.0.0.1:5001:5001 ipfs/go-ipfs:latest

# Set up a second IPFS node (only for the experimental purpose in the)
# Set default paths for the second IPFS node
ipfs_staging2=${PWD}/ipfs_node2/staging2
ipfs_data2=${PWD}/ipfs_node2/storage2

# Ask the user if they want to use their own paths for the second IPFS node
echo "The default path for the second IPFS staging is $ipfs_staging2. Would you like to use a different path? (yes/no)"
read answer
if [ "$answer" == "yes" ]; then
    echo "Enter the path for the second IPFS staging:"
    read ipfs_staging2
fi

echo "The default path for the second IPFS storage is $ipfs_data2. Would you like to use a different path? (yes/no)"
read answer
if [ "$answer" == "yes" ]; then
    echo "Enter the path for the second IPFS storage:"
    read ipfs_data2
fi

# Run the Docker command for the second IPFS node
docker run -d --name ipfs_host2 -e IPFS_PROFILE=server -v $ipfs_staging2:/export -v $ipfs_data2:/data/ipfs -p 4101:4001 -p 4101:4001/udp -p 127.0.0.1:8180:8080 -p 127.0.0.1:5101:5001 ipfs/go-ipfs:latest

# get node 1's multi address to connect to node 2 to speed up the communication.
# only valid in the prototype, in the production network, it needs to be replaced by the ip address of one hoster.
# Get the ID of the IPFS node
ID=$(docker exec ipfs_host ipfs id -f="<id>\n")
# Get the JSON output from the 'ipfs id' command
JSON=$(docker exec ipfs_host ipfs id)
# Use 'jq' to parse the JSON and get the first address
ADDRESS=$(echo $JSON | jq -r '.Addresses[0]')
# Connect the second IPFS node to the first
docker exec ipfs_host2 ipfs swarm connect /ip4/${ADDRESS}/tcp/4001/p2p/${ID}
