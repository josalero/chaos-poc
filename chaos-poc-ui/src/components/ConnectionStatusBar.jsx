export default function ConnectionStatusBar({
  targetLabel,
  targetOk,
  loading,
  onRefresh,
}) {
  if (loading) {
    return (
      <output className="text-xs text-slate-500" aria-live="polite">
        Checking {targetLabel}…
      </output>
    );
  }

  return (
    <div className="flex flex-wrap items-center gap-3" aria-live="polite">
      <StatusDot ok={targetOk} label={targetLabel} />
      <button
        type="button"
        onClick={onRefresh}
        className="text-xs font-medium text-blue-600 hover:text-blue-800 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600"
      >
        Refresh status
      </button>
    </div>
  );
}

function StatusDot({ ok, label }) {
  return (
    <span
      className={`inline-flex items-center gap-1.5 text-xs font-medium ${
        ok ? 'text-emerald-700' : 'text-rose-700'
      }`}
      role="status"
    >
      <span
        className={`h-2 w-2 rounded-full ${ok ? 'bg-emerald-500' : 'bg-rose-500'}`}
        aria-hidden
      />
      {label} {ok ? 'reachable' : 'unreachable'}
    </span>
  );
}
