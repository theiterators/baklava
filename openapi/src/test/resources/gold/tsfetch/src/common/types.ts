export interface ErrorResponse {
  code: string;
  details?: string[];
  message: string;
}

export interface HealthResponse {
  status: string;
  uptimeSeconds: number;
}

export interface User {
  email: string;
  id: string;
  name: string;
  role: "admin" | "guest" | "member";
}
