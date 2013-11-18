angular.module('shopkeep')
    .factory('Api', ['$http', function ($http) {
        function data(res) { return res.data; }
        return {
            me: function () { return $http.get('/api/me').then(data); },
            searchProducts: function (q, page) { return $http.get('/api/products', { params: { q: q, page: page || 0 } }).then(data); },
            scan: function (code) { return $http.get('/api/products/scan/' + encodeURIComponent(code)).then(data); },
            lowStock: function () { return $http.get('/api/products/low-stock').then(data); },
            createProduct: function (p) { return $http.post('/api/products', p).then(data); },
            updateProduct: function (p) { return $http.put('/api/products/' + p.id, p, { headers: { 'If-Match': String(p.version) } }).then(data); },
            deactivate: function (id) { return $http.delete('/api/products/' + id); },
            adjust: function (id, delta, reason, note) { return $http.post('/api/inventory/' + id + '/adjust', { delta: delta, reason: reason, note: note }).then(data); },
            history: function (id) { return $http.get('/api/inventory/' + id + '/history').then(data); },
            quote: function (lines, orderDiscountCents) { return $http.post('/api/sales/quote', { lines: lines, orderDiscountCents: orderDiscountCents || 0 }).then(data); },
            checkout: function (req) { return $http.post('/api/sales', req).then(data); },
            sales: function (page) { return $http.get('/api/sales', { params: { page: page || 0 } }).then(data); },
            sale: function (receiptNo) { return $http.get('/api/sales/' + encodeURIComponent(receiptNo)).then(data); },
            returnItems: function (receiptNo, lines) { return $http.post('/api/sales/' + encodeURIComponent(receiptNo) + '/returns', { lines: lines }).then(data); },
            voidSale: function (receiptNo) { return $http.post('/api/sales/' + encodeURIComponent(receiptNo) + '/void').then(data); },
            daily: function (date) { return $http.get('/api/reports/daily', { params: { date: date } }).then(data); }
        };
    }])
    .factory('errorText', function () {
        return function (res) {
            var body = res && res.data;
            if (!body) { return 'Network error'; }
            if (body.code === 'INSUFFICIENT_STOCK') {
                return 'Not enough stock: ' + body.details.shortages.map(function (s) { return s.sku + ' (' + s.onHand + ' on hand)'; }).join(', ');
            }
            if (body.code === 'INVALID' && body.details && Object.keys(body.details).length) {
                return Object.keys(body.details).map(function (k) { return k + ': ' + body.details[k]; }).join('; ');
            }
            return body.message || ('Error ' + res.status);
        };
    });
