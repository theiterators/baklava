export interface ErrorResponse {
  code: string;
  details?: string[];
  message: string;
}

export interface User {
  email: string;
  id: string;
  name: string;
  role: "admin" | "guest" | "member";
}
