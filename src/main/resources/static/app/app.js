angular.module('shopkeep', ['ngRoute'])
    .config(['$routeProvider', '$httpProvider', function ($routeProvider, $httpProvider) {
        $routeProvider
            .when('/register', { templateUrl: '/app/views/register.html', controller: 'RegisterCtrl', controllerAs: 'vm' })
            .when('/sales', { templateUrl: '/app/views/sales.html', controller: 'SalesCtrl', controllerAs: 'vm' })
            .when('/products', { templateUrl: '/app/views/products.html', controller: 'ProductsCtrl', controllerAs: 'vm' })
            .when('/reports', { templateUrl: '/app/views/reports.html', controller: 'ReportsCtrl', controllerAs: 'vm' })
            .otherwise('/register');
        // Spring Security's CookieCsrfTokenRepository uses the same names Angular does by default.
        $httpProvider.defaults.xsrfCookieName = 'XSRF-TOKEN';
        $httpProvider.defaults.xsrfHeaderName = 'X-XSRF-TOKEN';
        $httpProvider.interceptors.push('authInterceptor');
    }])
    .factory('authInterceptor', ['$q', '$window', function ($q, $window) {
        return {
            responseError: function (rejection) {
                if (rejection.status === 401) { $window.location.href = '/login'; }
                return $q.reject(rejection);
            }
        };
    }])
    .filter('money', function () {
        return function (cents) {
            if (cents === undefined || cents === null) { return ''; }
            var sign = cents < 0 ? '-' : '';
            return sign + '$' + (Math.abs(cents) / 100).toFixed(2).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        };
    })
    .controller('ShellCtrl', ['$scope', '$location', '$timeout', 'Api', function ($scope, $location, $timeout, Api) {
        $scope.me = {};
        Api.me().then(function (me) { $scope.me = me; });
        $scope.isActive = function (path) { return $location.path() === path; };

        var timer;
        $scope.$on('toast', function (e, text, kind) {
            $scope.toast = { text: text, kind: kind || 'info' };
            $timeout.cancel(timer);
            timer = $timeout(function () { $scope.toast = null; }, 4000);
        });
    }]);
