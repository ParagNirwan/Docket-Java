import { toast } from "sonner";

export interface NotificationOptions {
  description?: string;
  duration?: number;
  action?: {
    label: string;
    onClick: () => void;
  };
}

export const notify = {
  success: (message: string, options?: NotificationOptions | string) => {
    const description = typeof options === "string" ? options : options?.description;
    const duration = typeof options === "object" ? options?.duration : undefined;
    const action = typeof options === "object" ? options?.action : undefined;

    return toast.success(message, {
      description,
      duration,
      action,
    });
  },

  error: (message: string, options?: NotificationOptions | string) => {
    const description = typeof options === "string" ? options : options?.description;
    const duration = typeof options === "object" ? options?.duration : undefined;
    const action = typeof options === "object" ? options?.action : undefined;

    return toast.error(message, {
      description,
      duration: duration || 5000,
      action,
    });
  },

  warning: (message: string, options?: NotificationOptions | string) => {
    const description = typeof options === "string" ? options : options?.description;
    const duration = typeof options === "object" ? options?.duration : undefined;
    const action = typeof options === "object" ? options?.action : undefined;

    return toast.warning(message, {
      description,
      duration: duration || 4500,
      action,
    });
  },

  info: (message: string, options?: NotificationOptions | string) => {
    const description = typeof options === "string" ? options : options?.description;
    const duration = typeof options === "object" ? options?.duration : undefined;
    const action = typeof options === "object" ? options?.action : undefined;

    return toast.info(message, {
      description,
      duration,
      action,
    });
  },

  uploadComplete: (fileName: string, folderName?: string) => {
    return toast.success("Upload complete", {
      description: folderName
        ? `"${fileName}" saved to ${folderName}`
        : `"${fileName}" uploaded successfully`,
      duration: 4000,
    });
  },

  uploadFailed: (fileName: string, error?: string) => {
    return toast.error("Upload failed", {
      description: error ? `"${fileName}": ${error}` : `Failed to upload "${fileName}"`,
      duration: 6000,
    });
  },

  dismiss: (id?: string | number) => {
    toast.dismiss(id);
  },
};

export { toast };

