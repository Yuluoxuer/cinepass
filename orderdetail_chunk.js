"use strict";
(self["webpackChunkmiaoyu_frontend"] = self["webpackChunkmiaoyu_frontend"] || []).push([["p__me__orderDetail"],{

/***/ "./src/api/order.ts":
/*!**************************!*\
  !*** ./src/api/order.ts ***!
  \**************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony export */ __webpack_require__.d(__webpack_exports__, {
/* harmony export */   cancelOrder: function() { return /* binding */ cancelOrder; },
/* harmony export */   createOrder: function() { return /* binding */ createOrder; },
/* harmony export */   getOrder: function() { return /* binding */ getOrder; },
/* harmony export */   getPayQrcode: function() { return /* binding */ getPayQrcode; },
/* harmony export */   getPaySession: function() { return /* binding */ getPaySession; },
/* harmony export */   getRedeemQrcode: function() { return /* binding */ getRedeemQrcode; },
/* harmony export */   getRedeemSession: function() { return /* binding */ getRedeemSession; },
/* harmony export */   listOrders: function() { return /* binding */ listOrders; },
/* harmony export */   lockSeats: function() { return /* binding */ lockSeats; },
/* harmony export */   payOrder: function() { return /* binding */ payOrder; },
/* harmony export */   redeemOrder: function() { return /* binding */ redeemOrder; },
/* harmony export */   unlockSeats: function() { return /* binding */ unlockSeats; }
/* harmony export */ });
/* harmony import */ var _client__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./client */ "./src/api/client.ts");
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");


function lockSeats(body) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.post)('/locks', body, {
    headers: {
      'Idempotency-Key': (0,_client__WEBPACK_IMPORTED_MODULE_0__.idempotencyKey)('lock')
    }
  });
}
function unlockSeats(lockId, sessionId) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.del)("/locks/".concat(lockId), {
    sessionId: sessionId
  });
}
function createOrder(body) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.post)('/orders', body, {
    headers: {
      'Idempotency-Key': (0,_client__WEBPACK_IMPORTED_MODULE_0__.idempotencyKey)('order')
    }
  });
}
function listOrders(params) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)('/orders', params);
}
function getOrder(orderId) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)("/orders/".concat(orderId));
}
function cancelOrder(orderId, reason) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.post)("/orders/".concat(orderId, "/cancel"), reason ? {
    reason: reason
  } : {}, {
    headers: {
      'Idempotency-Key': (0,_client__WEBPACK_IMPORTED_MODULE_0__.idempotencyKey)('cancel')
    }
  });
}
function getPayQrcode(orderId) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)("/orders/".concat(orderId, "/pay-qrcode"));
}
function getPaySession(orderId, t) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)("/orders/".concat(orderId, "/pay-session"), {
    t: t
  }, {
    skipAuth: true
  });
}
function payOrder(orderId, body, payToken) {
  var headers = {
    'Idempotency-Key': (0,_client__WEBPACK_IMPORTED_MODULE_0__.idempotencyKey)('pay')
  };
  if (payToken) headers['X-Pay-Token'] = payToken;
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.post)("/orders/".concat(orderId, "/pay"), body, {
    headers: headers,
    skipAuth: !!payToken
  });
}
function getRedeemQrcode(orderId) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)("/orders/".concat(orderId, "/redeem-qrcode"));
}
function getRedeemSession(orderId, t) {
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.get)("/orders/".concat(orderId, "/redeem-session"), {
    t: t
  }, {
    skipAuth: true
  });
}
function redeemOrder(orderId, redeemToken) {
  var headers = {
    'Idempotency-Key': (0,_client__WEBPACK_IMPORTED_MODULE_0__.idempotencyKey)('redeem')
  };
  if (redeemToken) headers['X-Redeem-Token'] = redeemToken;
  return (0,_client__WEBPACK_IMPORTED_MODULE_0__.post)("/orders/".concat(orderId, "/redeem"), {}, {
    headers: headers,
    skipAuth: !!redeemToken
  });
}

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/components/BlankPlaceholder/index.tsx":
/*!***************************************************!*\
  !*** ./src/components/BlankPlaceholder/index.tsx ***!
  \***************************************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_0__);
/* harmony import */ var _BlankPlaceholder_less_modules__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./BlankPlaceholder.less?modules */ "./src/components/BlankPlaceholder/BlankPlaceholder.less?modules");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_2___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_2__);
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");

var _jsxFileName = "D:\\cinepass-front\\leijieming-cinepass-front\\src\\components\\BlankPlaceholder\\index.tsx",
  _this = undefined;



/**
 * 资源缺失 / 读接口失败时的基本形状占位（灰块，非内容文案）。
 */
