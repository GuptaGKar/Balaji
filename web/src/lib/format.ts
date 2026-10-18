import { format } from "date-fns";

export function formatDateTime(iso: string): string {
  return format(new Date(iso), "dd MMM yyyy, HH:mm");
}

export function formatDate(iso: string): string {
  return format(new Date(iso), "dd MMM yyyy");
}

export function formatTime(iso: string): string {
  return format(new Date(iso), "HH:mm");
}

export function formatRelativeTime(iso: string): string {
  const date = new Date(iso);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMins = Math.floor(diffMs / 60000);
  const diffHours = Math.floor(diffMs / 3600000);
  const diffDays = Math.floor(diffMs / 86400000);

  if (diffMins < 1) return "just now";
  if (diffMins < 60) return `${diffMins}m ago`;
  if (diffHours < 24) return `${diffHours}h ago`;
  if (diffDays < 7) return `${diffDays}d ago`;
  return formatDate(iso);
}