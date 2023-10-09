'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');

class UpdateAclWorkload extends WorkloadModuleBase {
    constructor() {
        super();
    }

    async submitTransaction() {
        const assetID = 'phi_mock0';
        const myArgs = {
            contractId: 'medcare',
            contractFunction: 'updateAclPermission',
            contractArguments: [assetID, 'Org3MSP'],
        };

        await this.sutAdapter.sendRequests(myArgs);
    }
}

function createWorkloadModule() {
    return new UpdateAclWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