var BlankPlaceholder = function BlankPlaceholder(_ref) {
  var _ref$variant = _ref.variant,
    variant = _ref$variant === void 0 ? 'block' : _ref$variant,
    _ref$count = _ref.count,
    count = _ref$count === void 0 ? 1 : _ref$count,
    className = _ref.className,
    style = _ref.style;
  var n = Math.max(1, count);
  var items = Array.from({
    length: n
  }, function (_, i) {
    return i;
  });
  var wrapClass = [_BlankPlaceholder_less_modules__WEBPACK_IMPORTED_MODULE_1__["default"].wrap, variant === 'poster' || variant === 'card' ? _BlankPlaceholder_less_modules__WEBPACK_IMPORTED_MODULE_1__["default"].grid : null, className].filter(Boolean).join(' ');
  return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_2__.jsxDEV)("div", {
    className: wrapClass,
    style: style,
    "aria-hidden": true,
    children: items.map(function (i) {
      return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_2__.jsxDEV)("div", {
        className: "".concat(_BlankPlaceholder_less_modules__WEBPACK_IMPORTED_MODULE_1__["default"].item, " ").concat(_BlankPlaceholder_less_modules__WEBPACK_IMPORTED_MODULE_1__["default"][variant], " miaoyu-skeleton")
      }, i, false, {
        fileName: _jsxFileName,
        lineNumber: 36,
        columnNumber: 9
      }, _this);
    })
  }, void 0, false, {
    fileName: _jsxFileName,
    lineNumber: 34,
    columnNumber: 5
  }, _this);
};
_c = BlankPlaceholder;
/* harmony default export */ __webpack_exports__["default"] = (BlankPlaceholder);
var _c;
__webpack_require__.$Refresh$.register(_c, "BlankPlaceholder");

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/components/OrderQrModal/index.tsx":
/*!***********************************************!*\
  !*** ./src/components/OrderQrModal/index.tsx ***!
  \***********************************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/regeneratorRuntime.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/regeneratorRuntime.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/asyncToGenerator.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/asyncToGenerator.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2__);
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__);
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_antd__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/antd */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/antd");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_antd__WEBPACK_IMPORTED_MODULE_4___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_antd__WEBPACK_IMPORTED_MODULE_4__);
/* harmony import */ var umi__WEBPACK_IMPORTED_MODULE_5__ = __webpack_require__(/*! umi */ "./src/.umi/exports.ts");
/* harmony import */ var _api_order__WEBPACK_IMPORTED_MODULE_6__ = __webpack_require__(/*! @/api/order */ "./src/api/order.ts");
/* harmony import */ var _api_error__WEBPACK_IMPORTED_MODULE_7__ = __webpack_require__(/*! @/api/error */ "./src/api/error.ts");
/* harmony import */ var _types__WEBPACK_IMPORTED_MODULE_8__ = __webpack_require__(/*! @/types */ "./src/types/index.ts");
/* harmony import */ var _utils_format__WEBPACK_IMPORTED_MODULE_9__ = __webpack_require__(/*! @/utils/format */ "./src/utils/format.ts");
/* harmony import */ var _components_BlankPlaceholder__WEBPACK_IMPORTED_MODULE_10__ = __webpack_require__(/*! @/components/BlankPlaceholder */ "./src/components/BlankPlaceholder/index.tsx");
/* harmony import */ var _features_seatmap_useLockCountdown__WEBPACK_IMPORTED_MODULE_11__ = __webpack_require__(/*! @/features/seatmap/useLockCountdown */ "./src/features/seatmap/useLockCountdown.ts");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__);
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");




var _jsxFileName = "D:\\cinepass-front\\leijieming-cinepass-front\\src\\components\\OrderQrModal\\index.tsx",
  _this = undefined,
  _s = __webpack_require__.$Refresh$.signature();











/**
 * 订单二维码弹窗：pay 模式生成支付二维码（扫码后轮询订单直至离开待支付，触发 onDone）；
 * redeem 模式生成入场核销二维码（扫码核销成功后轮询到 redeemed，触发 onDone 自动关闭）。
 * 均免登录，扫码直接进入手机 H5。
 */
