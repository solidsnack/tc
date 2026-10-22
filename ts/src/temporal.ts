import { Temporal as polyfill } from 'temporal-polyfill'

/**
 * The runtime's own Temporal, where it has one, or else the polyfill. Types
 * come from TypeScript's `esnext.temporal` library either way.
 */
export const temporal: typeof Temporal = globalThis.Temporal ??
    (polyfill as unknown as typeof Temporal)
