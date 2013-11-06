angular.module('shopkeep').controller('ReportsCtrl', ['Api', function (Api) {
    var vm = this;
    vm.date = new Date();

    function iso(d) {
        return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2);
    }

    vm.load = function () {
        Api.daily(iso(vm.date)).then(function (r) { vm.report = r; });
    };

    vm.marginPct = function () {
        return vm.report && vm.report.netSalesCents ? Math.round(vm.report.grossMarginCents / vm.report.netSalesCents * 100) : 0;
    };

    vm.load();
}]);
