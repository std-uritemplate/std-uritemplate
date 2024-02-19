import { defineConfig } from 'vitest/config'

export default defineConfig({
    test: {
        browser: {
            enabled: true,
            name: 'chrome',
            headless: true
        },
        environmentMatchGlobs: [
            ['src/index.test.ts', 'jsdom']
        ]
    },
})