"use client";

import { useEffect, useMemo, useState, useSyncExternalStore, type FormEvent } from "react";
import { useAuth } from "@/components/auth/auth-provider";
import { Button } from "@/components/ui/button";
import { Form, FormBanner, SelectField, TextField } from "@/components/ui/field";
import { Spinner } from "@/components/ui/loading";
import { Dialog } from "@/components/ui/dialog";
import { StatusBadge, roleTone } from "@/components/ui/status-badge";
import { useToast } from "@/components/ui/toast";
import { ApiError } from "@/lib/api/client";
import { addMember, removeMember, updateMemberRole, updateOrganization } from "@/lib/api/organizations";
import { listMembers } from "@/lib/api/tasks";
import type { Membership, OrganizationRole, Permission } from "@/lib/api/types";
import {
  readSelectedOrganization,
  subscribeSelectedOrganization,
  writeSelectedOrganization,
} from "@/lib/selected-organization";

const ROLE_LABELS: Record<OrganizationRole, string> = {
  OWNER: "Owner",
  ADMIN: "Admin",
  MEMBER: "Member",
};

function hasPermission(membership: Membership | undefined, permission: Permission) {
  return membership?.permissions.includes(permission) ?? false;
}

function canManage(actor: OrganizationRole | undefined, target: OrganizationRole) {
  if (actor === "OWNER") {
    return true;
  }
  return actor === "ADMIN" && target === "MEMBER";
}

function inviteRoles(actor: OrganizationRole | undefined): OrganizationRole[] {
  if (actor === "OWNER") {
    return ["MEMBER", "ADMIN", "OWNER"];
  }
  if (actor === "ADMIN") {
    return ["MEMBER"];
  }
  return [];
}

