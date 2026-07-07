import { useCallback, useRef } from 'react';

function Tab({
  id,
  label,
  description,
  badge,
  activeId,
  onChange,
  tabIds,
  onKeyDown,
}) {
  const selected = activeId === id;
  const tabRef = useRef(null);

  const handleKeyDown = useCallback((event) => {
    if (!tabIds?.length) return;
    const index = tabIds.indexOf(id);
    let nextIndex = index;

    if (event.key === 'ArrowRight') {
      nextIndex = (index + 1) % tabIds.length;
    } else if (event.key === 'ArrowLeft') {
      nextIndex = (index - 1 + tabIds.length) % tabIds.length;
    } else if (event.key === 'Home') {
      nextIndex = 0;
    } else if (event.key === 'End') {
      nextIndex = tabIds.length - 1;
    } else {
      return;
    }

    event.preventDefault();
    onChange(tabIds[nextIndex]);
    onKeyDown?.(tabIds[nextIndex]);
  }, [id, onChange, onKeyDown, tabIds]);

  return (
    <button
      ref={tabRef}
      type="button"
      role="tab"
      id={`tab-${id}`}
      aria-selected={selected}
      aria-controls={`tabpanel-${id}`}
      tabIndex={selected ? 0 : -1}
      onClick={() => onChange(id)}
      onKeyDown={handleKeyDown}
      className={`min-w-[9rem] flex-1 rounded-md px-3 py-2.5 text-left transition focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 sm:min-w-0 ${
        selected
          ? 'bg-white text-slate-900 shadow-sm ring-1 ring-slate-200'
          : 'text-slate-600 hover:bg-white/60 hover:text-slate-900'
      }`}
    >
      <span className="flex items-center justify-between gap-2">
        <span className="text-sm font-semibold">{label}</span>
        {badge != null && badge !== '' && (
          <span
            className={`shrink-0 rounded-full px-2 py-0.5 text-xs font-medium tabular-nums ${
              selected
                ? 'bg-blue-50 text-blue-800 ring-1 ring-blue-100'
                : 'bg-slate-200/80 text-slate-700'
            }`}
          >
            {badge}
          </span>
        )}
      </span>
      {description && (
        <span className={`mt-0.5 block text-xs ${selected ? 'text-slate-600' : 'text-slate-500'}`}>
          {description}
        </span>
      )}
    </button>
  );
}

export function TabNav({
  activeId,
  onChange,
  tabs,
  'aria-label': ariaLabel = 'Verify sections',
}) {
  const tabIds = tabs.map((tab) => tab.id);
  const focusTab = useCallback((tabId) => {
    document.getElementById(`tab-${tabId}`)?.focus();
  }, []);

  return (
    <div
      role="tablist"
      aria-label={ariaLabel}
      className="flex gap-1 overflow-x-auto rounded-lg border border-slate-200 bg-slate-100/80 p-1"
    >
      {tabs.map((tab) => (
        <Tab
          key={tab.id}
          id={tab.id}
          label={tab.label}
          description={tab.description}
          badge={tab.badge}
          activeId={activeId}
          onChange={onChange}
          tabIds={tabIds}
          onKeyDown={focusTab}
        />
      ))}
    </div>
  );
}

export function TabPanel({ id, activeId, labelledBy, children }) {
  if (activeId !== id) return null;

  return (
    <div
      role="tabpanel"
      id={`tabpanel-${id}`}
      aria-labelledby={labelledBy ?? `tab-${id}`}
      tabIndex={0}
      className="outline-none focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-blue-600"
    >
      {children}
    </div>
  );
}
