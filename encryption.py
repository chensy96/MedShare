from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.asymmetric import padding
from umbral import SecretKey, Signer, PublicKey, SecretKeyFactory
from umbral import encrypt, Capsule, decrypt_original, generate_kfrags, decrypt_reencrypted, KeyFrag, reencrypt, VerifiedCapsuleFrag
import time
import sys
import base64

def generate_keypair():
    # Generate key pair 1
    private_key = SecretKey.random()
    public_key = private_key.public_key()
    # Serialize the keys to PEM format
    private_pem = private_key.to_secret_bytes()
    public_pem = bytes(public_key)
    # Write the keys to files
    with open('private_key1.pem', 'wb') as f:
        f.write(private_pem)
    with open('public_key1.pem', 'wb') as f:
        f.write(public_pem)

    # Generate key pair 2
    private_key2 = SecretKey.random()
    public_key2 = private_key2.public_key()
    # Serialize the keys to PEM format
    private_pem2 = private_key2.to_secret_bytes()
    public_pem2 = bytes(public_key2)
    # Write the keys to files
    with open('private_key2.pem', 'wb') as f:
        f.write(private_pem2)
    with open('public_key2.pem', 'wb') as f:
        f.write(public_pem2)

    # Generate key pair 3
    private_key3 = SecretKey.random()
    public_key3 = private_key3.public_key()
    # Serialize the keys to PEM format
    private_pem3 = private_key3.to_secret_bytes()
    public_pem3 = bytes(public_key3)
    # Write the keys to files
    with open('private_key3.pem', 'wb') as f:
        f.write(private_pem3)
    with open('public_key3.pem', 'wb') as f:
        f.write(public_pem3)

    # Get the current timestamp
    timestamp = int(time.time())
    return timestamp

def generate_signers():
    # Generate a key pair for Org1
    signing_key  = SecretKey.random()
    verifying_key = signing_key.public_key()
    # Serialize the keys to PEM format
    signing_pem = signing_key.to_secret_bytes()
    verifying_pem = bytes(verifying_key)
    # Write the keys to files
    with open('signing_key1.pem', 'wb') as f:
        f.write(signing_pem)
    with open('verifying_key1.pem', 'wb') as f:
        f.write(verifying_pem)

    # Generate a key pair for Org2
    signing_key2  = SecretKey.random()
    verifying_key2 = signing_key2.public_key()
    # Serialize the keys to PEM format
    signing_pem2 = signing_key2.to_secret_bytes()
    verifying_pem2 = bytes(verifying_key2)
    # Write the keys to files
    with open('signing_key2.pem', 'wb') as f:
        f.write(signing_pem2)
    with open('verifying_key2.pem', 'wb') as f:
        f.write(verifying_pem2)

    # Generate a key pair for Org3
    signing_key3  = SecretKey.random()
    verifying_key3 = signing_key3.public_key()
    # Serialize the keys to PEM format
    signing_pem3 = signing_key3.to_secret_bytes()
    verifying_pem3 = bytes(verifying_key3)
    # Write the keys to files
    with open('signing_key3.pem', 'wb') as f:
        f.write(signing_pem3)
    with open('verifying_key3.pem', 'wb') as f:
        f.write(verifying_pem3)

def generate_pubkey_string(pubkey_path):
    with open(pubkey_path, 'rb') as f:
            pubkey_pem = f.read()
    pubkey_str = base64.b64encode(pubkey_pem).decode()
    return pubkey_str

def deserialize_private_key(private_key_pem):
    private_key = SecretKey.from_bytes(private_key_pem) 
    return private_key

def deserialize_public_key(public_key_pem):
    public_key = PublicKey.from_bytes(public_key_pem)
    return public_key

def encrypt_file(public_key_pem, file_path):
    # Read the file 
    with open(file_path, 'rb') as file:
        data = file.read()
    public_key = deserialize_public_key(public_key_pem)
    capsule, ciphertext = encrypt(public_key, data)
    # Write the encrypted data to a new file
    encrypted_file_path = file_path + "_encrypted"
    with open(encrypted_file_path, 'wb') as f:
        f.write(ciphertext) 
    serialized_capsule = bytes(capsule)
    capsuleString = base64.b64encode(serialized_capsule).decode()
    # print(f"Encryption successful. Encrypted file saved at {encrypted_file_path}, capsule: {capsuleString}")
    return capsuleString, encrypted_file_path

