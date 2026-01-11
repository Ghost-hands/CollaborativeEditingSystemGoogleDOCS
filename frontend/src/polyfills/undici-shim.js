// Shim for undici module to prevent "Cannot destructure property 'Request' of 'undefined'" error
// This provides a browser-compatible version of undici's Request, Response, and Headers

// Get browser-native APIs - try multiple sources
let RequestNative, ResponseNative, HeadersNative, FetchNative;

// Try globalThis first (set by index.html polyfill)
if (typeof globalThis !== 'undefined') {
  RequestNative = globalThis.Request;
  ResponseNative = globalThis.Response;
  HeadersNative = globalThis.Headers;
  FetchNative = globalThis.fetch;
}

// Try window
if (!RequestNative && typeof window !== 'undefined') {
  RequestNative = window.Request;
  ResponseNative = window.Response;
  HeadersNative = window.Headers;
  FetchNative = window.fetch;
}

// Try self (for web workers)
if (!RequestNative && typeof self !== 'undefined') {
  RequestNative = self.Request;
  ResponseNative = self.Response;
  HeadersNative = self.Headers;
  FetchNative = self.fetch;
}

// Fallback: if still undefined, try to get from global scope
if (!RequestNative && typeof Request !== 'undefined') {
  RequestNative = Request;
  ResponseNative = Response;
  HeadersNative = Headers;
  FetchNative = fetch;
}

// Final fallback: throw error if still not available
if (!RequestNative) {
  console.error('Request API not available. This should not happen in modern browsers.');
  // Create minimal fallback functions
  RequestNative = function Request() {
    throw new Error('Request API not available. Please use a modern browser.');
  };
  ResponseNative = function Response() {
    throw new Error('Response API not available. Please use a modern browser.');
  };
  HeadersNative = function Headers() {
    throw new Error('Headers API not available. Please use a modern browser.');
  };
  FetchNative = function fetch() {
    throw new Error('fetch API not available. Please use a modern browser.');
  };
}

// Create the shim object - ensure it's always an object, never undefined
const undiciShim = {
  Request: RequestNative,
  Response: ResponseNative,
  Headers: HeadersNative,
  fetch: FetchNative,
};

// Export named exports (ES modules)
export const Request = RequestNative;
export const Response = ResponseNative;
export const Headers = HeadersNative;
export const fetch = FetchNative;

// Export default (for default imports)
export default undiciShim;

// CommonJS compatibility
if (typeof module !== 'undefined' && module.exports) {
  module.exports = undiciShim;
  module.exports.Request = RequestNative;
  module.exports.Response = ResponseNative;
  module.exports.Headers = HeadersNative;
  module.exports.fetch = FetchNative;
  module.exports.default = undiciShim;
}