var OrderQrModal = function OrderQrModal(_ref) {
  _s();
  var open = _ref.open,
    orderId = _ref.orderId,
    mode = _ref.mode,
    onClose = _ref.onClose,
    onDone = _ref.onDone;
  var _useState = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(null),
    _useState2 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState, 2),
    qr = _useState2[0],
    setQr = _useState2[1];
  var _useState3 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(null),
    _useState4 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState3, 2),
    order = _useState4[0],
    setOrder = _useState4[1];
  var _useState5 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(''),
    _useState6 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState5, 2),
    error = _useState6[0],
    setError = _useState6[1];
  var timer = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useRef)(null);
  var doneRef = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useRef)(onDone);
  doneRef.current = onDone;
  var load = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useCallback)( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default()( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().mark(function _callee() {
    return D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().wrap(function _callee$(_context) {
      while (1) switch (_context.prev = _context.next) {
        case 0:
          setError('');
          setQr(null);
          setOrder(null);
          if (orderId) {
            _context.next = 6;
            break;
          }
          setError('订单缺失');
          return _context.abrupt("return");
        case 6:
          _context.prev = 6;
          if (!(mode === 'pay')) {
            _context.next = 15;
            break;
          }
          _context.t0 = setQr;
          _context.next = 11;
          return _api_order__WEBPACK_IMPORTED_MODULE_6__.getPayQrcode(orderId);
        case 11:
          _context.t1 = _context.sent;
          (0, _context.t0)(_context.t1);
          _context.next = 20;
          break;
        case 15:
          _context.t2 = setQr;
          _context.next = 18;
          return _api_order__WEBPACK_IMPORTED_MODULE_6__.getRedeemQrcode(orderId);
        case 18:
          _context.t3 = _context.sent;
          (0, _context.t2)(_context.t3);
        case 20:
          _context.next = 25;
          break;
        case 22:
          _context.prev = 22;
          _context.t4 = _context["catch"](6);
          setError(_context.t4 instanceof _types__WEBPACK_IMPORTED_MODULE_8__.ApiError ? (0,_api_error__WEBPACK_IMPORTED_MODULE_7__.qrFlowErrorMessage)(_context.t4, mode) : '二维码生成失败');
        case 25:
        case "end":
          return _context.stop();
      }
    }, _callee, null, [[6, 22]]);
  })), [orderId, mode]);
  (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useEffect)(function () {
    if (!open) return;
    void load();
    return function () {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, load]);
  var payExpireAt = mode === 'pay' && qr && 'expireAt' in qr ? qr.expireAt : null;
  var _useLockCountdown = (0,_features_seatmap_useLockCountdown__WEBPACK_IMPORTED_MODULE_11__.useLockCountdown)(payExpireAt),
    text = _useLockCountdown.text,
    expired = _useLockCountdown.expired;

  // pay 模式：按后端建议间隔轮询订单，状态离开待支付后回调 onDone 关闭并刷新
  (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useEffect)(function () {
    if (!open || mode !== 'pay' || !qr || !('pollIntervalMs' in qr)) return;
    if (expired) {
      if (timer.current) clearInterval(timer.current);
      return;
    }
    var interval = qr.pollIntervalMs || 2000;
    timer.current = setInterval( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default()( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().mark(function _callee2() {
      var o, _doneRef$current;
      return D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().wrap(function _callee2$(_context2) {
        while (1) switch (_context2.prev = _context2.next) {
          case 0:
            _context2.prev = 0;
            _context2.next = 3;
            return _api_order__WEBPACK_IMPORTED_MODULE_6__.getOrder(orderId);
          case 3:
            o = _context2.sent;
            setOrder(o);
            if (o.status !== 'pending_pay') {
              if (timer.current) clearInterval(timer.current);
              (_doneRef$current = doneRef.current) === null || _doneRef$current === void 0 || _doneRef$current.call(doneRef);
            }
            _context2.next = 10;
            break;
          case 8:
            _context2.prev = 8;
            _context2.t0 = _context2["catch"](0);
          case 10:
          case "end":
            return _context2.stop();
        }
      }, _callee2, null, [[0, 8]]);
    })), interval);
    return function () {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, mode, orderId, qr, expired]);

  // redeem 模式：轮询订单，状态变为已核销后回调 onDone 自动关闭并刷新；
  // 已取消/已过期时停止轮询，保留弹窗展示对应状态文案。
  (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useEffect)(function () {
    if (!open || mode !== 'redeem' || !qr) return;
    timer.current = setInterval( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default()( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().mark(function _callee3() {
      var o, _doneRef$current2;
      return D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().wrap(function _callee3$(_context3) {
        while (1) switch (_context3.prev = _context3.next) {
          case 0:
            _context3.prev = 0;
            _context3.next = 3;
            return _api_order__WEBPACK_IMPORTED_MODULE_6__.getOrder(orderId);
          case 3:
            o = _context3.sent;
            setOrder(o);
            if (o.status === 'redeemed') {
              if (timer.current) clearInterval(timer.current);
              (_doneRef$current2 = doneRef.current) === null || _doneRef$current2 === void 0 || _doneRef$current2.call(doneRef);
            } else if (o.status === 'cancelled' || o.status === 'expired') {
              if (timer.current) clearInterval(timer.current);
            }
            _context3.next = 10;
            break;
          case 8:
            _context3.prev = 8;
            _context3.t0 = _context3["catch"](0);
          case 10:
          case "end":
            return _context3.stop();
        }
      }, _callee3, null, [[0, 8]]);
    })), 2000);
    return function () {
      if (timer.current) clearInterval(timer.current);
    };
  }, [open, mode, orderId, qr]);
  var title = mode === 'pay' ? '支付二维码' : '核销二维码';
  var renderBody = function renderBody() {
    if (error) {
      return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("div", {
        style: {
          textAlign: 'center',
          padding: '24px 0',
          color: '#d9423a'
        },
        children: error
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 116,
        columnNumber: 14
      }, _this);
    }
    if (!qr) {
      return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("div", {
        style: {
          padding: '24px 0'
        },
        children: /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)(_components_BlankPlaceholder__WEBPACK_IMPORTED_MODULE_10__["default"], {
          variant: "block"
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 121,
          columnNumber: 11
        }, _this)
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 120,
        columnNumber: 9
      }, _this);
    }
    if ((order === null || order === void 0 ? void 0 : order.status) === 'issued' || (order === null || order === void 0 ? void 0 : order.status) === 'redeemed') {
      return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("div", {
        style: {
          textAlign: 'center',
          padding: '8px 0'
        },
        children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
          style: {
            color: '#16806f',
            fontSize: 16,
            fontWeight: 700
          },
          children: ["\u2713 ", order.status === 'issued' ? '已支付' : '已核销']
        }, void 0, true, {
          fileName: _jsxFileName,
          lineNumber: 128,
          columnNumber: 11
        }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
          children: order.status === 'issued' ? '订单已出票，可前往查看取票/核销码。' : '该票券已核销，可正常入场。'
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 131,
          columnNumber: 11
        }, _this), order.status === 'issued' ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("button", {
          type: "button",
          className: "miaoyu-btn-primary",
          onClick: function onClick() {
            onClose();
            umi__WEBPACK_IMPORTED_MODULE_5__.history.push("/booking/ticket?orderId=".concat(orderId));
          },
          children: "\u67E5\u770B\u53D6\u7968\u7801"
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 133,
          columnNumber: 13
        }, _this) : null]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 127,
        columnNumber: 9
      }, _this);
    }
    if ((order === null || order === void 0 ? void 0 : order.status) === 'cancelled') {
      return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("div", {
        style: {
          textAlign: 'center',
          padding: '24px 0',
          color: '#999'
        },
        children: "\u8BA2\u5355\u5DF2\u53D6\u6D88"
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 148,
        columnNumber: 14
      }, _this);
    }
    var url = 'redeemUrl' in qr ? qr.redeemUrl : qr.payUrl;
    return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("div", {
      style: {
        textAlign: 'center',
        padding: '8px 0'
      },
      children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_antd__WEBPACK_IMPORTED_MODULE_4__.QRCode, {
        value: (0,_utils_format__WEBPACK_IMPORTED_MODULE_9__.mobileUrl)(url),
        size: 220,
        bordered: false
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 153,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
        style: {
          marginTop: 12,
          color: '#666'
        },
        children: mode === 'pay' ? '请使用手机扫码完成支付' : '请使用手机扫码核销入场'
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 154,
        columnNumber: 9
      }, _this), 'ticketCode' in qr && qr.ticketCode ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
        children: ["\u53D6\u7968\u7801 ", qr.ticketCode]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 157,
        columnNumber: 48
      }, _this) : null, mode === 'pay' && 'amount' in qr ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.Fragment, {
        children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
          style: {
            color: '#e54847',
            fontWeight: 700
          },
          children: ["\xA5", qr.amount.toFixed(2)]
        }, void 0, true, {
          fileName: _jsxFileName,
          lineNumber: 160,
          columnNumber: 13
        }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)("p", {
          style: {
            color: expired ? '#d9423a' : '#999'
          },
          children: expired ? '支付已超时，请重新下单' : "\u652F\u4ED8\u622A\u6B62\u5269\u4F59 ".concat(text)
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 161,
          columnNumber: 13
        }, _this)]
      }, void 0, true) : null]
    }, void 0, true, {
      fileName: _jsxFileName,
      lineNumber: 152,
      columnNumber: 7
    }, _this);
  };
  return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_12__.jsxDEV)(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_antd__WEBPACK_IMPORTED_MODULE_4__.Modal, {
    open: open,
    title: title,
    onCancel: onClose,
    footer: null,
    width: 360,
    children: renderBody()
  }, void 0, false, {
    fileName: _jsxFileName,
    lineNumber: 171,
    columnNumber: 5
  }, _this);
};
_s(OrderQrModal, "kFVifn/hXXUO2VGvejqVXAUw97A=", false, function () {
  return [_features_seatmap_useLockCountdown__WEBPACK_IMPORTED_MODULE_11__.useLockCountdown];
});
_c = OrderQrModal;
/* harmony default export */ __webpack_exports__["default"] = (OrderQrModal);
var _c;
__webpack_require__.$Refresh$.register(_c, "OrderQrModal");

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/features/seatmap/useLockCountdown.ts":
/*!**************************************************!*\
  !*** ./src/features/seatmap/useLockCountdown.ts ***!
  \**************************************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony export */ __webpack_require__.d(__webpack_exports__, {
/* harmony export */   useLockCountdown: function() { return /* binding */ useLockCountdown; }
/* harmony export */ });
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0__);
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_1___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_1__);
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");


