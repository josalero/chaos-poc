const outcomes = [
  { code: '2xx', title: 'Healthy response', copy: 'No matching exception assault. Latency may still increase.', tone: 'ok' },
  { code: '404', title: 'Resource not found', copy: 'Expected when ResourceNotFoundException targets inventory lookup.', tone: 'warn' },
  { code: '403', title: 'Access forbidden', copy: 'Expected when ForbiddenException targets authorization checks.', tone: 'warn' },
  { code: '409', title: 'Business conflict', copy: 'Expected for duplicate orders or reserve conflicts.', tone: 'warn' },
  { code: '500', title: 'Injected failure', copy: 'Expected when RuntimeException targets create or submit paths.', tone: 'bad' },
];

export default function OutcomeGuide({ service }) {
  const assault = service?.appliedAssault;
  const exceptionType = assault?.exception?.type || '';
  let expected = service?.cmEnabled ? 'Latency / successful HTTP' : '2xx';
  if (exceptionType.includes('ResourceNotFound')) expected = '404';
  else if (exceptionType.includes('Forbidden')) expected = '403';
  else if (exceptionType.includes('Duplicate')) expected = '409';
  else if (assault?.exceptionsActive) expected = '500';

  return (
    <section className="outcome-guide" aria-labelledby="outcome-title">
      <div className="guide-intro">
        <p className="eyebrow">AUDIENCE GUIDE</p>
        <h2 id="outcome-title">What response should I expect?</h2>
        <p>Highlighted outcome reflects observed fault injection on the selected service (patched in Operator).</p>
        <div className="expected-callout"><span>EXPECTED NOW</span><strong>{expected}</strong></div>
      </div>
      <div className="outcome-grid">
        {outcomes.map((item) => (
          <article key={item.code} className={`outcome-item ${item.tone} ${expected === item.code ? 'expected' : ''}`}>
            <strong>{item.code}</strong><div><h3>{item.title}</h3><p>{item.copy}</p></div>
          </article>
        ))}
      </div>
    </section>
  );
}
