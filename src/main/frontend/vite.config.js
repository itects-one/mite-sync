import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// The build writes straight into the Spring Boot classpath, so `mvn spring-boot:run` and the
// packaged jar both serve the UI from / without an extra copy step.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../../../target/classes/static',
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
