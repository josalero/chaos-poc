export default function Input({ label, hint, id, className = '', ...props }) {
  const inputId = id || props.name;
  return (
    <label htmlFor={inputId} className="block text-sm">
      {label && (
        <span className="mb-1.5 block font-medium text-slate-700">{label}</span>
      )}
      <input
        id={inputId}
        className={`w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm placeholder:text-slate-400 focus:border-blue-500 focus:ring-2 focus:ring-blue-500/20 ${className}`}
        {...props}
      />
      {hint && <span className="mt-1 block text-xs text-slate-500">{hint}</span>}
    </label>
  );
}
