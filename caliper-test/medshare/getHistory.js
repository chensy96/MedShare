'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');

class GetHistoryForAssetWorkload extends WorkloadModuleBase {
    constructor() {
        super();
    }

    async submitTransaction() {
        const myArgs = {
            contractId: 'medcare',
            contractFunction: 'getHistoryForAsset',
            contractArguments: ['phi_mock0_read'],
        };

        await this.sutAdapter.sendRequests(myArgs);
    }
}

function createWorkloadModule() {
    return new GetHistoryForAssetWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;