var _s = __webpack_require__.$Refresh$.signature();

function useLockCountdown(expireAt) {
  _s();
  var _useState = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_1__.useState)(0),
    _useState2 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0___default()(_useState, 2),
    remainMs = _useState2[0],
    setRemainMs = _useState2[1];
  (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_1__.useEffect)(function () {
    if (!expireAt) {
      setRemainMs(0);
      return;
    }
    var tick = function tick() {
      setRemainMs(Math.max(0, new Date(expireAt).getTime() - Date.now()));
    };
    tick();
    var id = setInterval(tick, 1000);
    return function () {
      return clearInterval(id);
    };
  }, [expireAt]);
  var totalSec = Math.floor(remainMs / 1000);
  var mm = String(Math.floor(totalSec / 60)).padStart(2, '0');
  var ss = String(totalSec % 60).padStart(2, '0');
  var warning = totalSec > 0 && totalSec < 180;
  var expired = !!expireAt && remainMs <= 0;
  return {
    remainMs: remainMs,
    text: "".concat(mm, ":").concat(ss),
    warning: warning,
    expired: expired
  };
}
_s(useLockCountdown, "/VX4sULNLLZHUIPFSIB/wc9b7YI=");

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/pages/me/orderDetail.tsx":
/*!**************************************!*\
  !*** ./src/pages/me/orderDetail.tsx ***!
  \**************************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/regeneratorRuntime.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/regeneratorRuntime.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/asyncToGenerator.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/asyncToGenerator.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1__);
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2__);
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__);
/* harmony import */ var umi__WEBPACK_IMPORTED_MODULE_4__ = __webpack_require__(/*! umi */ "./src/.umi/exports.ts");
/* harmony import */ var _api_order__WEBPACK_IMPORTED_MODULE_5__ = __webpack_require__(/*! @/api/order */ "./src/api/order.ts");
/* harmony import */ var _components_BlankPlaceholder__WEBPACK_IMPORTED_MODULE_6__ = __webpack_require__(/*! @/components/BlankPlaceholder */ "./src/components/BlankPlaceholder/index.tsx");
/* harmony import */ var _components_OrderQrModal__WEBPACK_IMPORTED_MODULE_7__ = __webpack_require__(/*! @/components/OrderQrModal */ "./src/components/OrderQrModal/index.tsx");
/* harmony import */ var _stores_booking__WEBPACK_IMPORTED_MODULE_8__ = __webpack_require__(/*! @/stores/booking */ "./src/stores/booking.ts");
/* harmony import */ var _utils_format__WEBPACK_IMPORTED_MODULE_9__ = __webpack_require__(/*! @/utils/format */ "./src/utils/format.ts");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__ = __webpack_require__(/*! mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime */ "webpack/container/remote/mf/D:/cinepass-front/leijieming-cinepass-front/node_modules/react/jsx-dev-runtime");
/* harmony import */ var mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10___default = /*#__PURE__*/__webpack_require__.n(mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__);
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");




