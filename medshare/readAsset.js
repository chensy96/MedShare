'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');

class ReadAssetWorkload extends WorkloadModuleBase {
    constructor() {
        super();
    }

    async submitTransaction() {
        const assetID = 'phi_mock0';
        const myArgs = {
            contractId: 'medcare',
            contractFunction: 'ReadAsset',
            contractArguments: [assetID],
        };

        await this.sutAdapter.sendRequests(myArgs);
    }
}

function createWorkloadModule() {
    return new ReadAssetWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