export function SettingsWorkspace() {
  const { user, saveProfile, refreshProfile } = useAuth();
  const { showToast } = useToast();
  const memberships = useMemo(
    () => [...(user?.memberships ?? [])].sort((a, b) => a.organizationName.localeCompare(b.organizationName)),
    [user],
  );
  const [chosenOrganizationId, setChosenOrganizationId] = useState<string | null>(null);
  const storedOrganizationId = useSyncExternalStore(
    subscribeSelectedOrganization,
    readSelectedOrganization,
    () => null,
  );
  const organizationId = useMemo(() => {
    const preferred = chosenOrganizationId ?? storedOrganizationId;
    const match = memberships.find((membership) => membership.organizationId === preferred);
    return (match ?? memberships[0])?.organizationId ?? "";
  }, [memberships, chosenOrganizationId, storedOrganizationId]);
  const membership = memberships.find((item) => item.organizationId === organizationId);

  const [members, setMembers] = useState<Membership[]>([]);
  const [membersError, setMembersError] = useState<string | null>(null);
  const [loadingMembers, setLoadingMembers] = useState(false);
  const [inviteEmail, setInviteEmail] = useState("");
  const [inviteRole, setInviteRole] = useState<OrganizationRole>("MEMBER");
  const [inviteError, setInviteError] = useState<string | null>(null);
  const [inviting, setInviting] = useState(false);
  const [pendingMemberId, setPendingMemberId] = useState<string | null>(null);
  const [pendingRemove, setPendingRemove] = useState<Membership | null>(null);
  const [removing, setRemoving] = useState(false);

  useEffect(() => {
    if (!organizationId) {
      return;
    }
    let active = true;
    const handle = window.setTimeout(() => {
      setLoadingMembers(true);
      setMembersError(null);
      void listMembers(organizationId)
        .then((next) => {
          if (active) {
            setMembers(next);
          }
        })
        .catch((caught: unknown) => {
          if (!active) {
            return;
          }
          setMembers([]);
          setMembersError(caught instanceof ApiError ? caught.message : "The members could not be loaded.");
        })
        .finally(() => {
          if (active) {
            setLoadingMembers(false);
          }
        });
    }, 0);
    return () => {
      active = false;
      window.clearTimeout(handle);
    };
  }, [organizationId]);

  const canRename = hasPermission(membership, "ORG_UPDATE");
  const canInvite = hasPermission(membership, "MEMBER_INVITE");
  const canUpdateRole = hasPermission(membership, "MEMBER_UPDATE");
  const canRemove = hasPermission(membership, "MEMBER_REMOVE");
  const roles = inviteRoles(membership?.role);

  async function invite(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!organizationId) {
      return;
    }
    if (!inviteEmail.trim()) {
      setInviteError("Enter an email.");
      return;
    }
    setInviteError(null);
    setInviting(true);
    try {
      await addMember(organizationId, inviteEmail.trim(), inviteRole);
      setInviteEmail("");
      setInviteRole("MEMBER");
      setMembers(await listMembers(organizationId));
      showToast({ tone: "success", message: "Member added." });
    } catch (caught) {
      setInviteError(caught instanceof ApiError ? caught.message : "The member could not be added.");
    } finally {
      setInviting(false);
    }
  }

  async function changeRole(member: Membership, role: OrganizationRole) {
    if (role === member.role) {
      return;
    }
    setPendingMemberId(member.userId);
    setMembersError(null);
    try {
      const updated = await updateMemberRole(organizationId, member.userId, role);
      setMembers((current) => current.map((item) => (item.userId === member.userId ? updated : item)));
      if (member.userId === user?.id) {
        await refreshProfile();
      }
      showToast({ tone: "success", message: "Role updated." });
    } catch (caught) {
      setMembersError(caught instanceof ApiError ? caught.message : "The role could not be changed.");
    } finally {
      setPendingMemberId(null);
    }
  }

  async function confirmRemove() {
    if (!pendingRemove) {
      return;
    }
    setRemoving(true);
    setMembersError(null);
    try {
      await removeMember(organizationId, pendingRemove.userId);
      const removedSelf = pendingRemove.userId === user?.id;
      setPendingRemove(null);
      if (removedSelf) {
        const profile = await refreshProfile();
        const remaining = profile.memberships[0]?.organizationId;
        if (remaining) {
          writeSelectedOrganization(remaining);
          setChosenOrganizationId(remaining);
        }
      } else {
        setMembers((current) => current.filter((item) => item.userId !== pendingRemove.userId));
      }
      showToast({ tone: "success", message: "Member removed." });
    } catch (caught) {
      setMembersError(caught instanceof ApiError ? caught.message : "The member could not be removed.");
    } finally {
      setRemoving(false);
    }
  }

  return (
    <div className="mx-auto flex max-w-3xl flex-col gap-6">
      <div>
        <h1 className="page-title">Settings</h1>
        <p className="mt-2 text-sm leading-6 text-slate-700">
          Your account, and the people who belong to {membership?.organizationName ?? "your organization"}.
        </p>
      </div>

      {user ? <AccountForm key={user.id} user={user} saveProfile={saveProfile} /> : null}

      {memberships.length === 0 ? (
        <section className="empty-panel px-4 py-12">
          <p className="font-medium text-slate-950">No organization yet</p>
          <p className="mt-1 text-sm text-slate-700">
            Register with an organization name, or ask an owner to add your account.
          </p>
        </section>
      ) : (
        <section className="panel p-5">
          <div className="flex flex-wrap items-end justify-between gap-4">
            <div>
              <h2 className="text-lg font-semibold text-slate-950">Organization</h2>
              <p className="mt-1 text-sm text-slate-600">Your role is {ROLE_LABELS[membership?.role ?? "MEMBER"]}.</p>
            </div>
            {memberships.length > 1 ? (
              <SelectField
                id="settings-organization"
                label="Organization"
                value={organizationId}
                onChange={(event) => {
                  const nextId = event.target.value;
                  writeSelectedOrganization(nextId);
                  setChosenOrganizationId(nextId);
                }}
              >
                {memberships.map((item) => (
                  <option key={item.organizationId} value={item.organizationId}>
                    {item.organizationName}
                  </option>
                ))}
              </SelectField>
            ) : null}
          </div>

          {canRename && membership ? (
            <OrganizationNameForm
              key={membership.organizationId}
              organizationId={membership.organizationId}
              initialName={membership.organizationName}
              onSaved={async () => {
                await refreshProfile();
              }}
            />
          ) : (
            <p className="mt-4 text-sm text-slate-700">{membership?.organizationName}</p>
          )}

          <h3 className="mt-8 text-base font-semibold text-slate-950">Members</h3>
          {membersError ? <div className="mt-3"><FormBanner tone="error">{membersError}</FormBanner></div> : null}
          {loadingMembers && members.length === 0 ? (
            <div className="mt-3 flex items-center gap-3 text-sm text-slate-700" role="status">
              <Spinner />
              Loading members
            </div>
          ) : null}
          <ul className="mt-3 space-y-3">
            {members.map((member) => {
              const manageable = canManage(membership?.role, member.role);
              return (
                <li key={member.userId} className="rounded-xl border border-slate-200 px-4 py-3">
                  <div className="flex flex-wrap items-center justify-between gap-3">
                    <div>
                      <p className="font-semibold text-slate-950">{member.userFullName}</p>
                      <p className="text-sm text-slate-600">{member.userEmail}</p>
                    </div>
                    <div className="flex flex-wrap items-center gap-2">
                      {canUpdateRole && manageable ? (
                        <label className="text-sm text-slate-700">
                          <span className="sr-only">Role for {member.userFullName}</span>
                          <select
                            aria-label={`Role for ${member.userFullName}`}
                            className="rounded-lg border border-slate-300 bg-white px-2 py-1.5 text-sm"
                            value={member.role}
                            disabled={pendingMemberId === member.userId}
                            onChange={(event) => void changeRole(member, event.target.value as OrganizationRole)}
                          >
                            {(membership?.role === "OWNER" ? ["OWNER", "ADMIN", "MEMBER"] : ["MEMBER"]).map((role) => (
                              <option key={role} value={role}>
                                {ROLE_LABELS[role as OrganizationRole]}
                              </option>
                            ))}
                          </select>
                        </label>
                      ) : (
                        <StatusBadge tone={roleTone(member.role)}>{ROLE_LABELS[member.role]}</StatusBadge>
                      )}
                      {canRemove && manageable ? (
                        <Button variant="secondary" onClick={() => setPendingRemove(member)}>
                          Remove
                        </Button>
                      ) : null}
                    </div>
                  </div>
                </li>
              );
            })}
          </ul>

          {canInvite ? (
            <Form className="mt-4" onSubmit={(event) => void invite(event)}>
              <h3 className="text-base font-semibold text-slate-950">Add a member</h3>
              <p className="text-sm text-slate-600">The person needs an existing account.</p>
              {inviteError ? <FormBanner tone="error">{inviteError}</FormBanner> : null}
              <div className="grid gap-4 sm:grid-cols-[minmax(0,1fr)_10rem]">
                <TextField
                  id="settings-member-email"
                  label="Email"
                  type="email"
                  value={inviteEmail}
                  onChange={(event) => setInviteEmail(event.target.value)}
                  required
                />
                <SelectField
                  id="settings-member-role"
                  label="Role"
                  value={inviteRole}
                  onChange={(event) => setInviteRole(event.target.value as OrganizationRole)}
                >
                  {roles.map((role) => (
                    <option key={role} value={role}>
                      {ROLE_LABELS[role]}
                    </option>
                  ))}
                </SelectField>
              </div>
              <div className="flex justify-end">
                <Button type="submit" loading={inviting}>
                  Add member
                </Button>
              </div>
            </Form>
          ) : null}
        </section>
      )}

      <Dialog
        open={pendingRemove !== null}
        title="Remove member"
        description={pendingRemove ? `Remove ${pendingRemove.userFullName} from this organization?` : undefined}
        onClose={() => setPendingRemove(null)}
      >
        <div className="flex justify-end gap-2">
          <Button variant="secondary" onClick={() => setPendingRemove(null)}>
            Cancel
          </Button>
          <Button variant="danger" loading={removing} onClick={() => void confirmRemove()}>
            Remove
          </Button>
        </div>
      </Dialog>
    </div>
  );
}

