"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type FormEvent } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import {
  deleteDocument,
  downloadDocument,
  listDocuments,
  updateDocument,
  uploadDocument,
} from "@/lib/api/documents";
import type { DocumentPage, Membership, OrganizationDocument, Permission } from "@/lib/api/types";
import {
  DOCUMENT_ACCEPT,
  MAX_DOCUMENT_BYTES,
  formatFileSize,
  formatUploadedAt,
  isAllowedDocument,
} from "@/lib/documents";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Form, FormBanner, SelectField, TextAreaField, TextField } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";
import { DataTable } from "@/components/ui/table";
import { useToast } from "@/components/ui/toast";

const PAGE_SIZE = 10;

function hasPermission(membership: Membership | undefined, permission: Permission) {
  return membership?.permissions.includes(permission) ?? false;
}

export function DocumentsWorkspace() {
  const { user } = useAuth();
  const { showToast } = useToast();
  const fileRef = useRef<HTMLInputElement>(null);
  const memberships = useMemo(
    () => [...(user?.memberships ?? [])].sort((a, b) => a.organizationName.localeCompare(b.organizationName)),
    [user],
  );
  const [chosenOrganizationId, setChosenOrganizationId] = useState<string | null>(null);
  const storedOrganizationId = useSyncExternalStore(subscribeSelectedOrganization, readSelectedOrganization, () => null);
  const organizationId = useMemo(() => {
    const preferred = chosenOrganizationId ?? storedOrganizationId;
    const match = memberships.find((membership) => membership.organizationId === preferred);
    return (match ?? memberships[0])?.organizationId ?? "";
  }, [memberships, chosenOrganizationId, storedOrganizationId]);
  const [searchInput, setSearchInput] = useState("");
  const [query, setQuery] = useState("");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<DocumentPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [uploadOpen, setUploadOpen] = useState(false);
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [selected, setSelected] = useState<OrganizationDocument | null>(null);
  const [editing, setEditing] = useState(false);
  const [editTitle, setEditTitle] = useState("");
  const [editDescription, setEditDescription] = useState("");
  const [pendingDelete, setPendingDelete] = useState<OrganizationDocument | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setQuery((current) => (current === searchInput ? current : searchInput));
      setPage(0);
    }, 300);
    return () => window.clearTimeout(handle);
  }, [searchInput]);

  const membership = memberships.find((item) => item.organizationId === organizationId);
  const canCreate = hasPermission(membership, "RESOURCE_CREATE");
  const canUpdate = hasPermission(membership, "RESOURCE_UPDATE");
  const canDelete = hasPermission(membership, "RESOURCE_DELETE");

  const load = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const documents = await listDocuments(organizationId, {
        q: query,
        page,
        size: PAGE_SIZE,
        sort: "createdAt",
        direction: "desc",
      });
      setResult(documents);
      if (documents.totalPages > 0 && page > documents.totalPages - 1) {
        setPage(documents.totalPages - 1);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Documents could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [organizationId, query, page]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  function chooseOrganization(nextId: string) {
    writeSelectedOrganization(nextId);
    setChosenOrganizationId(nextId);
    setPage(0);
    setSelected(null);
  }

  function openUpload() {
    setTitle("");
    setDescription("");
    setFile(null);
    setFormError(null);
    if (fileRef.current) {
      fileRef.current.value = "";
    }
    setUploadOpen(true);
  }

  async function submitUpload(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!title.trim()) {
      setFormError("Enter a title.");
      return;
    }
    if (!file) {
      setFormError("Choose a file.");
      return;
    }
    if (!isAllowedDocument(file)) {
      setFormError("Use a PDF, text, CSV, Markdown, PNG, JPEG, WebP, or Word file.");
      return;
    }
    if (file.size > MAX_DOCUMENT_BYTES) {
      setFormError("File must be 10 MB or smaller.");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      await uploadDocument(organizationId, { title: title.trim(), description, file });
      showToast({ tone: "success", message: "Document uploaded." });
      setUploadOpen(false);
      setPage(0);
      await load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "The document could not be uploaded.");
    } finally {
      setSubmitting(false);
    }
  }

  function openEdit() {
    if (!selected) {
      return;
    }
    setEditTitle(selected.title);
    setEditDescription(selected.description ?? "");
    setFormError(null);
    setEditing(true);
  }

  async function submitEdit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!selected || !editTitle.trim()) {
      setFormError("Enter a title.");
      return;
    }
    setSubmitting(true);
    setFormError(null);
    try {
      const updated = await updateDocument(organizationId, selected.id, {
        title: editTitle.trim(),
        description: editDescription.trim() ? editDescription.trim() : null,
      });
      setSelected(updated);
      setEditing(false);
      showToast({ tone: "success", message: "Document updated." });
      await load();
    } catch (caught) {
      setFormError(caught instanceof ApiError ? caught.message : "The document could not be updated.");
    } finally {
      setSubmitting(false);
    }
  }

  async function saveDownload(document: OrganizationDocument) {
    setDownloading(true);
    try {
      await downloadDocument(organizationId, document);
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "The file could not be downloaded.",
      });
    } finally {
      setDownloading(false);
    }
  }

  async function confirmDelete() {
    if (!pendingDelete) {
      return;
    }
    setDeleting(true);
    try {
      await deleteDocument(organizationId, pendingDelete.id);
      showToast({ tone: "success", message: "Document deleted." });
      if (selected?.id === pendingDelete.id) {
        setSelected(null);
      }
      setPendingDelete(null);
      await load();
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "The document could not be deleted.",
      });
    } finally {
      setDeleting(false);
    }
  }

  if (memberships.length === 0) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">Documents</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an
          owner to add your account.
        </p>
      </section>
    );
  }

  const documents = result?.items ?? [];
  const filtersActive = Boolean(query);

  return (
    <div className="mx-auto max-w-6xl space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight text-slate-950">Documents</h1>
          <p className="mt-1 text-sm text-slate-700">Files stored for {membership?.organizationName}.</p>
        </div>
        <div className="flex flex-wrap items-end gap-3">
          {memberships.length > 1 ? (
            <SelectField
              id="document-organization"
              label="Organization"
              value={organizationId}
              onChange={(event) => chooseOrganization(event.target.value)}
            >
              {memberships.map((item) => (
                <option key={item.organizationId} value={item.organizationId}>
                  {item.organizationName}
                </option>
              ))}
            </SelectField>
          ) : null}
          {canCreate ? (
            <Button onClick={openUpload}>Upload document</Button>
          ) : null}
        </div>
      </div>

      <div className="max-w-md">
        <TextField
          id="document-search"
          label="Search"
          value={searchInput}
          placeholder="Title, file name, or text"
          onChange={(event) => setSearchInput(event.target.value)}
        />
      </div>

      {error ? <FormBanner tone="error">{error}</FormBanner> : null}
      {loading && !result ? (
        <div className="flex items-center gap-3 text-sm text-slate-700">
          <Spinner />
          Loading documents
        </div>
      ) : (
        <DataTable
          caption="Organization documents"
          rows={documents}
          getRowKey={(document) => document.id}
          emptyTitle={filtersActive ? "No matching documents" : "No documents yet"}
          emptyDescription={
            filtersActive
              ? "Change the search to see more documents."
              : "Upload a file to keep it with this organization."
          }
          columns={[
            {
              id: "title",
              header: "Document",
              cell: (document) => (
                <button
                  type="button"
                  className="text-left font-medium text-blue-800 underline"
                  onClick={() => setSelected(document)}
                >
                  {document.title}
                </button>
              ),
            },
            {
              id: "file",
              header: "File",
              cell: (document) => document.originalFileName,
            },
            {
              id: "size",
              header: "Size",
              cell: (document) => formatFileSize(document.sizeBytes),
            },
            {
              id: "uploaded",
              header: "Uploaded",
              cell: (document) => formatUploadedAt(document.createdAt),
            },
          ]}
        />
      )}

      {result && result.totalElements > 0 ? (
        <div className="flex flex-wrap items-center justify-between gap-3 text-sm text-slate-700">
          <p>
            Showing {result.page * result.size + 1}–
            {Math.min(result.totalElements, result.page * result.size + documents.length)} of {result.totalElements}
            {loading ? " · Updating" : ""}
          </p>
          <div className="flex gap-2">
            <Button variant="secondary" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
              Previous
            </Button>
            <Button
              variant="secondary"
              disabled={result.totalPages === 0 || page >= result.totalPages - 1}
              onClick={() => setPage((current) => current + 1)}
            >
              Next
            </Button>
          </div>
        </div>
      ) : null}

      <Dialog open={uploadOpen} title="Upload document" description="Add a file for this organization." onClose={() => setUploadOpen(false)}>
        <Form onSubmit={(event) => void submitUpload(event)}>
          {formError ? <FormBanner tone="error">{formError}</FormBanner> : null}
          <TextField id="upload-title" label="Title" value={title} required onChange={(event) => setTitle(event.target.value)} />
          <TextAreaField
            id="upload-description"
            label="Description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
          <div className="space-y-1">
            <label htmlFor="upload-file" className="block text-sm font-medium text-slate-800">
              File
            </label>
            <input
              ref={fileRef}
              id="upload-file"
              type="file"
              accept={DOCUMENT_ACCEPT}
              required
              className="block w-full text-sm text-slate-800"
              onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            />
            <p className="text-sm text-slate-600">PDF, text, CSV, Markdown, PNG, JPEG, WebP, or Word. Up to 10 MB.</p>
          </div>
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setUploadOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Upload
            </Button>
          </div>
        </Form>
      </Dialog>

      <Dialog
        open={selected !== null && !editing}
        title={selected?.title ?? "Document"}
        description={selected?.description ?? "No description."}
        onClose={() => setSelected(null)}
      >
        {selected ? (
          <div className="space-y-4">
            <dl className="grid gap-2 text-sm sm:grid-cols-2">
              <div>
                <dt className="text-slate-600">File</dt>
                <dd className="text-slate-950">{selected.originalFileName}</dd>
              </div>
              <div>
                <dt className="text-slate-600">Size</dt>
                <dd className="text-slate-950">{formatFileSize(selected.sizeBytes)}</dd>
              </div>
              <div>
                <dt className="text-slate-600">Uploaded by</dt>
                <dd className="text-slate-950">{selected.uploadedByName}</dd>
              </div>
              <div>
                <dt className="text-slate-600">Uploaded</dt>
                <dd className="text-slate-950">{formatUploadedAt(selected.createdAt)}</dd>
              </div>
            </dl>
            {selected.textPreview ? (
              <p className="whitespace-pre-wrap rounded-md bg-slate-50 p-3 text-sm text-slate-800">{selected.textPreview}</p>
            ) : (
              <p className="text-sm text-slate-600">Text from this file is not indexed. You can still download it.</p>
            )}
            <div className="flex flex-wrap justify-end gap-2">
              {canDelete ? (
                <Button variant="danger" onClick={() => setPendingDelete(selected)}>
                  Delete
                </Button>
              ) : null}
              <Button variant="secondary" loading={downloading} onClick={() => void saveDownload(selected)}>
                Download
              </Button>
              {canUpdate ? <Button onClick={openEdit}>Edit</Button> : null}
              <Button variant="secondary" onClick={() => setSelected(null)}>
                Close
              </Button>
            </div>
          </div>
        ) : null}
      </Dialog>

      <Dialog open={editing} title="Edit document" description="Change the title or description." onClose={() => setEditing(false)}>
        <Form onSubmit={(event) => void submitEdit(event)}>
          {formError ? <FormBanner tone="error">{formError}</FormBanner> : null}
          <TextField
            id="edit-document-title"
            label="Title"
            value={editTitle}
            required
            onChange={(event) => setEditTitle(event.target.value)}
          />
          <TextAreaField
            id="edit-document-description"
            label="Description"
            value={editDescription}
            onChange={(event) => setEditDescription(event.target.value)}
          />
          <div className="flex justify-end gap-2">
            <Button type="button" variant="secondary" onClick={() => setEditing(false)}>
              Cancel
            </Button>
            <Button type="submit" loading={submitting}>
              Save changes
            </Button>
          </div>
        </Form>
      </Dialog>

      <Dialog
        open={pendingDelete !== null}
        title="Delete document"
        description={pendingDelete ? `Delete “${pendingDelete.title}”? This cannot be undone.` : undefined}
        onClose={() => setPendingDelete(null)}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDelete(null)}>
            Cancel
          </Button>
          <Button variant="danger" loading={deleting} onClick={() => void confirmDelete()}>
            Delete document
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
