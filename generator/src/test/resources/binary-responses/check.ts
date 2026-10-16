import {FetchTransport} from '@outfoxx/sunday';
import {createDownloadsAPI} from './downloads-api.js';
const client = createDownloadsAPI(new FetchTransport('https://example.com'));
const originalFetch = globalThis.fetch;
try {
  for (const media of ['application/json', 'text/plain; charset=iso-8859-1', 'image/png', 'application/x-download']) {
    for (const bytes of [new Uint8Array([0, 255, 34, 128]), new Uint8Array()]) {
      globalThis.fetch = async () => new Response(bytes, {headers: {'content-type': media}});
      for (const operation of [client.directDownload(), client.aliasDownload(), client.unionDownload(), client.nestedDownload()]) {
        for (const actual of [await operation.execute(), (await operation.response()).result]) {
          if (!(actual instanceof ArrayBuffer) || new Uint8Array(actual).toString() !== bytes.toString()) throw new Error('Download bytes changed: ' + media);
        }
      }
    }
  }
  globalThis.fetch = async () => new Response('"asset"', {headers: {'content-type': 'application/json'}});
  if (await client.mixedDownload().execute() !== 'asset') throw new Error('Mixed union skipped JSON decoding');
} finally {
  globalThis.fetch = originalFetch;
}
