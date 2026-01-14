sap.ui.define([
    "sap/fe/test/JourneyRunner",
	"verifications/verificationui/test/integration/pages/MachinesList",
	"verifications/verificationui/test/integration/pages/MachinesObjectPage"
], function (JourneyRunner, MachinesList, MachinesObjectPage) {
    'use strict';

    var runner = new JourneyRunner({
        launchUrl: sap.ui.require.toUrl('verifications/verificationui') + '/test/flpSandbox.html#verificationsverificationui-tile',
        pages: {
			onTheMachinesList: MachinesList,
			onTheMachinesObjectPage: MachinesObjectPage
        },
        async: true
    });

    return runner;
});

