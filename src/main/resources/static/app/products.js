angular.module('shopkeep').controller('ProductsCtrl', ['$scope', 'Api', 'errorText', function ($scope, Api, errorText) {
    var vm = this;
    vm.page = { content: [] };
    vm.editing = null;
    vm.reasons = ['RECEIVE', 'ADJUST', 'DAMAGE'];

    function toast(text, kind) { $scope.$emit('toast', text, kind); }

    vm.load = function (page) {
        Api.searchProducts(vm.query, page).then(function (p) { vm.page = p; });
        Api.lowStock().then(function (items) { vm.lowStock = items; });
    };

    vm.newProduct = function () {
        vm.editing = { sku: '', name: '', category: '', price: '', cost: '', taxable: true, reorderLevel: 0, initialStock: 0 };
    };

    vm.edit = function (p) {
        vm.editing = angular.extend({}, p, { price: (p.priceCents / 100).toFixed(2), cost: (p.costCents / 100).toFixed(2) });
        Api.history(p.id).then(function (h) { vm.history = h; });
    };

    vm.save = function () {
        var p = angular.extend({}, vm.editing, {
            sku: (vm.editing.sku || '').toUpperCase(),
            priceCents: Math.round(parseFloat(vm.editing.price) * 100),
            costCents: Math.round(parseFloat(vm.editing.cost) * 100)
        });
        (p.id ? Api.updateProduct(p) : Api.createProduct(p)).then(function () {
            toast('Saved ' + p.name, 'success');
            vm.editing = null;
            vm.load(vm.page.number);
        }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.adjust = function () {
        var delta = parseInt(vm.adjustment.delta, 10);
        Api.adjust(vm.editing.id, delta, vm.adjustment.reason, vm.adjustment.note).then(function (p) {
            vm.editing.stockOnHand = p.stockOnHand;
            vm.editing.version = p.version;
            vm.adjustment = {};
            Api.history(p.id).then(function (h) { vm.history = h; });
            vm.load(vm.page.number);
        }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.deactivate = function (p) {
        if (!window.confirm('Hide ' + p.name + ' from the register?')) { return; }
        Api.deactivate(p.id).then(function () { vm.load(vm.page.number); });
    };

    vm.margin = function (p) {
        return p.priceCents ? Math.round((p.priceCents - p.costCents) / p.priceCents * 100) : 0;
    };

    vm.adjustment = {};
    vm.load(0);
}]);
