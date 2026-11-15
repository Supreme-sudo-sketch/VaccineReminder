// Kenyan routine childhood schedule (check against the current MoH schedule before real use).
// Doses in the same `series` must be at least `gapWeeks` after the previous dose in that series,
// so a late dose pushes the next one back instead of reminding the caregiver too early.
export const SCHEDULE = [
  { code: 'BCG', name: 'BCG', weeks: 0 },
  { code: 'OPV0', name: 'Polio (OPV 0)', weeks: 0 },
  { code: 'OPV1', name: 'Polio (OPV 1)', weeks: 6, series: 'OPV' },
  { code: 'PENTA1', name: 'Pentavalent 1', weeks: 6, series: 'PENTA' },
  { code: 'PCV1', name: 'Pneumococcal 1', weeks: 6, series: 'PCV' },
  { code: 'ROTA1', name: 'Rotavirus 1', weeks: 6, series: 'ROTA' },
  { code: 'OPV2', name: 'Polio (OPV 2)', weeks: 10, series: 'OPV', gapWeeks: 4 },
  { code: 'PENTA2', name: 'Pentavalent 2', weeks: 10, series: 'PENTA', gapWeeks: 4 },
  { code: 'PCV2', name: 'Pneumococcal 2', weeks: 10, series: 'PCV', gapWeeks: 4 },
  { code: 'ROTA2', name: 'Rotavirus 2', weeks: 10, series: 'ROTA', gapWeeks: 4 },
  { code: 'OPV3', name: 'Polio (OPV 3)', weeks: 14, series: 'OPV', gapWeeks: 4 },
  { code: 'PENTA3', name: 'Pentavalent 3', weeks: 14, series: 'PENTA', gapWeeks: 4 },
  { code: 'PCV3', name: 'Pneumococcal 3', weeks: 14, series: 'PCV', gapWeeks: 4 },
  { code: 'IPV', name: 'Polio (IPV)', weeks: 14 },
  { code: 'VITA', name: 'Vitamin A', months: 6 },
  { code: 'MR1', name: 'Measles-Rubella 1', months: 9, series: 'MR' },
  { code: 'YF', name: 'Yellow Fever', months: 9 },
  { code: 'MR2', name: 'Measles-Rubella 2', months: 18, series: 'MR', gapWeeks: 4 },
];

export const BY_CODE = new Map(SCHEDULE.map((s) => [s.code, s]));

// Short code for the age at which a dose is due, e.g. "birth", "6w", "9m". The app turns it into words.
export function milestoneOf(item) {
  if (!item) return '';
  if (item.weeks !== undefined) return item.weeks === 0 ? 'birth' : `${item.weeks}w`;
  return `${item.months}m`;
}

export function parseDate(s) {
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s || '');
  if (!m) return null;
  const d = new Date(Date.UTC(+m[1], +m[2] - 1, +m[3]));
  if (d.getUTCFullYear() !== +m[1] || d.getUTCMonth() !== +m[2] - 1 || d.getUTCDate() !== +m[3]) return null;
  return d;
}

export function fmt(d) {
  return d.toISOString().slice(0, 10);
}

export function addDays(dateStr, days) {
  const d = parseDate(dateStr);
  d.setUTCDate(d.getUTCDate() + days);
  return fmt(d);
}

export function dueDateFor(dobStr, item) {
  const d = parseDate(dobStr);
  if (item.weeks !== undefined) {
    d.setUTCDate(d.getUTCDate() + item.weeks * 7);
  } else {
    const day = d.getUTCDate();
    d.setUTCDate(1);
    d.setUTCMonth(d.getUTCMonth() + item.months);
    const last = new Date(Date.UTC(d.getUTCFullYear(), d.getUTCMonth() + 1, 0)).getUTCDate();
    d.setUTCDate(Math.min(day, last));
  }
  return fmt(d);
}

// Works out every due date for a child from the date of birth and the doses already given.
// `given` maps dose code to the date it was given. Returns a Map of code to due date.
export function planDueDates(dobStr, given = new Map()) {
  const due = new Map();
  const lastInSeries = new Map();
  for (const item of SCHEDULE) {
    let date = dueDateFor(dobStr, item);
    const prev = item.series && lastInSeries.get(item.series);
    if (prev && item.gapWeeks) {
      const earliest = addDays(given.get(prev) || due.get(prev), item.gapWeeks * 7);
      if (earliest > date) date = earliest;
    }
    due.set(item.code, date);
    if (item.series) lastInSeries.set(item.series, item.code);
  }
  return due;
}

// The dose that comes just before `code` in its series, if any.
export function previousInSeries(code) {
  const item = BY_CODE.get(code);
  if (!item || !item.series) return null;
  let prev = null;
  for (const s of SCHEDULE) {
    if (s.code === code) return prev;
    if (s.series === item.series) prev = s;
  }
  return null;
}

export function daysBetween(fromStr, toStr) {
  return Math.round((parseDate(toStr) - parseDate(fromStr)) / 86400000);
}

export function todayStr(offsetHours = 3) {
  return fmt(new Date(Date.now() + offsetHours * 3600000));
}