function AccountForm({
  user,
  saveProfile,
}: {
  user: NonNullable<ReturnType<typeof useAuth>["user"]>;
  saveProfile: (firstName: string, lastName: string) => Promise<unknown>;
}) {
  const { showToast } = useToast();
  const [firstName, setFirstName] = useState(user.firstName);
  const [lastName, setLastName] = useState(user.lastName);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextFields: Record<string, string> = {};
    if (!firstName.trim()) {
      nextFields.firstName = "Enter your first name.";
    }
    if (!lastName.trim()) {
      nextFields.lastName = "Enter your last name.";
    }
    setFields(nextFields);
    setError(null);
    if (Object.keys(nextFields).length > 0) {
      return;
    }
    setSaving(true);
    try {
      await saveProfile(firstName.trim(), lastName.trim());
      showToast({ tone: "success", message: "Account saved." });
    } catch (caught) {
      if (caught instanceof ApiError) {
        setFields(caught.details ?? {});
        setError(caught.message);
      } else {
        setError("The account could not be saved.");
      }
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="panel p-5">
      <h2 className="text-lg font-semibold text-slate-950">Account</h2>
      <p className="mt-1 text-sm text-slate-600">{user.email}</p>
      <Form className="mt-4" onSubmit={(event) => void onSubmit(event)}>
        {error ? <FormBanner tone="error">{error}</FormBanner> : null}
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            id="settings-first-name"
            label="First name"
            value={firstName}
            autoComplete="given-name"
            error={fields.firstName}
            onChange={(event) => setFirstName(event.target.value)}
            required
          />
          <TextField
            id="settings-last-name"
            label="Last name"
            value={lastName}
            autoComplete="family-name"
            error={fields.lastName}
            onChange={(event) => setLastName(event.target.value)}
            required
          />
        </div>
        <div className="flex justify-end">
          <Button type="submit" loading={saving}>
            Save account
          </Button>
        </div>
      </Form>
    </section>
  );
}

function OrganizationNameForm({
  organizationId,
  initialName,
  onSaved,
}: {
  organizationId: string;
  initialName: string;
  onSaved: () => Promise<void>;
}) {
  const { showToast } = useToast();
  const [name, setName] = useState(initialName);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  async function onSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (name.trim().length < 2) {
      setError("Organization name must be at least 2 characters.");
      return;
    }
    setError(null);
    setSaving(true);
    try {
      const updated = await updateOrganization(organizationId, name.trim());
      setName(updated.name);
      await onSaved();
      showToast({ tone: "success", message: "Organization saved." });
    } catch (caught) {
      setError(caught instanceof ApiError ? caught.message : "The organization could not be saved.");
    } finally {
      setSaving(false);
    }
  }

  return (
    <Form className="mt-4" onSubmit={(event) => void onSubmit(event)}>
      {error ? <FormBanner tone="error">{error}</FormBanner> : null}
      <TextField
        id="settings-organization-name"
        label="Organization name"
        value={name}
        onChange={(event) => setName(event.target.value)}
        required
      />
      <div className="flex justify-end">
        <Button type="submit" loading={saving}>
          Save organization
        </Button>
      </div>
    </Form>
  );
}
