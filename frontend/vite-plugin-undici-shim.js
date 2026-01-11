import path from 'path';
import { fileURLToPath } from 'url';

const __dirname = path.dirname(fileURLToPath(import.meta.url));

// Vite plugin to replace undici imports with our shim at build time
export function undiciShimPlugin() {
  const shimPath = path.resolve(__dirname, 'src/polyfills/undici-shim.js');
  
  return {
    name: 'undici-shim',
    enforce: 'pre', // Run before other plugins
    resolveId(id, importer) {
      // Intercept undici imports and resolve to our shim
      if (id === 'undici' || id.startsWith('undici/')) {
        return shimPath;
      }
      return null;
    },
    buildStart() {
      // Ensure undici is never bundled as external
      this.addWatchFile(shimPath);
    },
    generateBundle(options, bundle) {
      // Post-process the bundle to replace any remaining undici references
      for (const fileName in bundle) {
        const chunk = bundle[fileName];
        if (chunk.type === 'chunk' && chunk.code) {
          let modified = false;
          let code = chunk.code;
          
          // More aggressive pattern matching for minified code
          // Pattern: const{Request}=undici or const{Request,Response}=undici (minified, no spaces)
          const minifiedPattern1 = /const\{([^}]+)\}=undici[;,]/g;
          if (minifiedPattern1.test(code)) {
            code = code.replace(minifiedPattern1, (match, props) => {
              modified = true;
              return `const{${props}}=(typeof globalThis!=="undefined"&&globalThis.undici)||(typeof window!=="undefined"&&window.undici)||{};`;
            });
          }
          
          // Pattern: var{Request}=undici or let{Request}=undici
          const minifiedPattern2 = /(var|let)\{([^}]+)\}=undici[;,]/g;
          if (minifiedPattern2.test(code)) {
            code = code.replace(minifiedPattern2, (match, keyword, props) => {
              modified = true;
              return `${keyword}{${props}}=(typeof globalThis!=="undefined"&&globalThis.undici)||(typeof window!=="undefined"&&window.undici)||{};`;
            });
          }
          
          // Pattern with spaces: const { Request } = undici
          const spacedPattern = /(const|var|let)\s*\{\s*([^}]+)\s*\}\s*=\s*undici\s*[;,]/g;
          if (spacedPattern.test(code)) {
            code = code.replace(spacedPattern, (match, keyword, props) => {
              modified = true;
              return `${keyword} { ${props} } = (typeof globalThis !== 'undefined' && globalThis.undici) || (typeof window !== 'undefined' && window.undici) || {};`;
            });
          }
          
          // Pattern: undici.Request or undici["Request"] (property access)
          const propertyPattern = /undici\.(Request|Response|Headers|fetch)/g;
          if (propertyPattern.test(code)) {
            code = code.replace(propertyPattern, (match, prop) => {
              modified = true;
              return `((typeof globalThis !== 'undefined' && globalThis.undici) || (typeof window !== 'undefined' && window.undici) || {}).${prop}`;
            });
          }
          
          // Pattern: undici["Request"] (bracket notation)
          const bracketPattern = /undici\[["'](Request|Response|Headers|fetch)["']\]/g;
          if (bracketPattern.test(code)) {
            code = code.replace(bracketPattern, (match, prop) => {
              modified = true;
              return `((typeof globalThis !== 'undefined' && globalThis.undici) || (typeof window !== 'undefined' && window.undici) || {})["${prop}"]`;
            });
          }
          
          if (modified) {
            chunk.code = code;
            console.log(`[undici-shim] Modified bundle chunk: ${fileName}`);
          }
        }
      }
    },
    transform(code, id) {
      // Skip if this is our shim file itself
      if (id.includes('undici-shim.js')) {
        return null;
      }
      
      // Replace any undici imports in the code
      if (code.includes('undici')) {
        let modified = false;
        let newCode = code;
        
        // Replace patterns like: const { Request } = require('undici')
        const requirePattern = /const\s*\{\s*([^}]+)\s*\}\s*=\s*require\(['"]undici['"]\)/g;
        if (requirePattern.test(newCode)) {
          newCode = newCode.replace(requirePattern, (match, props) => {
            modified = true;
            return `const { ${props} } = require('${shimPath}')`;
          });
        }
        
        // Replace patterns like: import { Request } from 'undici'
        const importPattern = /import\s*\{\s*([^}]+)\s*\}\s*from\s*['"]undici['"]/g;
        if (importPattern.test(newCode)) {
          newCode = newCode.replace(importPattern, (match, props) => {
            modified = true;
            return `import { ${props} } from '${shimPath}'`;
          });
        }
        
        // Replace default imports: import undici from 'undici'
        const defaultImportPattern = /import\s+(\w+)\s+from\s+['"]undici['"]/g;
        if (defaultImportPattern.test(newCode)) {
          newCode = newCode.replace(defaultImportPattern, (match, name) => {
            modified = true;
            return `import ${name} from '${shimPath}'`;
          });
        }
        
        // Replace require: const undici = require('undici')
        const requireDefaultPattern = /const\s+(\w+)\s*=\s*require\(['"]undici['"]\)/g;
        if (requireDefaultPattern.test(newCode)) {
          newCode = newCode.replace(requireDefaultPattern, (match, name) => {
            modified = true;
            return `const ${name} = require('${shimPath}')`;
          });
        }
        
        // Replace dynamic imports: import('undici')
        const dynamicImportPattern = /import\(['"]undici['"]\)/g;
        if (dynamicImportPattern.test(newCode)) {
          newCode = newCode.replace(dynamicImportPattern, () => {
            modified = true;
            return `import('${shimPath}')`;
          });
        }
        
        // Replace direct variable access like: const { Request } = undici;
        // This catches cases where undici is a variable that's undefined
        const destructurePattern = /const\s*\{\s*([^}]+)\s*\}\s*=\s*undici\s*[;,\n]/g;
        if (destructurePattern.test(newCode)) {
          newCode = newCode.replace(destructurePattern, (match, props) => {
            modified = true;
            // Replace with globalThis.undici to ensure it uses our polyfill
            return `const { ${props} } = (typeof globalThis !== 'undefined' && globalThis.undici) || (typeof window !== 'undefined' && window.undici) || {};`;
          });
        }
        
        // Also replace: var { Request } = undici; and let { Request } = undici;
        const varDestructurePattern = /(var|let)\s*\{\s*([^}]+)\s*\}\s*=\s*undici\s*[;,\n]/g;
        if (varDestructurePattern.test(newCode)) {
          newCode = newCode.replace(varDestructurePattern, (match, keyword, props) => {
            modified = true;
            return `${keyword} { ${props} } = (typeof globalThis !== 'undefined' && globalThis.undici) || (typeof window !== 'undefined' && window.undici) || {};`;
          });
        }
        
        if (modified) {
          return { code: newCode, map: null };
        }
      }
      return null;
    },
  };
}

