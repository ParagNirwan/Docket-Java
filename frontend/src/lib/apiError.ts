export type ApiErrorResponse = {
  code: string;
  message: string;
  errors?: Record<string, string | string[]>;
};

export async function parseApiError(response: Response): Promise<ApiErrorResponse> {
  try {
    const data = await response.json();
    return data as ApiErrorResponse;
  } catch {
    return {
      code: "UNKNOWN_ERROR",
      message: "An unexpected error occurred on the server.",
    };
  }
}

export function getFieldError(errors: Record<string, string | string[]> | undefined, field: string): string | null {
  if (!errors || !errors[field]) return null;
  const error = errors[field];
  return Array.isArray(error) ? error[0] : error;
}
