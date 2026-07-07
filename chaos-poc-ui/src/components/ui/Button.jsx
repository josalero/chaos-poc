const variants = {
  primary:
    'bg-blue-600 text-white hover:bg-blue-700 border border-transparent shadow-sm',
  secondary:
    'bg-white text-slate-700 border border-slate-300 hover:bg-slate-50 shadow-sm',
  ghost:
    'bg-transparent text-slate-600 border border-transparent hover:bg-slate-100',
  danger:
    'bg-white text-rose-700 border border-rose-200 hover:bg-rose-50',
};

const sizes = {
  sm: 'px-3 py-1.5 text-sm',
  md: 'px-4 py-2 text-sm',
};

export default function Button({
  variant = 'secondary',
  size = 'md',
  className = '',
  children,
  ...props
}) {
  return (
    <button
      type="button"
      className={`inline-flex items-center justify-center gap-1.5 rounded-lg font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-blue-600 disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${sizes[size]} ${className}`}
      {...props}
    >
      {children}
    </button>
  );
}
