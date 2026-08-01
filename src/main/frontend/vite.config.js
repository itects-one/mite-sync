import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The build writes into src/main/resources/static rather than straight into target/classes: an
// IDE that compiles the module itself replaces target/classes without ever running Maven, and the
// UI would silently disappear. From the resource folder it is copied along by whoever builds —
// Maven or the IDE. The directory is generated and gitignored; `mvn clean` removes it.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../resources/static',
    emptyOutDir: true,
  },
  test: {
    environment: 'jsdom',
    globals: true,
    restoreMocks: true,
  },
  server: {
    // `npm run dev` serves the UI on 5173 and forwards the API to the running app on 8080.
    // The basic-auth prompt comes from the proxied 401, same as in production.
    proxy: {
      '/proposals': 'http://localhost:8080',
      '/profiles': 'http://localhost:8080',
    },
  },
})
