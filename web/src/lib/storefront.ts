/**
 * Shared types and pure section-list state for the Storefront page.
 *
 * The reducer is deliberately free of Supabase and React so the ordering and cap rules can be
 * tested directly. It mirrors what the database enforces in 20261105000001 rather than
 * replacing it: the RPC is still the authority, and this exists so a manager sees the same
 * limit in the UI before a save fails rather than discovering it afterwards.
 */

export type StorefrontPage = "home" | "categories";
export type SectionType = "products" | "categories";

export interface StorefrontSection {
  id: string;
  title: string;
  subtitle: string | null;
  sectionType: SectionType;
  active: boolean;
  sortOrder: number;
  /** Server value, echoed back as p_expected_updated_at so two managers cannot overwrite each other. */
  updatedAt: string | null;
  itemIds: string[];
  /** True for sections composed in this session but not yet saved. */
  dirty?: boolean;
}

export const MAX_SECTIONS_PER_PAGE = 20;
export const MAX_ITEMS_PER_SECTION = 30;
export const MAX_TITLE_LENGTH = 40;
export const MAX_SUBTITLE_LENGTH = 80;

export type SectionAction =
  | { type: "add"; section: StorefrontSection }
  | { type: "update"; id: string; patch: Partial<StorefrontSection> }
  | { type: "remove"; id: string }
  | { type: "move"; id: string; direction: -1 | 1 }
  | { type: "reorder"; fromId: string; toId: string }
  | { type: "toggleActive"; id: string };

export interface SectionState {
  sections: StorefrontSection[];
  /** Human-readable refusal, shown inline instead of mutating state. */
  notice: string | null;
}

function renumber(sections: StorefrontSection[]): StorefrontSection[] {
  return sections.map((section, index) => ({ ...section, sortOrder: index + 1 }));
}

export function initialSectionState(sections: StorefrontSection[] = []): SectionState {
  return { sections: renumber(sections), notice: null };
}

export function sectionReducer(state: SectionState, action: SectionAction): SectionState {
  switch (action.type) {
    case "add": {
      if (state.sections.length >= MAX_SECTIONS_PER_PAGE) {
        return {
          ...state,
          notice: `A page can have at most ${MAX_SECTIONS_PER_PAGE} sections. Remove or deactivate one before adding another.`,
        };
      }
      return {
        sections: renumber([...state.sections, { ...action.section, dirty: true }]),
        notice: null,
      };
    }

    case "update": {
      return {
        sections: state.sections.map((section) => {
          if (section.id !== action.id) return section;
          // Type is fixed at creation. sectionType is dropped from the patch rather than
          // trusted, so a stale or hand-edited form cannot silently turn a products rail into
          // a category grid - the database would reject the items either way.
          const { sectionType: _ignoredType, ...safePatch } = action.patch;
          return { ...section, ...safePatch, dirty: true };
        }),
        notice: null,
      };
    }

    case "remove":
      return {
        sections: renumber(state.sections.filter((section) => section.id !== action.id)),
        notice: null,
      };

    case "move": {
      const index = state.sections.findIndex((section) => section.id === action.id);
      if (index === -1) return state;
      const target = index + action.direction;
      if (target < 0 || target >= state.sections.length) {
        return { ...state, notice: null };
      }
      const next = [...state.sections];
      [next[index], next[target]] = [next[target], next[index]];
      return { sections: renumber(next), notice: null };
    }

    case "reorder": {
      const from = state.sections.findIndex((section) => section.id === action.fromId);
      const to = state.sections.findIndex((section) => section.id === action.toId);
      if (from === -1 || to === -1 || from === to) return state;
      const next = [...state.sections];
      const [moved] = next.splice(from, 1);
      next.splice(to, 0, moved);
      return { sections: renumber(next), notice: null };
    }

    case "toggleActive":
      return {
        sections: state.sections.map((section) =>
          section.id === action.id ? { ...section, active: !section.active, dirty: true } : section,
        ),
        notice: null,
      };

    default:
      return state;
  }
}

/**
 * A heading is required and bounded exactly as the database bounds it, so the manager is told
 * before saving rather than reading a server message after.
 */
export function validateSectionDraft(draft: {
  title: string;
  subtitle?: string | null;
  itemIds: string[];
}): string | null {
  const title = draft.title.trim();
  if (title.length === 0) return "A heading is required.";
  if (title.length > MAX_TITLE_LENGTH)
    return `A heading must be ${MAX_TITLE_LENGTH} characters or fewer.`;
  if (draft.subtitle && draft.subtitle.length > MAX_SUBTITLE_LENGTH)
    return `A subtitle must be ${MAX_SUBTITLE_LENGTH} characters or fewer.`;
  if (draft.itemIds.length > MAX_ITEMS_PER_SECTION)
    return `A section can hold at most ${MAX_ITEMS_PER_SECTION} items.`;
  return null;
}

/** The primary is always a member and duplicates are never sent. */
export function normaliseItemIds(primaryId: string, ids: string[]): string[] {
  return Array.from(new Set([primaryId, ...ids]));
}
