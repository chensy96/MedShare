'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');

class CreateAssetWorkload extends WorkloadModuleBase {
    constructor() {
        super();
    }

    async submitTransaction() {
        // const assetID = 'caliper_test' + this.workerIndex + '_2_' + this.txIndex;
        const assetID = 'caliper_test' + this.workerIndex + '_' + Date.now();
        const myArgs = {
            contractId: 'medcare',
            contractFunction: 'CreateAsset',
            contractArguments: [],
            transientMap: {
                asset_properties: Buffer.from(JSON.stringify({
                    pointer: 'QmfYmWF2auuPVmCTpEycsyMv4wHnTDGBG4nigDmy3YpgoQ',
                    assetID: assetID,
                    dataSubject: 'schen2',
                    version: 1,
                    filekey: 'abc123',
                    acl: ['Org1MSP', 'Org2MSP', 'Org3MSP']
                }))
            }
        };  
        await this.sutAdapter.sendRequests(myArgs);
    }

    async cleanupWorkloadModule() {
        // No cleanup required
    }
}

function createWorkloadModule() {
    return new CreateAssetWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
