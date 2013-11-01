angular.module('shopkeep').controller('RegisterCtrl', ['$scope', 'Api', 'errorText', function ($scope, Api, errorText) {
    var vm = this;
    vm.cart = [];          // [{product, quantity, discountPercent}]
    vm.totals = null;
    vm.payment = { method: 'CASH', tendered: '' };
    vm.orderDiscount = 0;
    vm.lastSale = null;

    function toast(text, kind) { $scope.$emit('toast', text, kind); }

    function cartLines() {
        return vm.cart.map(function (l) { return { productId: l.product.id, quantity: l.quantity, discountPercent: l.discountPercent || 0 }; });
    }

    vm.requote = function () {
        if (!vm.cart.length) { vm.totals = null; return; }
        Api.quote(cartLines(), Math.round((vm.orderDiscount || 0) * 100)).then(function (t) { vm.totals = t; }, function (res) { toast(errorText(res), 'error'); });
    };

    vm.add = function (product) {
        var existing = vm.cart.filter(function (l) { return l.product.id === product.id; })[0];
        if (existing) { existing.quantity++; } else { vm.cart.push({ product: product, quantity: 1, discountPercent: 0 }); }
        vm.query = '';
        vm.results = [];
        vm.requote();
    };

    // The input doubles as a barcode field: scanners type the code and press Enter.
    vm.scan = function () {
        if (!vm.query) { return; }
        Api.scan(vm.query).then(vm.add, function () {
            Api.searchProducts(vm.query).then(function (page) {
                if (page.content.length === 1) { vm.add(page.content[0]); } else { vm.results = page.content; }
            });
        });
    };

    vm.search = function () {
        if (!vm.query || vm.query.length < 2) { vm.results = []; return; }
        Api.searchProducts(vm.query).then(function (page) { vm.results = page.content; });
    };

    vm.remove = function (line) {
        vm.cart.splice(vm.cart.indexOf(line), 1);
        vm.requote();
    };

    vm.lineTotal = function (index) {
        return vm.totals && vm.totals.lines[index] ? vm.totals.lines[index].grossCents - vm.totals.lines[index].discountCents : null;
    };

    vm.quickTender = function () {
        if (!vm.totals) { return []; }
        var t = vm.totals.totalCents;
        var options = [t];
        [500, 1000, 2000, 5000, 10000].forEach(function (bill) {
            var rounded = Math.ceil(t / bill) * bill;
            if (rounded > t && options.indexOf(rounded) < 0) { options.push(rounded); }
        });
        return options.slice(0, 4);
    };

    vm.changeDue = function () {
        var tendered = Math.round(parseFloat(vm.payment.tendered || 0) * 100);
        return vm.totals ? tendered - vm.totals.totalCents : 0;
    };

    vm.charge = function () {
        var req = {
            lines: cartLines(),
            orderDiscountCents: Math.round((vm.orderDiscount || 0) * 100),
            paymentMethod: vm.payment.method,
            tenderedCents: vm.payment.method === 'CASH' ? Math.round(parseFloat(vm.payment.tendered || 0) * 100) : null
        };
        vm.busy = true;
        Api.checkout(req).then(function (sale) {
            vm.lastSale = sale;
            vm.cart = [];
            vm.totals = null;
            vm.orderDiscount = 0;
            vm.payment = { method: 'CASH', tendered: '' };
            toast('Sale ' + sale.receiptNo + ' complete', 'success');
        }, function (res) {
            toast(errorText(res), 'error');
        }).finally(function () { vm.busy = false; });
    };

    vm.printReceipt = function () { window.print(); };
}]);