def split_key(private_key_pem1, public_key_pem2, signing_key1):
    serialized_public_key2 = base64.b64decode(public_key_pem2.encode())
    private_key1 = deserialize_private_key(private_key_pem1)
    public_key2 = deserialize_public_key(serialized_public_key2)
    signing_key1 = deserialize_private_key(signing_key1)
    signer = Signer(signing_key1)
    kfrags = generate_kfrags(delegating_sk=private_key1,
                            receiving_pk=public_key2,
                            signer=signer,
                            threshold=1,
                            shares=2,
                            sign_delegating_key=False,
                            sign_receiving_key=False)
    serialized_kfrag1 = bytes(kfrags[0])
    kFragString1 = base64.b64encode(serialized_kfrag1).decode()
    serialized_kfrag2 = bytes(kfrags[1])
    kFragString2 = base64.b64encode(serialized_kfrag2).decode()
    kFragStrings = []
    kFragStrings.append(kFragString1)
    kFragStrings.append(kFragString2)
    print("kfrags are: ", kfrags, serialized_kfrag1, kFragString1, kFragString2)
    kFragStrings = ','.join(kFragStrings)
    return kFragStrings

# performed inside server
def re_encrypt(kfrag_str, capsuleString,verifying_key_pem):
    verifying_key = PublicKey.from_bytes(verifying_key_pem)
    serialized_kfrag = base64.b64decode(kfrag_str.encode())
    kfrag = KeyFrag.from_bytes(serialized_kfrag)
    verified_kfrag = kfrag.verify(verifying_key)
    print(serialized_kfrag, kfrag, verified_kfrag)

    serialized_capsule = base64.b64decode(capsuleString.encode())
    capsule = Capsule.from_bytes(serialized_capsule)

    cfrag = reencrypt(capsule=capsule, kfrag=verified_kfrag)
    # Serialize the cfrag to bytes
    serialized_cfrag = bytes(cfrag)
    cfragString1 = base64.b64encode(serialized_cfrag).decode()
    print(cfrag, serialized_cfrag, cfragString1)

def decrypt_capsule(private_key_pem2, publickeyString, capsuleString, cfragString, encrypted_file):
    private_key2 = deserialize_private_key(private_key_pem2)
    serialized_publickey = base64.b64decode(publickeyString.encode())
    public_key1 = deserialize_public_key(serialized_publickey)

    serialized_cfrag = base64.b64decode(cfragString.encode())
    # Deserialize the cfrag(only 1 in the prototype)
    cfrag = VerifiedCapsuleFrag.from_verified_bytes(serialized_cfrag)
    cfrags = [cfrag]
    serialized_capsule = base64.b64decode(capsuleString.encode())
    capsule = Capsule.from_bytes(serialized_capsule)

    file_path = './downloads/' + encrypted_file
    with open(file_path, 'rb') as file:
        cipher_data = file.read()
    cleartext = decrypt_reencrypted(receiving_sk=private_key2,
                                    delegating_pk=public_key1,
                                    capsule=capsule,
                                    verified_cfrags=cfrags,
                                    ciphertext=cipher_data)
    decrypted_file_path = encrypted_file + '_decrypted'
    with open(decrypted_file_path, 'wb') as file:
        file.write(cleartext)
    print(f"Decryption successful. Decrypted file saved at {decrypted_file_path}")

