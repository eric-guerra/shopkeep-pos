angular.module('shopkeep').controller('SalesCtrl', ['$scope', 'Api', 'errorText', function ($scope, Api, errorText) {
    var vm = this;
    vm.selected = null;
    vm.returnQty = {};

    function toast(text, kind) { $scope.$emit('toast', text, kind); }

    vm.load = function (page) {
        Api.sales(page).then(function (p) { vm.page = p; });
    };

    vm.open = function (receiptNo) {
        Api.sale(receiptNo).then(function (s) { vm.selected = s; vm.returnQty = {}; }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.processReturn = function () {
        var lines = Object.keys(vm.returnQty)
            .filter(function (id) { return vm.returnQty[id] > 0; })
            .map(function (id) { return { saleLineId: Number(id), quantity: vm.returnQty[id] }; });
        if (!lines.length) { toast('Choose quantities to return'); return; }
        Api.returnItems(vm.selected.receiptNo, lines).then(function (s) {
            toast('Refund issued', 'success');
            vm.selected = s;
            vm.returnQty = {};
            vm.load(0);
        }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.voidSale = function () {
        if (!window.confirm('Void ' + vm.selected.receiptNo + '? Stock will be restored.')) { return; }
        Api.voidSale(vm.selected.receiptNo).then(function (s) { vm.selected = s; vm.load(0); }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.load(0);
}]);
