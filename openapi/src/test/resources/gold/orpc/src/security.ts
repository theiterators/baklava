export const securitySchemes = {
  apiKey: { type: "apiKey", in: "header", name: "X-API-Key", description: "API key for webhook delivery" },
  basicAuth: { type: "http", scheme: "basic", description: "HTTP Basic for the login endpoint only" },
  bearerAuth: { type: "http", scheme: "bearer", bearerFormat: "JWT", description: "JWT token issued by /auth/login" },
  oauth2: { type: "oauth2", flows: { authorizationCode: { authorizationUrl: "https://example.com/oauth/authorize", tokenUrl: "https://example.com/oauth/token", scopes: { "projects:read": "Read projects", "projects:write": "Create/update projects" } } } }
} as const;