var _jsxFileName = "D:\\cinepass-front\\leijieming-cinepass-front\\src\\pages\\me\\orderDetail.tsx",
  _this = undefined,
  _s = __webpack_require__.$Refresh$.signature();








var STATUS_LABEL = {
  pending_pay: '待支付',
  issued: '已出票',
  redeemed: '已核销',
  cancelled: '已取消',
  expired: '已过期'
};
var OrderDetailPage = function OrderDetailPage() {
  _s();
  var _useParams = (0,umi__WEBPACK_IMPORTED_MODULE_4__.useParams)(),
    orderId = _useParams.orderId;
  var _useState = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(null),
    _useState2 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState, 2),
    order = _useState2[0],
    setOrder = _useState2[1];
  var _useState3 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(true),
    _useState4 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState3, 2),
    loading = _useState4[0],
    setLoading = _useState4[1];
  var _useState5 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(false),
    _useState6 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState5, 2),
    missing = _useState6[0],
    setMissing = _useState6[1];
  var _useState7 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)(false),
    _useState8 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState7, 2),
    qrOpen = _useState8[0],
    setQrOpen = _useState8[1];
  var _useState9 = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useState)('pay'),
    _useState10 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_2___default()(_useState9, 2),
    qrMode = _useState10[0],
    setQrMode = _useState10[1];
  var seatNameById = (0,_stores_booking__WEBPACK_IMPORTED_MODULE_8__.useBookingStore)(function (s) {
    return s.seatNameById;
  });
  var loadOrder = (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useCallback)( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_asyncToGenerator_js__WEBPACK_IMPORTED_MODULE_1___default()( /*#__PURE__*/D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().mark(function _callee() {
    var o;
    return D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_regeneratorRuntime_js__WEBPACK_IMPORTED_MODULE_0___default()().wrap(function _callee$(_context) {
      while (1) switch (_context.prev = _context.next) {
        case 0:
          if (orderId) {
            _context.next = 4;
            break;
          }
          setLoading(false);
          setMissing(true);
          return _context.abrupt("return");
        case 4:
          setLoading(true);
          setMissing(false);
          _context.prev = 6;
          _context.next = 9;
          return _api_order__WEBPACK_IMPORTED_MODULE_5__.getOrder(orderId);
        case 9:
          o = _context.sent;
          setOrder(o);
          setMissing(false);
          _context.next = 18;
          break;
        case 14:
          _context.prev = 14;
          _context.t0 = _context["catch"](6);
          setOrder(null);
          setMissing(true);
        case 18:
          _context.prev = 18;
          setLoading(false);
          return _context.finish(18);
        case 21:
        case "end":
          return _context.stop();
      }
    }, _callee, null, [[6, 14, 18, 21]]);
  })), [orderId]);
  (0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react__WEBPACK_IMPORTED_MODULE_3__.useEffect)(function () {
    void loadOrder();
  }, [loadOrder]);
  if (loading) {
    return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("div", {
      className: "miaoyu-container",
      children: /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)(_components_BlankPlaceholder__WEBPACK_IMPORTED_MODULE_6__["default"], {
        variant: "block"
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 54,
        columnNumber: 9
      }, _this)
    }, void 0, false, {
      fileName: _jsxFileName,
      lineNumber: 53,
      columnNumber: 7
    }, _this);
  }
  if (missing || !order) {
    return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("div", {
      className: "miaoyu-container",
      children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("button", {
        type: "button",
        className: "miaoyu-btn-text",
        onClick: function onClick() {
          return umi__WEBPACK_IMPORTED_MODULE_4__.history.back();
        },
        children: "\u2190 \u8FD4\u56DE"
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 62,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)(_components_BlankPlaceholder__WEBPACK_IMPORTED_MODULE_6__["default"], {
        variant: "block",
        style: {
          marginTop: 12
        }
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 65,
        columnNumber: 9
      }, _this)]
    }, void 0, true, {
      fileName: _jsxFileName,
      lineNumber: 61,
      columnNumber: 7
    }, _this);
  }
  return /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("div", {
    className: "miaoyu-container",
    children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("button", {
      type: "button",
      className: "miaoyu-btn-text",
      onClick: function onClick() {
        return umi__WEBPACK_IMPORTED_MODULE_4__.history.back();
      },
      children: "\u2190 \u8FD4\u56DE"
    }, void 0, false, {
      fileName: _jsxFileName,
      lineNumber: 72,
      columnNumber: 7
    }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("div", {
      style: {
        background: '#fff',
        borderRadius: 12,
        padding: 24,
        marginTop: 12
      },
      children: [/*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("h1", {
        children: order.movieTitle
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 76,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        children: ["\u72B6\u6001\uFF1A", STATUS_LABEL[order.status] || order.status]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 77,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        children: [order.cinemaName, " \xB7 ", order.hallName]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 78,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        children: order.startTime.replace('T', ' ').slice(0, 16)
      }, void 0, false, {
        fileName: _jsxFileName,
        lineNumber: 81,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        children: ["\u5EA7\u4F4D\uFF1A", (0,_utils_format__WEBPACK_IMPORTED_MODULE_9__.formatOrderSeatLabels)(order, seatNameById)]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 82,
        columnNumber: 9
      }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        style: {
          color: '#e54847',
          fontSize: 20,
          fontWeight: 700
        },
        children: ["\xA5", order.amount]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 83,
        columnNumber: 9
      }, _this), order.ticketCode ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("p", {
        children: ["\u53D6\u7968\u7801\uFF1A", order.ticketCode]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 84,
        columnNumber: 29
      }, _this) : null, /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("div", {
        style: {
          marginTop: 20,
          display: 'flex',
          gap: 12,
          flexWrap: 'wrap'
        },
        children: [order.status === 'pending_pay' ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("button", {
          type: "button",
          className: "miaoyu-btn-primary",
          onClick: function onClick() {
            setQrMode('pay');
            setQrOpen(true);
          },
          children: "\u652F\u4ED8\u4E8C\u7EF4\u7801"
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 87,
          columnNumber: 13
        }, _this) : null, order.status === 'issued' ? /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("button", {
          type: "button",
          className: "miaoyu-btn-secondary",
          onClick: function onClick() {
            setQrMode('redeem');
            setQrOpen(true);
          },
          children: "\u6838\u9500\u4E8C\u7EF4\u7801"
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 99,
          columnNumber: 13
        }, _this) : null, /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)("button", {
          type: "button",
          className: "miaoyu-btn-ghost",
          onClick: function onClick() {
            return umi__WEBPACK_IMPORTED_MODULE_4__.history.back();
          },
          children: "\u8FD4\u56DE"
        }, void 0, false, {
          fileName: _jsxFileName,
          lineNumber: 110,
          columnNumber: 11
        }, _this)]
      }, void 0, true, {
        fileName: _jsxFileName,
        lineNumber: 85,
        columnNumber: 9
      }, _this)]
    }, void 0, true, {
      fileName: _jsxFileName,
      lineNumber: 75,
      columnNumber: 7
    }, _this), /*#__PURE__*/(0,mf_D_cinepass_front_leijieming_cinepass_front_node_modules_react_jsx_dev_runtime__WEBPACK_IMPORTED_MODULE_10__.jsxDEV)(_components_OrderQrModal__WEBPACK_IMPORTED_MODULE_7__["default"], {
      open: qrOpen,
      orderId: order.orderId,
      mode: qrMode,
      onClose: function onClose() {
        return setQrOpen(false);
      },
      onDone: function onDone() {
        setQrOpen(false);
        void loadOrder();
      }
    }, void 0, false, {
      fileName: _jsxFileName,
      lineNumber: 115,
      columnNumber: 7
    }, _this)]
  }, void 0, true, {
    fileName: _jsxFileName,
    lineNumber: 71,
    columnNumber: 5
  }, _this);
};
_s(OrderDetailPage, "bTlgt6dKnEmo+MaMZcbQrhR8FiY=", false, function () {
  return [umi__WEBPACK_IMPORTED_MODULE_4__.useParams, _stores_booking__WEBPACK_IMPORTED_MODULE_8__.useBookingStore];
});
_c = OrderDetailPage;
/* harmony default export */ __webpack_exports__["default"] = (OrderDetailPage);
var _c;
__webpack_require__.$Refresh$.register(_c, "OrderDetailPage");

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/utils/format.ts":
/*!*****************************!*\
  !*** ./src/utils/format.ts ***!
  \*****************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony export */ __webpack_require__.d(__webpack_exports__, {
/* harmony export */   addLocalDays: function() { return /* binding */ addLocalDays; },
/* harmony export */   formatDistance: function() { return /* binding */ formatDistance; },
/* harmony export */   formatMoney: function() { return /* binding */ formatMoney; },
/* harmony export */   formatOrderSeatLabels: function() { return /* binding */ formatOrderSeatLabels; },
/* harmony export */   localDateISO: function() { return /* binding */ localDateISO; },
/* harmony export */   mobileUrl: function() { return /* binding */ mobileUrl; }
/* harmony export */ });
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0__ = __webpack_require__(/*! ./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js */ "./node_modules/@umijs/babel-preset-umi/node_modules/@babel/runtime/helpers/slicedToArray.js");
/* harmony import */ var D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0___default = /*#__PURE__*/__webpack_require__.n(D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0__);
/* harmony import */ var _utils_lanIp__WEBPACK_IMPORTED_MODULE_1__ = __webpack_require__(/*! @/utils/lanIp */ "./src/utils/lanIp.ts");
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");




/** 兼容旧引用的轻量 format 工具 */
function formatMoney(n) {
  return "\xA5".concat(n.toFixed(2));
}

/**
 * 后端 public-base-url 默认指向后端自身（如 http://localhost:8080），
 * 拼出的 payUrl/redeemUrl 需改写为当前前端源，否则二维码扫码打到错误 host。
 * 已指向当前源时原样返回；host 是 localhost 时自动替换为本机局域网 IP，
 * 保证顾客手机扫码能连到这台机器（见 reachableHost）。
 */
function mobileUrl(raw) {
  if (!raw) return '';
  try {
    var u = new URL(raw, window.location.origin);
    u.protocol = window.location.protocol;
    u.host = (0,_utils_lanIp__WEBPACK_IMPORTED_MODULE_1__.reachableHost)(window.location.host);
    return u.toString();
  } catch (_unused) {
    return raw;
  }
}
function formatDistance(meters) {
  if (meters == null) return '';
  return meters >= 1000 ? "".concat((meters / 1000).toFixed(1), "km") : "".concat(meters, "m");
}

/** 本地日历日 YYYY-MM-DD（勿用 toISOString，UTC+8 会偏一天） */
function localDateISO() {
  var d = arguments.length > 0 && arguments[0] !== undefined ? arguments[0] : new Date();
  var y = d.getFullYear();
  var m = String(d.getMonth() + 1).padStart(2, '0');
  var day = String(d.getDate()).padStart(2, '0');
  return "".concat(y, "-").concat(m, "-").concat(day);
}

/** 相对本地日历日加减天数 */
function addLocalDays(isoDate, days) {
  var _isoDate$split$map = isoDate.split('-').map(Number),
    _isoDate$split$map2 = D_cinepass_front_leijieming_cinepass_front_node_modules_umijs_babel_preset_umi_node_modules_babel_runtime_helpers_slicedToArray_js__WEBPACK_IMPORTED_MODULE_0___default()(_isoDate$split$map, 3),
    y = _isoDate$split$map2[0],
    m = _isoDate$split$map2[1],
    d = _isoDate$split$map2[2];
  var dt = new Date(y, m - 1, d);
  dt.setDate(dt.getDate() + days);
  return localDateISO(dt);
}

/**
 * 订单座位展示文案：优先 seatPrices.seatName，其次客户端 seatNameById，最后 seatId。
 */
function formatOrderSeatLabels(order, seatNameById) {
  var _order$seatPrices;
  var ids = order.seatIds || [];
  if (!ids.length && (_order$seatPrices = order.seatPrices) !== null && _order$seatPrices !== void 0 && _order$seatPrices.length) {
    return order.seatPrices.map(function (p) {
      return p.seatName || (seatNameById === null || seatNameById === void 0 ? void 0 : seatNameById[p.seatId]) || p.seatId;
    }).filter(Boolean).join('、');
  }
  var nameFromSnapshot = new Map((order.seatPrices || []).filter(function (p) {
    return p.seatId && p.seatName;
  }).map(function (p) {
    return [p.seatId, p.seatName];
  }));
  return ids.map(function (id) {
    return nameFromSnapshot.get(id) || (seatNameById === null || seatNameById === void 0 ? void 0 : seatNameById[id]) || id;
  }).join('、');
}

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/utils/lanIp.ts":
/*!****************************!*\
  !*** ./src/utils/lanIp.ts ***!
  \****************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
/* harmony export */ __webpack_require__.d(__webpack_exports__, {
/* harmony export */   getLanIp: function() { return /* binding */ getLanIp; },
/* harmony export */   reachableHost: function() { return /* binding */ reachableHost; }
/* harmony export */ });
/* provided dependency */ var __react_refresh_utils__ = __webpack_require__(/*! ./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js */ "./node_modules/@umijs/react-refresh-webpack-plugin/lib/runtime/RefreshUtils.js");
__webpack_require__.$Refresh$.runtime = __webpack_require__(/*! ./node_modules/react-refresh/runtime.js */ "./node_modules/react-refresh/runtime.js");

/**
 * 局域网 IPv4 自动探测。
 *
 * 二维码里的地址（/m/pay、/m/redeem 等）必须能被顾客手机访问到，
 * 手机与电脑在同一局域网，所以 host 必须是本机局域网 IP，
 * 不能是 localhost / 127.0.0.1（手机会把 localhost 解析成手机自己，从而连不上）。
 *
 * 探测优先级：
 * 1. 构建/启动时由 .umirc.ts 通过 define 注入的 __LAN_IP__（用 os.networkInterfaces() 探测，最可靠）
 * 2. 运行时 WebRTC 探测（新版 Chrome 会返回 mDNS 而探测不到真实 IP，仅作兜底）
 *
 * __LAN_IP__ 的全局类型声明见 src/typings.d.ts。
 */

var cachedIp = null;
var webrtcStarted = false;

/** host 是否为本地回环（localhost / 127.x / 0.0.0.0 / [::1]） */
function isLoopback(host) {
  return host.startsWith('localhost') || host.startsWith('127.') || host.startsWith('0.0.0.0') || host.startsWith('[::1]') || host === '::1';
}

/** 是否为可被局域网手机访问的私网 IPv4（10.x / 172.16-31.x / 192.168.x） */
function isPrivateIpv4(ip) {
  return /^(10\.|192\.168\.|172\.(1[6-9]|2\d|3[01])\.)/.test(ip);
}

/** 同步读取局域网 IP：优先构建注入，其次 WebRTC 已探测到的结果 */
function getLanIp() {
  if (cachedIp) return cachedIp;
  if (true) {
    // 兼容 define 双重转义的历史值（首尾可能带一对字面量引号）
    var ip = "10.243.126.31".replace(/^"+|"+$/g, '');
    if (isPrivateIpv4(ip)) cachedIp = ip;
  }
  return cachedIp;
}

/** 模块加载时后台用 WebRTC 探测真实内网 IP（探测不到时静默回退） */
function startWebRtcDetect() {
  if (webrtcStarted || typeof RTCPeerConnection === 'undefined') return;
  webrtcStarted = true;
  try {
    var pc = new RTCPeerConnection({
      iceServers: []
    });
    pc.createDataChannel('');
    var settle = function settle(ip) {
      if (ip && isPrivateIpv4(ip) && !cachedIp) cachedIp = ip;
      try {
        pc.close();
      } catch (_unused) {
        /* noop */
      }
    };
    var timer = window.setTimeout(function () {
      return settle(null);
    }, 2000);
    pc.onicecandidate = function (e) {
      if (!e.candidate) {
        window.clearTimeout(timer);
        settle(null);
        return;
      }
      var m = /([0-9]{1,3}(\.[0-9]{1,3}){3})/.exec(e.candidate.candidate);
      if (m && isPrivateIpv4(m[1])) {
        window.clearTimeout(timer);
        settle(m[1]);
      }
    };
    pc.createOffer().then(function (o) {
      return pc.setLocalDescription(o);
    })["catch"](function () {
      window.clearTimeout(timer);
      settle(null);
    });
  } catch (_unused2) {
    /* WebRTC 不可用 */
  }
}
startWebRtcDetect();

/**
 * 把当前页面 host 中的本地回环地址替换为本机局域网 IP（保留端口）。
 * host 本身已是局域网 IP / 域名时原样返回。
 */
function reachableHost(host) {
  if (!isLoopback(host)) return host;
  var ip = getLanIp();
  if (!ip) return host;
  var port = window.location.port ? ":".concat(window.location.port) : '';
  return "".concat(ip).concat(port);
}

var $ReactRefreshModuleId$ = __webpack_require__.$Refresh$.moduleId;
var $ReactRefreshCurrentExports$ = __react_refresh_utils__.getModuleExports(
	$ReactRefreshModuleId$
);

function $ReactRefreshModuleRuntime$(exports) {
	if (true) {
		var errorOverlay;
		if (true) {
			errorOverlay = false;
		}
		var testMode;
		if (typeof __react_refresh_test__ !== 'undefined') {
			testMode = __react_refresh_test__;
		}
		return __react_refresh_utils__.executeRuntime(
			exports,
			$ReactRefreshModuleId$,
			module.hot,
			errorOverlay,
			testMode
		);
	}
}

if (typeof Promise !== 'undefined' && $ReactRefreshCurrentExports$ instanceof Promise) {
	$ReactRefreshCurrentExports$.then($ReactRefreshModuleRuntime$);
} else {
	$ReactRefreshModuleRuntime$($ReactRefreshCurrentExports$);
}

/***/ }),

/***/ "./src/components/BlankPlaceholder/BlankPlaceholder.less?modules":
/*!***********************************************************************!*\
  !*** ./src/components/BlankPlaceholder/BlankPlaceholder.less?modules ***!
  \***********************************************************************/
/***/ (function(module, __webpack_exports__, __webpack_require__) {

__webpack_require__.r(__webpack_exports__);
// extracted by mini-css-extract-plugin
/* harmony default export */ __webpack_exports__["default"] = ({"wrap":"wrap___rI7uC","grid":"grid___ExkLR","item":"item___YlJtJ","poster":"poster___zOxlg","card":"card___Icqmg","row":"row___b6azL","block":"block___iON3d"});
    if(true) {
      // 1786035068777
      var cssReload = __webpack_require__(/*! ../../../node_modules/@umijs/bundler-webpack/compiled/mini-css-extract-plugin/hmr/hotModuleReplacement.js */ "./node_modules/@umijs/bundler-webpack/compiled/mini-css-extract-plugin/hmr/hotModuleReplacement.js")(module.id, {"publicPath":"./","emit":true,"esModule":true,"locals":true});
      module.hot.dispose(cssReload);
      
    }
  

/***/ })

}]);
//# sourceMappingURL=p__me__orderDetail.async.js.map