def decrypt_file(private_key_pem, encrypted_file, capsuleString):
    private_key = deserialize_private_key(private_key_pem)
    serialized_capsule = base64.b64decode(capsuleString.encode())
    capsule = Capsule.from_bytes(serialized_capsule)

    # Read the file 
    file_path = './downloads/' + encrypted_file
    with open(file_path, 'rb') as file:
        cipher_data = file.read()

    # Use the key to decrypt the file
    cleartext = decrypt_original(private_key, capsule, cipher_data)
    # Write the decrypted data to a new file
    decrypted_file_path = file_path + '_decrypted'
    with open(decrypted_file_path, 'wb') as file:
        file.write(cleartext)
    print(f"Decryption successful. Decrypted file saved at {decrypted_file_path}")

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 key_encryption.py {generate|deserialize_private|deserialize_public|encrypt|decrypt} [public_key_pem/private_key_pem/file_key/encrypted_file_key]")
        sys.exit(1)
    command = sys.argv[1]
    if command == "generate":
        timestamp = generate_keypair()
        print(f"Key pair generated. Timestamp: {timestamp}")
    elif command == "generate_signers":
        generate_signers()
        print(f"signer generated")
    elif command == "re_encrypt":
        kfrag = sys.argv[2]
        capsule = sys.argv[3]
        with open(sys.argv[4], 'rb') as f:
            verifying_key_pem = f.read()
        re_encrypt(kfrag, capsule, verifying_key_pem)
    elif command == "encrypt_file":
        if len(sys.argv) != 4:
            print("Usage: python3 encryption.py public_key_pem file_path ")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            public_key_pem = f.read()
        file_path = sys.argv[3]
        capsuleString, encrypted_file_path = encrypt_file(public_key_pem, file_path)
        print(f"Capsule: {capsuleString}, Encrypted file path: {encrypted_file_path},")
        # sys.stdout.write(f"{capsuleString}\n{encrypted_file_path}\n")
    elif command == "decrypt_file":
        if len(sys.argv) != 5:
            print("Usage: python3 key_encryption.py decrypt_file private_key_pem file_path capsuleString")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            private_key_pem = f.read()
        file_path = sys.argv[3]
        capsuleString = sys.argv[4]
        decrypt_file(private_key_pem, file_path, capsuleString)
    elif command == "decrypt_capsule":
        if len(sys.argv) != 7:
            print("Usage: python3 key_encryption.py decrypt_capsule private_key_pem2, public_key_pem1, capsuleString, serialized_cfrag, encrypted_file")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            private_key_pem = f.read()
        publickeyString = sys.argv[3]
        capsuleString = sys.argv[4]
        cfragString = sys.argv[5]
        file_path = sys.argv[6]
        decrypt_capsule(private_key_pem, publickeyString, capsuleString, cfragString, file_path)
    elif command == "split_key":
        if len(sys.argv) != 5:
            print("Usage: python3 key_encryption.py split_key private_key_pem1, public_key_pem2, signing_key1")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            private_key_pem = f.read()
        public_key_string = sys.argv[3]
        with open(sys.argv[4], 'rb') as f:
            signing_key = f.read()
        kFragStrings = split_key(private_key_pem, public_key_string, signing_key)
        # print(f"kFragStrings: {kFragStrings[0]}")
        print(f"kFragStrings: {kFragStrings}" + ";")
    elif command == "deserialize_private":
        if len(sys.argv) != 3:
            print("Usage: python3 key_encryption.py deserialize_private private_key_pem")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            private_key_pem = f.read()
        private_key = deserialize_private_key(private_key_pem)
        print(f"Private key deserialized: {private_key}")
    elif command == "deserialize_public":
        if len(sys.argv) != 3:
            print("Usage: python3 key_encryption.py deserialize_public public_key_pem")
            sys.exit(1)
        with open(sys.argv[2], 'rb') as f:
            public_key_pem = f.read()
        public_key = deserialize_public_key(public_key_pem)
        print(f"Public key deserialized: {public_key}")
    elif command == "generate_pubkey_string":
        if len(sys.argv) != 3:
            print("Usage: python3 key_encryption.py generate_pubkey_string public_key_pem")
            sys.exit(1)
        public_key_pem = sys.argv[2]
        pubkey_string = generate_pubkey_string(public_key_pem)
        print(f"pubkey_string: {pubkey_string}")
    else:
        print(f"Unknown command: {command}")
        sys.exit(1)

if __name__ == "__main__":
    main()
