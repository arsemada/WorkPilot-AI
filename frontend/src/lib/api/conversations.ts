import { apiRequest } from "@/lib/api/client";
import type { Conversation, ConversationDetail } from "@/lib/api/types";

export function listConversations(organizationId: string) {
  return apiRequest<Conversation[]>(`/api/organizations/${organizationId}/conversations`, {
    session: "required",
  });
}

export function createConversation(organizationId: string) {
  return apiRequest<Conversation>(`/api/organizations/${organizationId}/conversations`, {
    method: "POST",
    body: {},
    session: "required",
  });
}

export function getConversation(organizationId: string, conversationId: string) {
  return apiRequest<ConversationDetail>(`/api/organizations/${organizationId}/conversations/${conversationId}`, {
    session: "required",
  });
}

export function sendConversationMessage(organizationId: string, conversationId: string, content: string) {
  return apiRequest<ConversationDetail>(
    `/api/organizations/${organizationId}/conversations/${conversationId}/messages`,
    {
      method: "POST",
      body: { content },
      session: "required",
    },
  );
}

export function deleteConversation(organizationId: string, conversationId: string) {
  return apiRequest<void>(`/api/organizations/${organizationId}/conversations/${conversationId}`, {
    method: "DELETE",
    session: "required",
  });
}
