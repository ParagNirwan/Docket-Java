import {
  File,
  FileText,
  FileSpreadsheet,
  FileCode,
  FileImage,
  FileArchive,
  type LucideIcon,
} from "lucide-react";

export type FileCategory =
  | "pdf"
  | "sheet"
  | "doc"
  | "image"
  | "archive"
  | "code"
  | "other";

export interface FileTypeInfo {
  category: FileCategory;
  extension: string;
  label: string;
  icon: LucideIcon;
  colorClass: string;
  bgClass: string;
  borderClass: string;
  badgeClass: string;
}

export function getFileExtension(filename: string): string {
  const dotIndex = filename.lastIndexOf(".");
  if (dotIndex === -1 || dotIndex === filename.length - 1) return "";
  return filename.substring(dotIndex + 1).toLowerCase();
}

export function getFileTypeInfo(
  filename: string,
  contentType?: string,
): FileTypeInfo {
  const ext = getFileExtension(filename);
  const type = (contentType || "").toLowerCase();

  // PDF
  if (ext === "pdf" || type.includes("pdf")) {
    return {
      category: "pdf",
      extension: "PDF",
      label: "PDF Document",
      icon: FileText,
      colorClass: "text-rose-600 dark:text-rose-400",
      bgClass: "bg-rose-50 dark:bg-rose-950/40",
      borderClass: "border-rose-200 dark:border-rose-900/50",
      badgeClass:
        "bg-rose-100/80 text-rose-700 dark:bg-rose-950/60 dark:text-rose-300",
    };
  }

  // Spreadsheets
  if (
    ["xls", "xlsx", "csv"].includes(ext) ||
    type.includes("sheet") ||
    type.includes("csv") ||
    type.includes("excel")
  ) {
    return {
      category: "sheet",
      extension: ext ? ext.toUpperCase() : "XLS",
      label: ext === "csv" ? "CSV Spreadsheet" : "Excel Spreadsheet",
      icon: FileSpreadsheet,
      colorClass: "text-emerald-600 dark:text-emerald-400",
      bgClass: "bg-emerald-50 dark:bg-emerald-950/40",
      borderClass: "border-emerald-200 dark:border-emerald-900/50",
      badgeClass:
        "bg-emerald-100/80 text-emerald-700 dark:bg-emerald-950/60 dark:text-emerald-300",
    };
  }

  // Word / Text docs
  if (
    ["doc", "docx", "rtf"].includes(ext) ||
    type.includes("word") ||
    type.includes("document")
  ) {
    return {
      category: "doc",
      extension: ext ? ext.toUpperCase() : "DOC",
      label: "Word Document",
      icon: FileText,
      colorClass: "text-blue-600 dark:text-blue-400",
      bgClass: "bg-blue-50 dark:bg-blue-950/40",
      borderClass: "border-blue-200 dark:border-blue-900/50",
      badgeClass:
        "bg-blue-100/80 text-blue-700 dark:bg-blue-950/60 dark:text-blue-300",
    };
  }

  // Images
  if (
    ["png", "jpg", "jpeg", "gif", "svg", "webp"].includes(ext) ||
    type.startsWith("image/")
  ) {
    return {
      category: "image",
      extension: ext ? ext.toUpperCase() : "IMG",
      label: "Image File",
      icon: FileImage,
      colorClass: "text-purple-600 dark:text-purple-400",
      bgClass: "bg-purple-50 dark:bg-purple-950/40",
      borderClass: "border-purple-200 dark:border-purple-900/50",
      badgeClass:
        "bg-purple-100/80 text-purple-700 dark:bg-purple-950/60 dark:text-purple-300",
    };
  }

  // Archives
  if (
    ["zip", "rar", "7z", "tar", "gz"].includes(ext) ||
    type.includes("zip") ||
    type.includes("compressed") ||
    type.includes("archive")
  ) {
    return {
      category: "archive",
      extension: ext ? ext.toUpperCase() : "ZIP",
      label: "Compressed Archive",
      icon: FileArchive,
      colorClass: "text-amber-600 dark:text-amber-400",
      bgClass: "bg-amber-50 dark:bg-amber-950/40",
      borderClass: "border-amber-200 dark:border-amber-900/50",
      badgeClass:
        "bg-amber-100/80 text-amber-700 dark:bg-amber-950/60 dark:text-amber-300",
    };
  }

  // Code / Plain Text
  if (
    ["txt", "json", "xml", "html", "md", "js", "ts", "java"].includes(ext) ||
    type.startsWith("text/")
  ) {
    return {
      category: "code",
      extension: ext ? ext.toUpperCase() : "TXT",
      label: ext === "txt" ? "Text Document" : "Code / Data File",
      icon: FileCode,
      colorClass: "text-slate-600 dark:text-slate-300",
      bgClass: "bg-slate-50 dark:bg-slate-800/60",
      borderClass: "border-slate-200 dark:border-slate-700",
      badgeClass:
        "bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-300",
    };
  }

  // Fallback
  return {
    category: "other",
    extension: ext ? ext.toUpperCase() : "FILE",
    label: "Document",
    icon: File,
    colorClass: "text-indigo-600 dark:text-indigo-400",
    bgClass: "bg-indigo-50 dark:bg-indigo-950/40",
    borderClass: "border-indigo-200 dark:border-indigo-900/50",
    badgeClass:
      "bg-indigo-100/80 text-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300",
  };
}

