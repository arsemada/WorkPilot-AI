export const DOCUMENT_ACCEPT = ".pdf,.txt,.csv,.md,.png,.jpg,.jpeg,.webp,.docx";
export const MAX_DOCUMENT_BYTES = 10 * 1024 * 1024;

const ALLOWED_EXTENSIONS = new Set(["pdf", "txt", "csv", "md", "png", "jpg", "jpeg", "webp", "docx"]);

export function documentExtension(fileName: string) {
  const dot = fileName.lastIndexOf(".");
  if (dot < 0 || dot === fileName.length - 1) {
    return "";
  }
  return fileName.slice(dot + 1).toLowerCase();
}

export function isAllowedDocument(file: File) {
  return ALLOWED_EXTENSIONS.has(documentExtension(file.name));
}

export function formatFileSize(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const kilobytes = bytes / 1024;
  if (kilobytes < 1024) {
    return `${kilobytes >= 10 ? Math.round(kilobytes) : kilobytes.toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export function formatUploadedAt(value: string) {
  return new Intl.DateTimeFormat("en", {
    dateStyle: "medium",
    timeStyle: "short",
    timeZone: "UTC",
  }).format(new Date(value));
}
