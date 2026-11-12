import { useEffect, useState } from "react";
import { X } from "lucide-react";
import { EMPTY_SCOPE_SELECTION, FILE_TYPES, PERIODS, type AiScopeSelection } from "../lib/aiScope";
import { getTenantTrays, type Tray } from "../services/trayService";
import { getTags, type TagDTO } from "../services/tagService";

interface AiScopePickerProps {
  slug: string;
  token: string | null;
  selection: AiScopeSelection;
  onChange: (selection: AiScopeSelection) => void;
  disabled?: boolean;
}

export function AiScopePicker({ slug, token, selection, onChange, disabled }: AiScopePickerProps) {
  const [trays, setTrays] = useState<Tray[]>([]);
  const [tags, setTags] = useState<TagDTO[]>([]);

  useEffect(() => {
    if (!token) return;
    let active = true;
    // Without the lists the picker still offers type and date
    getTenantTrays(slug, token)
      .then((data) => {
        if (active) setTrays(data.filter((tray) => !(tray.isDefault && tray.name === "Trash")));
      })
      .catch(() => {});
    getTags(slug, token)
      .then((data) => {
        if (active) setTags(data);
      })
      .catch(() => {});
    return () => {
      active = false;
    };
  }, [slug, token]);

  const isNarrowed = Object.values(selection).some(Boolean);

  function chip(
    label: string,
    key: keyof AiScopeSelection,
    anyLabel: string,
    options: { value: string; label: string }[],
  ) {
    const active = selection[key] !== "";
    return (
      <label
        className={`flex items-center gap-1 rounded-full border px-2.5 py-1 text-[11px] transition-colors ${
          active
            ? "border-indigo-300 bg-indigo-50 text-indigo-700 dark:border-indigo-700 dark:bg-indigo-950/60 dark:text-indigo-300"
            : "border-gray-200 bg-white text-gray-600 dark:border-gray-700 dark:bg-gray-800 dark:text-gray-300"
        }`}
      >
        <span className="font-medium">{label}:</span>
        <select
          aria-label={`Limit search by ${label.toLowerCase()}`}
          value={selection[key]}
          disabled={disabled}
          onChange={(event) => onChange({ ...selection, [key]: event.target.value })}
          className="max-w-[140px] cursor-pointer truncate bg-transparent outline-none disabled:cursor-not-allowed"
        >
          <option value="">{anyLabel}</option>
          {options.map((option) => (
            <option key={option.value} value={option.value}>
              {option.label}
            </option>
          ))}
        </select>
      </label>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-1.5" role="group" aria-label="Limit the search">
      {chip("In", "trayId", "All trays", trays.map((tray) => ({ value: tray.id, label: tray.name })))}
      {chip(
        "Type",
        "fileType",
        "Any",
        Object.entries(FILE_TYPES).map(([value, { label }]) => ({ value, label })),
      )}
      {chip(
        "Uploaded",
        "period",
        "Any time",
        Object.entries(PERIODS).map(([value, { label }]) => ({ value, label })),
      )}
      {tags.length > 0 &&
        chip("Tag", "tagId", "Any", tags.map((tag) => ({ value: tag.id, label: tag.name })))}
      {isNarrowed && (
        <button
          type="button"
          disabled={disabled}
          onClick={() => onChange(EMPTY_SCOPE_SELECTION)}
          className="flex items-center gap-0.5 rounded-full px-1.5 py-1 text-[11px] text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
        >
          <X className="h-3 w-3" />
          Clear
        </button>
      )}
    </div>
  );
}
