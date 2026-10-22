import type { ReactNode } from "react";

export type Column<T> = {
  id: string;
  header: string;
  cell: (row: T) => ReactNode;
};

type DataTableProps<T> = {
  columns: Column<T>[];
  rows: T[];
  getRowKey: (row: T) => string;
  emptyTitle: string;
  emptyDescription: string;
  caption: string;
};

export function DataTable<T>({
  columns,
  rows,
  getRowKey,
  emptyTitle,
  emptyDescription,
  caption,
}: DataTableProps<T>) {
  if (rows.length === 0) {
    return (
      <div className="empty-panel px-4 py-12">
        <p className="font-medium text-slate-950">{emptyTitle}</p>
        <p className="mt-1 text-sm text-slate-700">{emptyDescription}</p>
      </div>
    );
  }

  return (
    <div className="panel overflow-x-auto">
      <table className="min-w-full border-collapse text-left text-sm">
        <caption className="sr-only">{caption}</caption>
        <thead className="text-slate-500">
          <tr>
            {columns.map((column) => (
              <th key={column.id} scope="col" className="px-4 py-3 text-xs font-semibold tracking-wide uppercase">
                {column.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={getRowKey(row)} className="border-t border-slate-100 transition-colors hover:bg-slate-50/80">
              {columns.map((column) => (
                <td key={column.id} className="px-4 py-3.5 align-middle text-slate-950">
                  {column.cell(row)}
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
