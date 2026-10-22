"use client";

import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore, type FormEvent, type KeyboardEvent } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { ApiError } from "@/lib/api/client";
import {
  createConversation,
  deleteConversation,
  getConversation,
  listConversations,
  sendConversationMessage,
} from "@/lib/api/conversations";
import type { Conversation, ConversationDetail, Membership } from "@/lib/api/types";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";
import { Button } from "@/components/ui/button";
import { Dialog } from "@/components/ui/dialog";
import { Form, FormBanner, SelectField, TextAreaField } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";

const MAX_MESSAGE_CHARS = 4000;

export function AssistantWorkspace() {
  const { user } = useAuth();
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
  const membership = memberships.find((item) => item.organizationId === organizationId);
  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ConversationDetail | null>(null);
  const [listError, setListError] = useState<string | null>(null);
  const [threadError, setThreadError] = useState<string | null>(null);
  const [loadingList, setLoadingList] = useState(false);
  const [loadingThread, setLoadingThread] = useState(false);
  const [creating, setCreating] = useState(false);
  const [draft, setDraft] = useState("");
  const [sending, setSending] = useState(false);
  const [sendError, setSendError] = useState<string | null>(null);
  const [pendingDelete, setPendingDelete] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const threadEndRef = useRef<HTMLDivElement>(null);

  const loadConversations = useCallback(async () => {
    if (!organizationId) {
      return;
    }
    setLoadingList(true);
    setListError(null);
    try {
      const items = await listConversations(organizationId);
      setConversations(items);
      setSelectedId((current) => (current && items.some((item) => item.id === current) ? current : items[0]?.id ?? null));
    } catch (caught) {
      setListError(caught instanceof ApiError ? caught.message : "Conversations could not be loaded.");
    } finally {
      setLoadingList(false);
    }
  }, [organizationId]);

  useEffect(() => {
    const handle = window.setTimeout(() => {
      setSelectedId(null);
      setDetail(null);
      setDraft("");
      setSendError(null);
      void loadConversations();
    }, 0);
    return () => window.clearTimeout(handle);
  }, [loadConversations]);

  useEffect(() => {
    if (!organizationId || !selectedId) {
      return;
    }
    let cancelled = false;
    const handle = window.setTimeout(() => {
      setLoadingThread(true);
      setThreadError(null);
      void getConversation(organizationId, selectedId)
        .then((conversation) => {
          if (!cancelled) {
            setDetail((current) =>
              current?.id === conversation.id && current.messages.length > conversation.messages.length ? current : conversation,
            );
          }
        })
        .catch((caught: unknown) => {
          if (!cancelled) {
            setDetail(null);
            setThreadError(caught instanceof ApiError ? caught.message : "The conversation could not be loaded.");
          }
        })
        .finally(() => {
          if (!cancelled) {
            setLoadingThread(false);
          }
        });
    }, 0);
    return () => {
      cancelled = true;
      window.clearTimeout(handle);
    };
  }, [organizationId, selectedId]);

  useEffect(() => {
    threadEndRef.current?.scrollIntoView({ block: "end" });
  }, [detail?.messages.length, sending]);

  function chooseOrganization(nextOrganizationId: string) {
    setChosenOrganizationId(nextOrganizationId);
    writeSelectedOrganization(nextOrganizationId);
  }

  async function startConversation() {
    setCreating(true);
    setListError(null);
    try {
      const created = await createConversation(organizationId);
      setConversations((current) => [created, ...current.filter((item) => item.id !== created.id)]);
      setSelectedId(created.id);
      setDraft("");
      setSendError(null);
    } catch (caught) {
      setListError(caught instanceof ApiError ? caught.message : "A conversation could not be started.");
    } finally {
      setCreating(false);
    }
  }

  async function send(content: string) {
    const message = content.trim();
    if (!selectedId || !message || sending) {
      return;
    }
    setSending(true);
    setSendError(null);
    try {
      const updated = await sendConversationMessage(organizationId, selectedId, message);
      setDetail(updated);
      setDraft("");
      setConversations((current) =>
        [
          {
            id: updated.id,
            organizationId: updated.organizationId,
            title: updated.title,
            createdAt: updated.createdAt,
            updatedAt: updated.updatedAt,
          },
          ...current.filter((item) => item.id !== updated.id),
        ],
      );
    } catch (caught) {
      setSendError(caught instanceof ApiError ? caught.message : "The message could not be sent.");
    } finally {
      setSending(false);
    }
  }

  function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void send(draft);
  }

  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      void send(draft);
    }
  }

  async function confirmDelete() {
    if (!selectedId) {
      return;
    }
    setDeleting(true);
    try {
      await deleteConversation(organizationId, selectedId);
      const remaining = conversations.filter((item) => item.id !== selectedId);
      setConversations(remaining);
      setSelectedId(remaining[0]?.id ?? null);
      setDetail(null);
      setDraft("");
      setPendingDelete(false);
      setSendError(null);
    } catch (caught) {
      setSendError(caught instanceof ApiError ? caught.message : "The conversation could not be deleted.");
    } finally {
      setDeleting(false);
    }
  }

  if (memberships.length === 0) {
    return (
      <section className="mx-auto max-w-3xl">
        <h1 className="text-2xl font-semibold tracking-tight text-slate-950">AI Assistant</h1>
        <p className="mt-4 text-base leading-7 text-slate-700">
          You are not a member of an organization yet. Register with an organization name, or ask an owner to add your
          account.
        </p>
      </section>
    );
  }

  const activeTitle = detail?.title ?? conversations.find((item) => item.id === selectedId)?.title ?? "Conversation";

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-4 lg:h-[calc(100vh-8rem)] lg:flex-row">
      <aside className="flex max-h-80 flex-col gap-3 rounded-lg border border-slate-200 bg-white p-3 lg:max-h-none lg:w-72">
        <div className="flex items-center justify-between gap-2">
          <h1 className="text-lg font-semibold text-slate-950">AI Assistant</h1>
          {loadingList ? <Spinner /> : null}
        </div>
        {memberships.length > 1 ? (
          <SelectField
            id="assistant-organization"
            label="Organization"
            value={organizationId}
            onChange={(event) => chooseOrganization(event.target.value)}
          >
            {memberships.map((item: Membership) => (
              <option key={item.organizationId} value={item.organizationId}>
                {item.organizationName}
              </option>
            ))}
          </SelectField>
        ) : (
          <p className="text-sm text-slate-700">{membership?.organizationName}</p>
        )}
        <Button loading={creating} onClick={() => void startConversation()}>
          New conversation
        </Button>
        {listError ? <FormBanner tone="error">{listError}</FormBanner> : null}
        <div className="min-h-0 flex-1 space-y-1 overflow-y-auto" aria-label="Conversations">
          {conversations.length === 0 && !loadingList ? (
            <p className="px-2 py-3 text-sm text-slate-600">No conversations yet.</p>
          ) : null}
          {conversations.map((conversation) => {
            const selected = conversation.id === selectedId;
            return (
              <button
                key={conversation.id}
                type="button"
                aria-current={selected ? "true" : undefined}
                className={
                  selected
                    ? "block w-full rounded-md bg-blue-50 px-3 py-2 text-left text-sm font-semibold text-blue-950"
                    : "block w-full rounded-md px-3 py-2 text-left text-sm text-slate-800 hover:bg-slate-50"
                }
                onClick={() => setSelectedId(conversation.id)}
              >
                {conversation.title}
              </button>
            );
          })}
        </div>
      </aside>

      <section className="flex min-h-[32rem] flex-1 flex-col rounded-lg border border-slate-200 bg-white">
        <div className="flex items-center justify-between gap-3 border-b border-slate-200 px-4 py-3">
          <h2 className="truncate text-base font-semibold text-slate-950">{selectedId ? activeTitle : "Conversation"}</h2>
          {selectedId ? (
            <Button variant="secondary" onClick={() => setPendingDelete(true)}>
              Delete
            </Button>
          ) : null}
        </div>
        <div className="flex-1 space-y-3 overflow-y-auto px-4 py-4" role="log" aria-live="polite" aria-relevant="additions" aria-busy={sending || loadingThread}>
          {!selectedId ? (
            <p className="text-sm leading-6 text-slate-700">Start a conversation to ask a question.</p>
          ) : null}
          {loadingThread ? <Spinner /> : null}
          {threadError ? <FormBanner tone="error">{threadError}</FormBanner> : null}
          {detail?.messages.map((message) => {
            const fromUser = message.role === "USER";
            return (
              <article
                key={message.id}
                aria-label={fromUser ? "You" : "Assistant"}
                className={fromUser ? "ml-auto max-w-[85%] rounded-lg bg-blue-50 px-3 py-2" : "mr-auto max-w-[85%] rounded-lg bg-slate-50 px-3 py-2"}
              >
                <p className="text-xs font-semibold uppercase tracking-wide text-slate-600">{fromUser ? "You" : "Assistant"}</p>
                <p className="mt-1 whitespace-pre-wrap break-words text-sm leading-6 text-slate-900">{message.content}</p>
              </article>
            );
          })}
          {sending ? (
            <p className="text-sm text-slate-600" role="status">
              The assistant is writing a reply.
            </p>
          ) : null}
          <div ref={threadEndRef} />
        </div>
        <Form className="border-t border-slate-200 p-4" onSubmit={onSubmit}>
          {sendError ? (
            <div className="space-y-2">
              <FormBanner tone="error">{sendError}</FormBanner>
              <Button variant="secondary" disabled={sending || !draft.trim()} onClick={() => void send(draft)}>
                Try again
              </Button>
            </div>
          ) : null}
          <TextAreaField
            id="assistant-message"
            label="Message"
            value={draft}
            maxLength={MAX_MESSAGE_CHARS}
            disabled={!selectedId || sending}
            placeholder={selectedId ? "Ask a question" : "Start a conversation first"}
            hint="Enter sends the message. Shift+Enter adds a line. The assistant only sees this conversation."
            onChange={(event) => setDraft(event.target.value)}
            onKeyDown={onKeyDown}
          />
          <div className="flex justify-end">
            <Button type="submit" loading={sending} disabled={!selectedId || !draft.trim()}>
              Send
            </Button>
          </div>
        </Form>
      </section>

      <Dialog
        open={pendingDelete}
        title="Delete conversation"
        description={activeTitle ? `Delete “${activeTitle}”? This cannot be undone.` : "Delete this conversation?"}
        onClose={() => setPendingDelete(false)}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingDelete(false)}>
            Cancel
          </Button>
          <Button variant="danger" loading={deleting} onClick={() => void confirmDelete()}>
            Delete conversation
          </Button>
        </div>
      </Dialog>
    </div>
  );
}
