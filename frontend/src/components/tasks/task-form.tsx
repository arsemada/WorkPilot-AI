"use client";

import { useId, type FormEvent } from "react";
import type { Membership, TaskPriority, TaskStatus } from "@/lib/api/types";
import { TASK_PRIORITIES, priorityLabel, statusLabel } from "@/lib/tasks";
import { Form, FormBanner, SelectField, TextAreaField, TextField } from "@/components/ui/field";
import { Button } from "@/components/ui/button";

export type TaskFormValues = {
  title: string;
  description: string;
  status: TaskStatus;
  priority: TaskPriority;
  dueDate: string;
  assigneeId: string;
};

export const emptyTaskForm: TaskFormValues = {
  title: "",
  description: "",
  status: "TODO",
  priority: "MEDIUM",
  dueDate: "",
  assigneeId: "",
};

export function TaskForm({
  values,
  statuses,
  members,
  fieldErrors,
  formError,
  submitting,
  submitLabel,
  onChange,
  onSubmit,
  onCancel,
}: {
  values: TaskFormValues;
  statuses: TaskStatus[];
  members: Membership[];
  fieldErrors: Record<string, string>;
  formError: string | null;
  submitting: boolean;
  submitLabel: string;
  onChange: (values: TaskFormValues) => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  onCancel: () => void;
}) {
  const prefix = useId();

  return (
    <Form onSubmit={onSubmit}>
      {formError ? <FormBanner tone="error">{formError}</FormBanner> : null}
      <TextField
        id={`${prefix}-title`}
        label="Title"
        value={values.title}
        error={fieldErrors.title}
        required
        onChange={(event) => onChange({ ...values, title: event.target.value })}
      />
      <TextAreaField
        id={`${prefix}-description`}
        label="Description"
        value={values.description}
        error={fieldErrors.description}
        onChange={(event) => onChange({ ...values, description: event.target.value })}
      />
      <div className="grid gap-4 sm:grid-cols-2">
        <SelectField
          id={`${prefix}-status`}
          label="Status"
          value={values.status}
          error={fieldErrors.status}
          onChange={(event) => onChange({ ...values, status: event.target.value as TaskStatus })}
        >
          {statuses.map((status) => (
            <option key={status} value={status}>
              {statusLabel(status)}
            </option>
          ))}
        </SelectField>
        <SelectField
          id={`${prefix}-priority`}
          label="Priority"
          value={values.priority}
          error={fieldErrors.priority}
          onChange={(event) => onChange({ ...values, priority: event.target.value as TaskPriority })}
        >
          {TASK_PRIORITIES.map((priority) => (
            <option key={priority} value={priority}>
              {priorityLabel(priority)}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          id={`${prefix}-due`}
          label="Due date"
          type="date"
          value={values.dueDate}
          error={fieldErrors.dueDate}
          onChange={(event) => onChange({ ...values, dueDate: event.target.value })}
        />
        <SelectField
          id={`${prefix}-assignee`}
          label="Assignee"
          value={values.assigneeId}
          error={fieldErrors.assigneeId}
          onChange={(event) => onChange({ ...values, assigneeId: event.target.value })}
        >
          <option value="">Unassigned</option>
          {members.map((member) => (
            <option key={member.userId} value={member.userId}>
              {member.userFullName}
            </option>
          ))}
        </SelectField>
      </div>
      <div className="flex justify-end gap-2">
        <Button variant="secondary" onClick={onCancel}>
          Cancel
        </Button>
        <Button type="submit" loading={submitting}>
          {submitLabel}
        </Button>
      </div>
    </Form>
  );
}
