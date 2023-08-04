from flask import Flask, request, jsonify
from flask_sqlalchemy import SQLAlchemy
from datetime import datetime
from umbral import reencrypt, KeyFrag, PublicKey, Capsule
import base64

app = Flask(__name__)
app.config['SQLALCHEMY_DATABASE_URI'] = 'sqlite:////tmp/test.db'
db = SQLAlchemy(app)

class ReencryptionKey(db.Model):
    id = db.Column(db.Integer, primary_key=True)
    file_id = db.Column(db.String, nullable=False)
    requestor_id = db.Column(db.String, nullable=False)
    reencryption_key = db.Column(db.String, nullable=False)
    timestamp = db.Column(db.DateTime, default=datetime.utcnow)

    def __init__(self, file_id, requestor_id, reencryption_key):
        self.file_id = file_id
        self.requestor_id = requestor_id
        self.reencryption_key = reencryption_key

with app.app_context():
    db.create_all()

@app.route('/store_key', methods=['POST'])
def store_key():
    data = request.get_json()
    file_id = data['file_id']
    requestor_id = data['requestor_id']
    reencryption_key = data['reencryption_key']
    key = ReencryptionKey(file_id, requestor_id, reencryption_key)
    db.session.add(key)
    db.session.commit()
    return jsonify({'message': 'Re-encryption key stored successfully.'})

@app.route('/delete_key', methods=['POST'])
def delete_key():
    data = request.get_json()
    file_id = data['file_id']
    requestor_id = data['requestor_id']
    
    key_to_delete = ReencryptionKey.query.filter_by(file_id=file_id, requestor_id=requestor_id).first()
    
    if key_to_delete is None:
        return jsonify({'message': 'Re-encryption key not found.'}), 404

    db.session.delete(key_to_delete)
    db.session.commit()
    return jsonify({'message': 'Re-encryption key deleted successfully.'})

@app.route('/re_encrypt', methods=['POST'])
def re_encrypt():
    # need to receive file_id, requestor_id, capsuleString, verifying_key_pem
    data = request.get_json()
    file_id = data['file_id']
    requestor_id = data['requestor_id']
    capsuleString = data['capsule']
    verifying_key_string = data['verifying_key']
    kfrag_str = ReencryptionKey.query.filter_by(file_id=file_id, requestor_id=requestor_id).first().reencryption_key
    if kfrag_str is None:
        return jsonify({'message': 'No re-encryption key found.'}), 404
    
    serialized_verifying_key = base64.b64decode(verifying_key_string.encode())
    verifying_key = PublicKey.from_bytes(serialized_verifying_key)
    serialized_kfrag = base64.b64decode(kfrag_str.encode())
    kfrag = KeyFrag.from_bytes(serialized_kfrag)
    verified_kfrag = kfrag.verify(verifying_key)
    serialized_capsule = base64.b64decode(capsuleString.encode())
    capsule = Capsule.from_bytes(serialized_capsule)

    cfrag = reencrypt(capsule=capsule, kfrag=verified_kfrag)
    # Serialize the cfrag to bytes
    serialized_cfrag = bytes(cfrag)
    cfragString = base64.b64encode(serialized_cfrag).decode()
    return jsonify({'cfrag': cfragString})


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
