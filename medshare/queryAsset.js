'use strict';

const { WorkloadModuleBase } = require('@hyperledger/caliper-core');

class QueryAssetByPatientWorkload extends WorkloadModuleBase {
    constructor() {
        super();
    }

    async submitTransaction() {
        const myArgs = {
            contractId: 'medcare',
            contractFunction: 'QueryAssetByPatient',
            contractArguments: ['cbirm1'],
        };

        await this.sutAdapter.sendRequests(myArgs);
    }
}

function createWorkloadModule() {
    return new QueryAssetByPatientWorkload();
}

module.exports.createWorkloadModule = createWorkloadModule;
