sap.ui.define(['sap/fe/test/ListReport'], function(ListReport) {
    'use strict';

    var CustomPageDefinitions = {
        actions: {},
        assertions: {}
    };

    return new ListReport(
        {
            appId: 'verifications.verificationui',
            componentId: 'MachinesList',
            contextPath: '/Machines'
        },
        CustomPageDefinitions
    );
});