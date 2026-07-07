export default function Card({ as: Tag = 'section', className = '', children, ...props }) {
  return (
    <Tag
      className={`rounded-xl border border-slate-200 bg-white shadow-sm ${className}`}
      {...props}
    >
      {children}
    </Tag>
  );
}

export function CardHeader({ title, description, actions }) {
  return (
    <div className="flex flex-wrap items-start justify-between gap-3 border-b border-slate-100 px-5 py-4">
      <div className="min-w-0">
        {title && <h2 className="text-base font-semibold text-slate-900">{title}</h2>}
        {description && (
          <p className="mt-0.5 text-sm text-slate-600">{description}</p>
        )}
      </div>
      {actions && <div className="flex shrink-0 flex-wrap gap-2">{actions}</div>}
    </div>
  );
}

export function CardBody({ className = '', children }) {
  return <div className={`px-5 py-4 ${className}`}>{children}</div>;
}
