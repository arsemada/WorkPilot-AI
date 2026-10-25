"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import { ApiError } from "@/lib/api/client";
import { listDocuments, downloadDocument } from "@/lib/api/documents";
import {
  addTaskComment,
  deleteTaskComment,
  linkTaskDocument,
  listTaskComments,
  listTaskDocuments,
  unlinkTaskDocument,
} from "@/lib/api/task-notes";
import type { OrganizationDocument, Task, TaskComment, TaskDocumentLink } from "@/lib/api/types";
import { formatActivityTime } from "@/lib/dashboard";
import { Button } from "@/components/ui/button";
import { Form, FormBanner, SelectField, TextAreaField } from "@/components/ui/field";
import { useToast } from "@/components/ui/toast";

export function TaskNotesPanel({
  organizationId,
  task,
  currentUserId,
  canUpdate,
  canDelete,
}: {
  organizationId: string;
  task: Task;
  currentUserId: string;
  canUpdate: boolean;
  canDelete: boolean;
}) {
  const { showToast } = useToast();
  const [comments, setComments] = useState<TaskComment[]>([]);
  const [links, setLinks] = useState<TaskDocumentLink[]>([]);
  const [library, setLibrary] = useState<OrganizationDocument[]>([]);
  const [body, setBody] = useState("");
  const [documentId, setDocumentId] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [savingComment, setSavingComment] = useState(false);
  const [savingLink, setSavingLink] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const [nextComments, nextLinks, documents] = await Promise.all([
        listTaskComments(organizationId, task.id),
        listTaskDocuments(organizationId, task.id),
        canUpdate ? listDocuments(organizationId, { size: 50, sort: "title", direction: "asc" }) : Promise.resolve(null),
      ]);
      setComments(nextComments);
      setLinks(nextLinks);
      if (documents) {
        setLibrary(documents.items);
      }
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "Notes could not be loaded.");
    } finally {
      setLoading(false);
    }
  }, [organizationId, task.id, canUpdate]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      void load();
    }, 0);
    return () => window.clearTimeout(handle);
  }, [load]);

  const available = library.filter((item) => !links.some((link) => link.documentId === item.id));

  async function onComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!body.trim()) {
      setError("Enter a comment.");
      return;
    }
    setSavingComment(true);
    setError(null);
    try {
      await addTaskComment(organizationId, task.id, body.trim());
      setBody("");
      showToast({ tone: "success", message: "Comment saved." });
      await load();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The comment could not be saved.");
    } finally {
      setSavingComment(false);
    }
  }

  async function onDeleteComment(comment: TaskComment) {
    try {
      await deleteTaskComment(organizationId, task.id, comment.id);
      showToast({ tone: "success", message: "Comment removed." });
      await load();
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "The comment could not be removed.",
      });
    }
  }

  async function onLink(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!documentId) {
      setError("Choose a document.");
      return;
    }
    setSavingLink(true);
    setError(null);
    try {
      await linkTaskDocument(organizationId, task.id, documentId);
      setDocumentId("");
      showToast({ tone: "success", message: "Document linked." });
      await load();
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The document could not be linked.");
    } finally {
      setSavingLink(false);
    }
  }

  async function onUnlink(link: TaskDocumentLink) {
    try {
      await unlinkTaskDocument(organizationId, task.id, link.documentId);
      showToast({ tone: "success", message: "Document unlinked." });
      await load();
    } catch (caught) {
      showToast({
        tone: "error",
        message: caught instanceof ApiError ? caught.message : "The document could not be unlinked.",
      });
    }
  }

  return (
    <div className="space-y-5">
      {error ? <FormBanner tone="error">{error}</FormBanner> : null}

      <section>
        <h3 className="text-sm font-semibold text-slate-950">Documents</h3>
        {loading && links.length === 0 ? <p className="mt-2 text-sm text-slate-700">Loading documents</p> : null}
        {links.length === 0 && !loading ? (
          <p className="mt-2 text-sm text-slate-700">No organization files are linked yet.</p>
        ) : (
          <ul className="mt-2 space-y-2">
            {links.map((link) => (
              <li key={link.id} className="flex flex-wrap items-center justify-between gap-2 text-sm">
                <div>
                  <p className="font-medium text-slate-950">{link.title}</p>
                  <p className="text-slate-600">{link.originalFileName}</p>
                </div>
                <div className="flex gap-2">
                  <Button
                    variant="secondary"
                    onClick={() =>
                      void downloadDocument(organizationId, {
                        id: link.documentId,
                        originalFileName: link.originalFileName,
                      })
                    }
                  >
                    Download
                  </Button>
                  {canUpdate ? (
                    <Button variant="ghost" onClick={() => void onUnlink(link)}>
                      Remove
                    </Button>
                  ) : null}
                </div>
              </li>
            ))}
          </ul>
        )}
        {canUpdate ? (
          <Form className="mt-3" onSubmit={(event) => void onLink(event)}>
            <SelectField
              id={`task-link-${task.id}`}
              label="Link a document"
              value={documentId}
              onChange={(event) => setDocumentId(event.target.value)}
            >
              <option value="">{available.length === 0 ? "No other files to link" : "Choose a file"}</option>
              {available.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.title}
                </option>
              ))}
            </SelectField>
            <div className="flex justify-end">
              <Button type="submit" loading={savingLink} disabled={!documentId}>
                Link document
              </Button>
            </div>
          </Form>
        ) : null}
      </section>

      <section>
        <h3 className="text-sm font-semibold text-slate-950">Comments</h3>
        {loading && comments.length === 0 ? <p className="mt-2 text-sm text-slate-700">Loading comments</p> : null}
        {comments.length === 0 && !loading ? (
          <p className="mt-2 text-sm text-slate-700">No comments yet.</p>
        ) : (
          <ul className="mt-2 space-y-3">
            {comments.map((comment) => (
              <li key={comment.id} className="rounded-lg border border-slate-200 bg-slate-50 px-3 py-2">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <p className="text-sm font-medium text-slate-950">{comment.authorName}</p>
                  <p className="text-xs text-slate-600">{formatActivityTime(comment.createdAt)}</p>
                </div>
                <p className="mt-1 whitespace-pre-wrap text-sm text-slate-800">{comment.body}</p>
                {comment.authorId === currentUserId || canDelete ? (
                  <div className="mt-2 flex justify-end">
                    <Button variant="ghost" onClick={() => void onDeleteComment(comment)}>
                      Remove
                    </Button>
                  </div>
                ) : null}
              </li>
            ))}
          </ul>
        )}
        {canUpdate ? (
          <Form className="mt-3" onSubmit={(event) => void onComment(event)}>
            <TextAreaField
              id={`task-comment-${task.id}`}
              label="Add a comment"
              value={body}
              onChange={(event) => setBody(event.target.value)}
              required
            />
            <div className="flex justify-end">
              <Button type="submit" loading={savingComment}>
                Save comment
              </Button>
            </div>
          </Form>
        ) : null}
      </section>
    </div>
  );
}
