# MedShare
Repository of my thesis project, MedShare, a blockchain-based distributed file share and management system for medical data. 

## Notice
This repository only covers the codes that interact with the Hyperledger Fabric network, including codes for smart contracts(chain codes), file encryption, and server but does not include the network itself. The network used in this prototype is built upon the Hyperledger test network, including 3 organizations hosted in docker-compose containers, with specific configurations that suit the use case.

# Installation Guide
- The system was built and tested only in **Linux** enviornment. 
# 
## Setting Up Hyperledger Fabric

Ensure you have Hyperledger Fabric v2.5 installed and running for this project. The platform operates within Docker containers. Follow the steps below to set up your environment.

### Prerequisites

- **Docker:** Ensure Docker is installed and running on your machine. If not, you can download it from [Docker's official website](https://www.docker.com/get-started).

- **Hyperledger Fabric v2.5:** Follow the steps below to install the Fabric on your machine:

### Installation Steps

1. **Environment Setup:**
   - Visit [Hyperledger Fabric Prerequisites](https://hyperledger-fabric.readthedocs.io/en/latest/prereqs.html) to set up the environment.

2. **Install Fabric and Fabric Samples:**
   - Follow the instructions on [Installing Hyperledger Fabric](https://hyperledger-fabric.readthedocs.io/en/latest/install.html) to get Fabric and Fabric samples installed.

### Directory Structure

Ensure the "fabric-samples" folder is installed parallel to this project's repository. The relative path should be `"../fabric-samples"`.

### Folder Structure

```plaintext
parent-folder
│
├── your-project-folder
│   ├── ...
│
└── fabric-samples
    ├── ...
```

## Setting Up Two Local Kubo IPFS Nodes

### Prerequisites

Ensure that you have Go installed on your machine. If not, you can download and install it from [the official Go website](https://golang.org/).

### Step 1: Install Kubo

- Follow the official instructions on [Install official IPFS distributions](https://docs.ipfs.tech/install/command-line/#system-requirements) to get IPFS Kubo (a Go-based IPFS implementation) installed on your machine.

### Step 2: Initialize the First IPFS Node
Make sure you are in the MedShare repository, then create a new directory for the first IPFS node and initialize it:

```bash
mkdir -p storage/.ipfs1
export IPFS_PATH=./storage/.ipfs1
ipfs init
```

### Step 3: Initialize the Second IPFS Node

Create a new directory for the second IPFS node and initialize it:

```bash
mkdir -p storage/.ipfs2
export IPFS_PATH=./storage/.ipfs2
ipfs init
```

### Step 4: Start the First IPFS Node

In the first terminal, start the first IPFS node:

```bash
export IPFS_PATH=./storage/.ipfs1
ipfs daemon
```

### Step 5: Start the Second IPFS Node

Open a second terminal, navigate to the same directory, and start the second IPFS node:

```bash
cd path/to/your/storage/directory
export IPFS_PATH=./storage/.ipfs2
ipfs daemon
```

### Step 6: Find the Multiaddresses

In each terminal, you should see an output similar to this when the node starts:

```bash
API server listening on /ip4/127.0.0.1/tcp/5001
Gateway (readonly) server listening on /ip4/127.0.0.1/tcp/8080
Daemon is ready
```

Note down the API server addresses; you will need them to connect the nodes.

### Step 7: Connect the Nodes

Use the `swarm connect` command to connect the nodes. Replace `<node2-multiaddr>` with the API server address of the second node:

```bash
ipfs swarm connect <node2-multiaddr>
```

### Verification

Verify that the nodes are connected:

```bash
ipfs swarm peers
```

This should display the multiaddress of the other node, confirming that they are connected.

Now, you have two local Kubo IPFS nodes running and connected. You can start adding and retrieving files on either node. Make sure to replace `<node2-multiaddr>` with the actual multiaddress of your second IPFS node.

## Setting Up MedShare Components
- In this section, The following setups needed to be done: 1. Create the PRE server in Docker 2. Create mock users and channels in the blockchain network 3. Install the chaincodes on the blockchain nodes. 4. Create organization keys. The file 'system_setup.sh' will help to automatically accompanish these tasks, but might need manual changes to fit different systems and enviornments.
- Make sure to give execute permission to your script file using ```bashchmod +x system_setup.sh``` and then you can run it using ```bash./system_setup.sh```.
 
### Run Set Up Script

```bash
./system_setup.sh
```
if no error is displayed, you should see a container called _'pre_server'_ in your Docker, peer nodes in the Blockchain compose, chaincodes containers outside of the compose, and pairs of keys generated in the repository. The _private_key1.pem_ and _public_key1.pem_ are asymmetric key pairs for Org1 and the _private_key2.pem_ and _public_key2.pem_ are for Org 2. In addition, a pair of sigining and verification key was also generated for each organization. For example, _'verifying_key1.pem'_ is for Org1.

### Error Handling
In case of errors within running the scripts or needs of customazation, you can refer to the offcial guide https://hyperledger-fabric.readthedocs.io/en/release-2.5/deploy_chaincode.html on deploying chaincodes to your network. The chaincode of this work is included in the folder _'chaincode-java'_ as the source codes, which can be altered and re-build, and the ready-to-be-installed version is also included as _'medcare.tar.gt'_ for the newest version with indexes, and 'medshare.tar.gz' for the old version.

## Running the System
Assuming all the former steps were successful, now you can try to run the application from terminal.

### Initialization
- Start your Docker, make sure the compose is running.
- Start the Proxy Re-encryption Server:
```bash
docker start -a pre_server
```

### Step 1: Start the Application
```bash
./run_app.sh
```

Input the user information as needed. If you do not know what to input, use the default values:
- Please enter the organization (ORG): org1
- Please enter the channel name (CHANNEL_NAME): mako
- Please enter the chaincode name (CHAINCODE_NAME): medcare
- Please enter the user (USER): owner

### Step 2: Uploading Public Keys to the network.
For each organiaztion to be fully functional, you need to use the function "UploadKey" in the menu to share this organization's public key and verification key on the ledger. For example, for Org2, input _'verifying_key2.pem'_ for verification key and _'public_key2.pem'_ for public key. 

### Step 3: Manage Files
Now you can choose the functions from the menu which you want to use. For switching user, you have to quite the process and re-run the script.
- NOTE: for all the files you need to give to the CLI, input the fullname with the file ending. For example, for uploading a file named "PHI.txt", input "PHI.txt", not "PHI".
- For creating ACL, the permitted organiations need to be seperated by commas and pointing to their MSP. For example, to allow org1, org2 and org3 to all be able to access an asset, you need to input exactly "**Org1MSP,Org2MSP,Org3MSP**" for **ACL**.
- If you have other problems with commands, you can follow the video 'medshare_walkthrough'.
