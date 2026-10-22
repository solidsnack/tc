// The tests use the global Temporal, so they install the polyfill where the
// runtime has none.
import 'temporal-polyfill/global'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'

// The specification folder, found by walking up from the compiled tests.
export const specification = ((): string => {
    for (let dir = import.meta.dirname;; dir = dirname(dir)) {
        const candidate = join(dir, 'specification')

        if (existsSync(join(candidate, 'tests.yaml'))) {
            return candidate
        }

        if (dirname(dir) === dir) {
            throw new Error('The specification folder was not found.')
        }
    }
})()
